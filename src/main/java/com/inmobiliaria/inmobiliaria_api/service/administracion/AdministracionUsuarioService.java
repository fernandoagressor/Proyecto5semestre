package com.inmobiliaria.inmobiliaria_api.service.administracion;

import com.inmobiliaria.inmobiliaria_api.dto.administracion.AdministracionDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.empresa.Empresa;
import com.inmobiliaria.inmobiliaria_api.entity.identidad.Persona;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import com.inmobiliaria.inmobiliaria_api.repository.empresa.EmpresaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.identidad.PersonaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaRegistroService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministracionUsuarioService {

    private final EmpresaRepository empresaRepository;
    private final PersonaRepository personaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final AuditoriaRegistroService auditoria;

    public AdministracionUsuarioService(
            EmpresaRepository empresaRepository,
            PersonaRepository personaRepository,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate,
            AuditoriaRegistroService auditoria
    ) {
        this.empresaRepository = empresaRepository;
        this.personaRepository = personaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<AdministracionDtos.UsuarioResponse> listar(
            Long empresaId,
            String texto,
            String estado
    ) {
        verificarEmpresa(empresaId, false);
        String busqueda = opcional(texto);
        if (busqueda != null) {
            busqueda = "%" + busqueda.toLowerCase(Locale.ROOT) + "%";
        }
        String estadoNormalizado = opcional(estado);
        if (estadoNormalizado != null) {
            estadoNormalizado = estadoNormalizado.toUpperCase(Locale.ROOT);
        }
        List<AdministracionDtos.UsuarioResponse> usuarios = jdbcTemplate.query("""
                SELECT u.id, u.empresa_id, u.persona_id,
                       TRIM(CONCAT(p.nombres, ' ', COALESCE(p.apellidos, ''))) nombre_completo,
                       p.nombres, p.apellidos, p.tipo_documento, p.numero_documento, p.telefono,
                       u.nombre_usuario, u.correo, u.estado,
                       u.requiere_cambio_password, u.mfa_habilitado,
                       u.ultimo_acceso_en, u.version
                FROM usuarios u
                JOIN personas p ON p.id = u.persona_id
                WHERE u.empresa_id = ?
                  AND (? IS NULL OR LOWER(CONCAT_WS(' ', p.nombres, p.apellidos,
                        p.numero_documento, u.nombre_usuario, u.correo)) LIKE ?)
                  AND (? IS NULL OR u.estado = ?)
                ORDER BY p.nombres, p.apellidos, u.id
                LIMIT 500
                """, (rs, rowNum) -> mapearUsuario(rs),
                empresaId,
                busqueda, busqueda,
                estadoNormalizado, estadoNormalizado);
        return usuarios.stream().map(this::completar).toList();
    }

    @Transactional(readOnly = true)
    public AdministracionDtos.UsuarioResponse obtener(
            Long empresaId,
            Long usuarioId
    ) {
        verificarEmpresa(empresaId, false);
        return completar(buscarBasico(empresaId, usuarioId));
    }

    @Transactional
    public AdministracionDtos.UsuarioResponse crear(
            Long actorId,
            Long empresaId,
            AdministracionDtos.UsuarioCrearRequest request
    ) {
        Empresa empresa = verificarEmpresa(empresaId, true);
        if (usuarioRepository.countByEmpresaId(empresaId) >= empresa.getLimiteUsuarios()) {
            throw new ReglaNegocioException(
                    "La empresa alcanzó el límite de usuarios de su plan"
            );
        }
        validarIdentificadoresDisponibles(
                request.nombreUsuario(),
                request.correo(),
                null
        );
        validarRoles(empresaId, request.rolIds());
        validarAlcances(empresaId, request.alcances());

        String tipoDocumento = requerido(request.tipoDocumento()).toUpperCase(Locale.ROOT);
        String numeroDocumento = requerido(request.numeroDocumento());
        Persona persona = personaRepository
                .findByTipoDocumentoAndNumeroDocumento(tipoDocumento, numeroDocumento)
                .orElseGet(() -> personaRepository.save(new Persona(
                        tipoDocumento,
                        numeroDocumento,
                        requerido(request.nombres()),
                        opcional(request.apellidos()),
                        requerido(request.correo()).toLowerCase(Locale.ROOT)
                )));
        if (request.telefono() != null && !request.telefono().isBlank()) {
            jdbcTemplate.update(
                    "UPDATE personas SET telefono = ? WHERE id = ?",
                    request.telefono().trim(),
                    persona.getId()
            );
        }
        Usuario usuario = usuarioRepository.saveAndFlush(new Usuario(
                empresaId,
                persona.getId(),
                requerido(request.nombreUsuario()).toLowerCase(Locale.ROOT),
                requerido(request.correo()).toLowerCase(Locale.ROOT),
                passwordEncoder.encode(request.passwordTemporal())
        ));
        asignarRoles(usuario.getId(), actorId, request.rolIds());
        asignarAlcances(usuario.getId(), empresaId, actorId, request.alcances());

        AdministracionDtos.UsuarioResponse response = obtener(empresaId, usuario.getId());
        auditoria.registrar(
                new ContextoUsuario(actorId, empresaId),
                null,
                "SUPERADMINISTRACION",
                "USUARIO_CREADO",
                "USUARIO",
                usuario.getId(),
                "Usuario interno creado desde la consola global",
                null,
                response
        );
        return response;
    }

    @Transactional
    public AdministracionDtos.UsuarioResponse actualizar(
            Long actorId,
            Long empresaId,
            Long usuarioId,
            AdministracionDtos.UsuarioActualizarRequest request
    ) {
        verificarEmpresa(empresaId, false);
        AdministracionDtos.UsuarioResponse anterior = obtener(empresaId, usuarioId);
        verificarNoProtegido(usuarioId);
        if (!anterior.version().equals(request.version())) {
            throw new ConflictoException(
                    "El usuario fue modificado por otro administrador; recarga la información"
            );
        }
        validarIdentificadoresDisponibles(
                anterior.nombreUsuario(),
                request.correo(),
                usuarioId
        );
        validarRoles(empresaId, request.rolIds());
        validarAlcances(empresaId, request.alcances());

        jdbcTemplate.update("""
                UPDATE personas
                SET nombres = ?, apellidos = ?, telefono = ?, correo = ?
                WHERE id = ?
                """,
                requerido(request.nombres()),
                opcional(request.apellidos()),
                opcional(request.telefono()),
                requerido(request.correo()).toLowerCase(Locale.ROOT),
                anterior.personaId());
        int actualizados = jdbcTemplate.update("""
                UPDATE usuarios
                SET correo = ?, version = version + 1
                WHERE id = ? AND empresa_id = ? AND version = ?
                """,
                requerido(request.correo()).toLowerCase(Locale.ROOT),
                usuarioId,
                empresaId,
                request.version());
        if (actualizados != 1) {
            throw new ConflictoException(
                    "El usuario fue modificado por otro administrador; recarga la información"
            );
        }
        asignarRoles(usuarioId, actorId, request.rolIds());
        asignarAlcances(usuarioId, empresaId, actorId, request.alcances());

        AdministracionDtos.UsuarioResponse nuevo = obtener(empresaId, usuarioId);
        auditoria.registrar(
                new ContextoUsuario(actorId, empresaId),
                null,
                "SUPERADMINISTRACION",
                "USUARIO_ACTUALIZADO",
                "USUARIO",
                usuarioId,
                "Datos, roles y alcances del usuario actualizados",
                anterior,
                nuevo
        );
        return nuevo;
    }

    @Transactional
    public AdministracionDtos.UsuarioResponse cambiarEstado(
            Long actorId,
            Long empresaId,
            Long usuarioId,
            AdministracionDtos.CambioEstadoUsuarioRequest request
    ) {
        AdministracionDtos.UsuarioResponse anterior = obtener(empresaId, usuarioId);
        verificarNoProtegido(usuarioId);
        if (!anterior.version().equals(request.version())) {
            throw new ConflictoException(
                    "El usuario fue modificado por otro administrador; recarga la información"
            );
        }
        int actualizados = jdbcTemplate.update("""
                UPDATE usuarios
                SET estado = ?, intentos_fallidos = 0, bloqueado_hasta = NULL,
                    version = version + 1
                WHERE id = ? AND empresa_id = ? AND version = ?
                """,
                request.estado().toUpperCase(Locale.ROOT),
                usuarioId,
                empresaId,
                request.version());
        if (actualizados != 1) {
            throw new ConflictoException(
                    "El usuario fue modificado por otro administrador; recarga la información"
            );
        }
        if (!"ACTIVO".equalsIgnoreCase(request.estado())) {
            revocarSesiones(usuarioId, request.motivo());
        }
        AdministracionDtos.UsuarioResponse nuevo = obtener(empresaId, usuarioId);
        auditoria.registrar(
                new ContextoUsuario(actorId, empresaId),
                null,
                "SUPERADMINISTRACION",
                "USUARIO_ESTADO_CAMBIADO",
                "USUARIO",
                usuarioId,
                request.motivo().trim(),
                anterior.estado(),
                nuevo.estado()
        );
        return nuevo;
    }

    @Transactional
    public void reiniciarCredenciales(
            Long actorId,
            Long empresaId,
            Long usuarioId,
            AdministracionDtos.ReinicioCredencialesRequest request
    ) {
        obtener(empresaId, usuarioId);
        verificarNoProtegido(usuarioId);
        jdbcTemplate.update("""
                UPDATE usuarios
                SET password_hash = ?, requiere_cambio_password = 1,
                    intentos_fallidos = 0, bloqueado_hasta = NULL,
                    token_version = token_version + 1,
                    version = version + 1
                WHERE id = ? AND empresa_id = ?
                """,
                passwordEncoder.encode(request.passwordTemporal()),
                usuarioId,
                empresaId);
        revocarSesiones(usuarioId, request.motivo());
        auditoria.registrar(
                new ContextoUsuario(actorId, empresaId),
                null,
                "SUPERADMINISTRACION",
                "CREDENCIALES_REINICIADAS",
                "USUARIO",
                usuarioId,
                request.motivo().trim(),
                null,
                "Contraseña temporal asignada y sesiones revocadas"
        );
    }

    private AdministracionDtos.UsuarioResponse mapearUsuario(ResultSet rs)
            throws SQLException {
        return new AdministracionDtos.UsuarioResponse(
                rs.getLong("id"),
                rs.getLong("empresa_id"),
                rs.getLong("persona_id"),
                rs.getString("nombre_completo"),
                rs.getString("nombres"),
                rs.getString("apellidos"),
                rs.getString("tipo_documento"),
                rs.getString("numero_documento"),
                rs.getString("nombre_usuario"),
                rs.getString("correo"),
                rs.getString("telefono"),
                rs.getString("estado"),
                rs.getBoolean("requiere_cambio_password"),
                rs.getBoolean("mfa_habilitado"),
                rs.getTimestamp("ultimo_acceso_en") == null
                        ? null
                        : rs.getTimestamp("ultimo_acceso_en").toLocalDateTime(),
                List.of(),
                List.of(),
                List.of(),
                rs.getLong("version")
        );
    }

    private AdministracionDtos.UsuarioResponse completar(
            AdministracionDtos.UsuarioResponse usuario
    ) {
        List<Long> rolIds = new ArrayList<>();
        List<String> roles = jdbcTemplate.query("""
                SELECT r.id, r.codigo
                FROM usuario_roles ur
                JOIN roles r ON r.id = ur.rol_id
                WHERE ur.usuario_id = ?
                ORDER BY ur.principal DESC, r.nombre
                """, (rs, rowNum) -> {
            rolIds.add(rs.getLong("id"));
            return rs.getString("codigo");
        }, usuario.id());
        List<AdministracionDtos.AlcanceResponse> alcances = jdbcTemplate.query("""
                SELECT ua.id, ua.tipo_alcance, ua.proyecto_id, p.nombre proyecto_nombre
                FROM usuario_alcances ua
                LEFT JOIN proyectos p ON p.id = ua.proyecto_id
                WHERE ua.usuario_id = ?
                  AND ua.empresa_id = ?
                  AND ua.vigente_desde <= CURRENT_TIMESTAMP(6)
                  AND (ua.vigente_hasta IS NULL OR ua.vigente_hasta > CURRENT_TIMESTAMP(6))
                ORDER BY ua.tipo_alcance, p.nombre
                """, (rs, rowNum) -> new AdministracionDtos.AlcanceResponse(
                rs.getLong("id"),
                rs.getString("tipo_alcance"),
                nullableLong(rs, "proyecto_id"),
                rs.getString("proyecto_nombre")
        ), usuario.id(), usuario.empresaId());
        return new AdministracionDtos.UsuarioResponse(
                usuario.id(),
                usuario.empresaId(),
                usuario.personaId(),
                usuario.nombreCompleto(),
                usuario.nombres(),
                usuario.apellidos(),
                usuario.tipoDocumento(),
                usuario.numeroDocumento(),
                usuario.nombreUsuario(),
                usuario.correo(),
                usuario.telefono(),
                usuario.estado(),
                usuario.requiereCambioPassword(),
                usuario.mfaHabilitado(),
                usuario.ultimoAccesoEn(),
                List.copyOf(rolIds),
                roles,
                alcances,
                usuario.version()
        );
    }

    private AdministracionDtos.UsuarioResponse buscarBasico(
            Long empresaId,
            Long usuarioId
    ) {
        List<AdministracionDtos.UsuarioResponse> resultado = jdbcTemplate.query("""
                SELECT u.id, u.empresa_id, u.persona_id,
                       TRIM(CONCAT(p.nombres, ' ', COALESCE(p.apellidos, ''))) nombre_completo,
                       p.nombres, p.apellidos, p.tipo_documento, p.numero_documento, p.telefono,
                       u.nombre_usuario, u.correo, u.estado,
                       u.requiere_cambio_password, u.mfa_habilitado,
                       u.ultimo_acceso_en, u.version
                FROM usuarios u
                JOIN personas p ON p.id = u.persona_id
                WHERE u.id = ? AND u.empresa_id = ?
                """, (rs, rowNum) -> mapearUsuario(rs), usuarioId, empresaId);
        if (resultado.isEmpty()) {
            throw new RecursoNoEncontradoException(
                    "El usuario no existe dentro de la empresa"
            );
        }
        return resultado.getFirst();
    }

    private void asignarRoles(Long usuarioId, Long actorId, List<Long> rolIds) {
        jdbcTemplate.update("DELETE FROM usuario_roles WHERE usuario_id = ?", usuarioId);
        for (int i = 0; i < rolIds.size(); i++) {
            jdbcTemplate.update("""
                    INSERT INTO usuario_roles
                        (usuario_id, rol_id, principal, asignado_por,
                         creado_en, actualizado_en)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """, usuarioId, rolIds.get(i), i == 0, actorId);
        }
    }

    private void asignarAlcances(
            Long usuarioId,
            Long empresaId,
            Long actorId,
            List<AdministracionDtos.AlcanceRequest> alcances
    ) {
        jdbcTemplate.update(
                "DELETE FROM usuario_alcances WHERE usuario_id = ? AND empresa_id = ?",
                usuarioId,
                empresaId
        );
        for (AdministracionDtos.AlcanceRequest alcance : alcances) {
            jdbcTemplate.update("""
                    INSERT INTO usuario_alcances
                        (usuario_id, empresa_id, proyecto_id, tipo_alcance,
                         asignado_por, vigente_desde, creado_en, actualizado_en)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6),
                            CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """,
                    usuarioId,
                    empresaId,
                    alcance.proyectoId(),
                    alcance.tipo().toUpperCase(Locale.ROOT),
                    actorId);
        }
    }

    private void validarRoles(Long empresaId, List<Long> rolIds) {
        if (new HashSet<>(rolIds).size() != rolIds.size()) {
            throw new ReglaNegocioException("No repitas roles en la asignación");
        }
        for (Long rolId : rolIds) {
            Long validos = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM roles
                    WHERE id = ? AND activo = 1
                      AND codigo <> 'SUPER_ADMIN'
                      AND (empresa_id IS NULL OR empresa_id = ?)
                    """, Long.class, rolId, empresaId);
            if (validos == null || validos != 1) {
                throw new ReglaNegocioException(
                        "Uno de los roles no pertenece a la empresa"
                );
            }
        }
    }

    private void validarAlcances(
            Long empresaId,
            List<AdministracionDtos.AlcanceRequest> alcances
    ) {
        Set<String> unicos = new HashSet<>();
        for (AdministracionDtos.AlcanceRequest alcance : alcances) {
            String tipo = alcance.tipo().toUpperCase(Locale.ROOT);
            if ("PROYECTO".equals(tipo)) {
                if (alcance.proyectoId() == null) {
                    throw new ReglaNegocioException(
                            "El alcance PROYECTO requiere seleccionar un proyecto"
                    );
                }
                Long existe = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM proyectos WHERE id = ? AND empresa_id = ?",
                        Long.class,
                        alcance.proyectoId(),
                        empresaId
                );
                if (existe == null || existe != 1) {
                    throw new ReglaNegocioException(
                            "El proyecto del alcance no pertenece a la empresa"
                    );
                }
            } else if (alcance.proyectoId() != null) {
                throw new ReglaNegocioException(
                        "Solo el alcance PROYECTO puede incluir un proyecto"
                );
            }
            String clave = tipo + ":" + alcance.proyectoId();
            if (!unicos.add(clave)) {
                throw new ReglaNegocioException("No repitas alcances");
            }
        }
    }

    private void validarIdentificadoresDisponibles(
            String nombreUsuario,
            String correo,
            Long usuarioId
    ) {
        usuarioRepository.findByIdentificador(nombreUsuario.trim())
                .filter(usuario -> !usuario.getId().equals(usuarioId))
                .ifPresent(usuario -> {
                    throw new ConflictoException(
                            "El nombre de usuario ya está registrado"
                    );
                });
        usuarioRepository.findByIdentificador(correo.trim())
                .filter(usuario -> !usuario.getId().equals(usuarioId))
                .ifPresent(usuario -> {
                    throw new ConflictoException("El correo ya está registrado");
                });
    }

    private void verificarNoProtegido(Long usuarioId) {
        Long rolesProtegidos = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM usuario_roles ur
                JOIN roles r ON r.id = ur.rol_id
                WHERE ur.usuario_id = ? AND r.codigo = 'SUPER_ADMIN'
                """, Long.class, usuarioId);
        if (rolesProtegidos != null && rolesProtegidos > 0) {
            throw new ReglaNegocioException(
                    "La cuenta SUPER_ADMIN no puede modificarse desde una empresa"
            );
        }
    }

    private Empresa verificarEmpresa(Long empresaId, boolean exigirActiva) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La empresa no existe"
                ));
        if (exigirActiva && !"ACTIVA".equals(empresa.getEstado())) {
            throw new ReglaNegocioException(
                    "La empresa debe estar activa para crear usuarios"
            );
        }
        return empresa;
    }

    private void revocarSesiones(Long usuarioId, String motivo) {
        jdbcTemplate.update("""
                UPDATE sesiones_usuario
                SET revocada_en = CURRENT_TIMESTAMP(6), motivo_revocacion = ?
                WHERE usuario_id = ? AND revocada_en IS NULL
                """, limitar(motivo, 200), usuarioId);
    }

    private Long nullableLong(ResultSet rs, String columna) throws SQLException {
        long valor = rs.getLong(columna);
        return rs.wasNull() ? null : valor;
    }

    private String limitar(String valor, int longitud) {
        String normalizado = requerido(valor);
        return normalizado.length() <= longitud
                ? normalizado
                : normalizado.substring(0, longitud);
    }

    private String requerido(String valor) {
        return valor.trim();
    }

    private String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
