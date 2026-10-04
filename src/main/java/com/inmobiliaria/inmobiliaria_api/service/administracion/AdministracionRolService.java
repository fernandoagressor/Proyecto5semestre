package com.inmobiliaria.inmobiliaria_api.service.administracion;

import com.inmobiliaria.inmobiliaria_api.dto.administracion.AdministracionDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.repository.empresa.EmpresaRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaRegistroService;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministracionRolService {

    private final EmpresaRepository empresaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditoriaRegistroService auditoria;

    public AdministracionRolService(
            EmpresaRepository empresaRepository,
            JdbcTemplate jdbcTemplate,
            AuditoriaRegistroService auditoria
    ) {
        this.empresaRepository = empresaRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<AdministracionDtos.RolResponse> listar(Long empresaId) {
        verificarEmpresa(empresaId);
        return jdbcTemplate.query("""
                SELECT id, empresa_id, codigo, nombre, descripcion,
                       es_sistema, protegido, activo
                FROM roles
                WHERE (empresa_id IS NULL OR empresa_id = ?)
                  AND codigo <> 'SUPER_ADMIN'
                ORDER BY es_sistema DESC, nombre
                """, (rs, rowNum) -> new AdministracionDtos.RolResponse(
                rs.getLong("id"),
                nullableLong(rs.getLong("empresa_id"), rs.wasNull()),
                rs.getString("codigo"),
                rs.getString("nombre"),
                rs.getString("descripcion"),
                rs.getBoolean("es_sistema"),
                rs.getBoolean("protegido"),
                rs.getBoolean("activo"),
                permisos(rs.getLong("id"))
        ), empresaId);
    }

    @Transactional(readOnly = true)
    public List<AdministracionDtos.PermisoResponse> permisos() {
        return jdbcTemplate.query("""
                SELECT id, codigo, modulo, nombre, descripcion, sensible
                FROM permisos
                WHERE activo = 1
                ORDER BY modulo, nombre
                """, (rs, rowNum) -> new AdministracionDtos.PermisoResponse(
                rs.getLong("id"),
                rs.getString("codigo"),
                rs.getString("modulo"),
                rs.getString("nombre"),
                rs.getString("descripcion"),
                rs.getBoolean("sensible")
        ));
    }

    @Transactional
    public AdministracionDtos.RolResponse crear(
            Long actorId,
            Long empresaId,
            AdministracionDtos.RolGuardarRequest request
    ) {
        verificarEmpresa(empresaId);
        String codigo = codigo(request.codigo());
        validarPermisos(request.permisoIds());
        Long duplicados = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM roles
                WHERE empresa_id = ? AND codigo = ?
                """, Long.class, empresaId, codigo);
        if (duplicados != null && duplicados > 0) {
            throw new ConflictoException(
                    "Ya existe un rol con ese código dentro de la empresa"
            );
        }
        jdbcTemplate.update("""
                INSERT INTO roles
                    (empresa_id, codigo, nombre, descripcion, es_sistema,
                     protegido, activo, creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, 0, 0, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                empresaId,
                codigo,
                request.nombre().trim(),
                opcional(request.descripcion()),
                request.activo());
        Long rolId = jdbcTemplate.queryForObject("""
                SELECT id FROM roles WHERE empresa_id = ? AND codigo = ?
                """, Long.class, empresaId, codigo);
        if (rolId == null) {
            throw new IllegalStateException("No fue posible recuperar el rol creado");
        }
        asignarPermisos(rolId, request.permisoIds());
        AdministracionDtos.RolResponse response = buscar(empresaId, rolId);
        auditoria.registrar(
                new ContextoUsuario(actorId, empresaId),
                null,
                "SUPERADMINISTRACION",
                "ROL_CREADO",
                "ROL",
                rolId,
                "Rol personalizado creado para la empresa",
                null,
                response
        );
        return response;
    }

    @Transactional
    public AdministracionDtos.RolResponse actualizar(
            Long actorId,
            Long empresaId,
            Long rolId,
            AdministracionDtos.RolGuardarRequest request
    ) {
        AdministracionDtos.RolResponse anterior = buscar(empresaId, rolId);
        if (anterior.sistema() || anterior.protegido()) {
            throw new ReglaNegocioException(
                    "Los roles del sistema no pueden editarse"
            );
        }
        String codigo = codigo(request.codigo());
        validarPermisos(request.permisoIds());
        Long duplicados = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM roles
                WHERE empresa_id = ? AND codigo = ? AND id <> ?
                """, Long.class, empresaId, codigo, rolId);
        if (duplicados != null && duplicados > 0) {
            throw new ConflictoException(
                    "Ya existe un rol con ese código dentro de la empresa"
            );
        }
        jdbcTemplate.update("""
                UPDATE roles
                SET codigo = ?, nombre = ?, descripcion = ?, activo = ?
                WHERE id = ? AND empresa_id = ? AND es_sistema = 0 AND protegido = 0
                """,
                codigo,
                request.nombre().trim(),
                opcional(request.descripcion()),
                request.activo(),
                rolId,
                empresaId);
        asignarPermisos(rolId, request.permisoIds());
        AdministracionDtos.RolResponse nuevo = buscar(empresaId, rolId);
        auditoria.registrar(
                new ContextoUsuario(actorId, empresaId),
                null,
                "SUPERADMINISTRACION",
                "ROL_ACTUALIZADO",
                "ROL",
                rolId,
                "Rol personalizado y permisos actualizados",
                anterior,
                nuevo
        );
        return nuevo;
    }

    private AdministracionDtos.RolResponse buscar(Long empresaId, Long rolId) {
        List<AdministracionDtos.RolResponse> resultado = jdbcTemplate.query("""
                SELECT id, empresa_id, codigo, nombre, descripcion,
                       es_sistema, protegido, activo
                FROM roles
                WHERE id = ? AND empresa_id = ?
                """, (rs, rowNum) -> new AdministracionDtos.RolResponse(
                rs.getLong("id"),
                rs.getLong("empresa_id"),
                rs.getString("codigo"),
                rs.getString("nombre"),
                rs.getString("descripcion"),
                rs.getBoolean("es_sistema"),
                rs.getBoolean("protegido"),
                rs.getBoolean("activo"),
                permisos(rs.getLong("id"))
        ), rolId, empresaId);
        if (resultado.isEmpty()) {
            throw new RecursoNoEncontradoException(
                    "El rol personalizado no existe dentro de la empresa"
            );
        }
        return resultado.getFirst();
    }

    private List<String> permisos(Long rolId) {
        return jdbcTemplate.queryForList("""
                SELECT p.codigo
                FROM rol_permisos rp
                JOIN permisos p ON p.id = rp.permiso_id
                WHERE rp.rol_id = ? AND rp.concedido = 1 AND p.activo = 1
                ORDER BY p.modulo, p.codigo
                """, String.class, rolId);
    }

    private void asignarPermisos(Long rolId, List<Long> permisoIds) {
        jdbcTemplate.update("DELETE FROM rol_permisos WHERE rol_id = ?", rolId);
        for (Long permisoId : permisoIds) {
            jdbcTemplate.update("""
                    INSERT INTO rol_permisos
                        (rol_id, permiso_id, concedido, creado_en, actualizado_en)
                    VALUES (?, ?, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """, rolId, permisoId);
        }
    }

    private void validarPermisos(List<Long> permisoIds) {
        if (permisoIds == null) {
            throw new ReglaNegocioException("La lista de permisos es obligatoria");
        }
        if (new HashSet<>(permisoIds).size() != permisoIds.size()) {
            throw new ReglaNegocioException("No repitas permisos en el rol");
        }
        for (Long permisoId : permisoIds) {
            Long existe = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM permisos WHERE id = ? AND activo = 1",
                    Long.class,
                    permisoId
            );
            if (existe == null || existe != 1) {
                throw new ReglaNegocioException(
                        "Uno de los permisos seleccionados no existe"
                );
            }
        }
    }

    private void verificarEmpresa(Long empresaId) {
        if (!empresaRepository.existsById(empresaId)) {
            throw new RecursoNoEncontradoException("La empresa no existe");
        }
    }

    private Long nullableLong(long valor, boolean wasNull) {
        return wasNull ? null : valor;
    }

    private String codigo(String valor) {
        return valor.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]+", "_");
    }

    private String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
