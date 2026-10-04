package com.inmobiliaria.inmobiliaria_api.service.administracion;

import com.inmobiliaria.inmobiliaria_api.dto.administracion.AdministracionDtos;
import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.empresa.EmpresaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.empresa.Empresa;
import com.inmobiliaria.inmobiliaria_api.entity.identidad.Persona;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import com.inmobiliaria.inmobiliaria_api.repository.empresa.EmpresaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.identidad.PersonaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.ProyectoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.empresa.ModuloEmpresaService;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaRegistroService;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministracionEmpresaService {

    private final EmpresaRepository empresaRepository;
    private final PersonaRepository personaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProyectoRepository proyectoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final ModuloEmpresaService moduloEmpresaService;
    private final AuditoriaRegistroService auditoria;

    public AdministracionEmpresaService(
            EmpresaRepository empresaRepository,
            PersonaRepository personaRepository,
            UsuarioRepository usuarioRepository,
            ProyectoRepository proyectoRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate,
            ModuloEmpresaService moduloEmpresaService,
            AuditoriaRegistroService auditoria
    ) {
        this.empresaRepository = empresaRepository;
        this.personaRepository = personaRepository;
        this.usuarioRepository = usuarioRepository;
        this.proyectoRepository = proyectoRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.moduloEmpresaService = moduloEmpresaService;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public AdministracionDtos.ResumenGlobalResponse resumen() {
        return new AdministracionDtos.ResumenGlobalResponse(
                contar("SELECT COUNT(*) FROM empresas"),
                contar("SELECT COUNT(*) FROM empresas WHERE estado = 'ACTIVA'"),
                contar("SELECT COUNT(*) FROM empresas WHERE estado = 'SUSPENDIDA'"),
                contar("SELECT COUNT(*) FROM empresas WHERE estado = 'INACTIVA'"),
                contar("SELECT COUNT(*) FROM proyectos"),
                contar("SELECT COUNT(*) FROM usuarios WHERE empresa_id IS NOT NULL"),
                contar("SELECT COUNT(*) FROM usuarios WHERE empresa_id IS NOT NULL AND estado = 'ACTIVO'")
        );
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AdministracionDtos.EmpresaResponse> listar(
            String texto,
            String estado,
            int pagina,
            int tamano
    ) {
        Specification<Empresa> filtro = Specification.where(null);
        String busqueda = opcional(texto);
        if (busqueda != null) {
            String patron = "%" + busqueda.toLowerCase(Locale.ROOT) + "%";
            filtro = filtro.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("codigo")), patron),
                    cb.like(cb.lower(root.get("nombre")), patron),
                    cb.like(cb.lower(root.get("nit")), patron),
                    cb.like(cb.lower(root.get("ciudad")), patron)
            ));
        }
        String estadoNormalizado = opcional(estado);
        if (estadoNormalizado != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(
                    root.get("estado"),
                    estadoNormalizado.toUpperCase(Locale.ROOT)
            ));
        }
        Page<Empresa> resultado = empresaRepository.findAll(
                filtro,
                PageRequest.of(
                        Math.max(pagina, 0),
                        Math.min(Math.max(tamano, 1), 100),
                        Sort.by(Sort.Direction.ASC, "nombre")
                )
        );
        return PaginaResponse.from(resultado, this::respuesta);
    }

    @Transactional(readOnly = true)
    public AdministracionDtos.EmpresaResponse obtener(Long empresaId) {
        return respuesta(buscar(empresaId));
    }

    @Transactional
    public AdministracionDtos.EmpresaResponse crear(
            Long actorId,
            AdministracionDtos.EmpresaCrearRequest request
    ) {
        String codigo = requerido(request.codigo()).toUpperCase(Locale.ROOT);
        String nit = requerido(request.nit()).toUpperCase(Locale.ROOT);
        validarUnicidad(codigo, nit, null);
        validarAdministradorDisponible(request.administrador());

        Empresa empresa = new Empresa(codigo, requerido(request.nombre()), nit);
        empresa.actualizar(
                codigo,
                requerido(request.nombre()),
                nit,
                opcional(request.direccion()),
                opcional(request.ciudad()),
                opcional(request.departamento()),
                opcional(request.telefono()),
                correoOpcional(request.correo()),
                opcional(request.logoUrl()),
                requerido(request.zonaHoraria()),
                requerido(request.moneda()).toUpperCase(Locale.ROOT),
                request.limiteProyectos(),
                request.limiteUsuarios()
        );
        empresa = empresaRepository.saveAndFlush(empresa);
        moduloEmpresaService.guardar(empresa.getId(), request.modulos());
        crearAdministradorInicial(empresa, actorId, request.administrador());
        concederAlcanceSuperAdministradores(empresa.getId());

        auditoria.registrar(
                new ContextoUsuario(actorId, empresa.getId()),
                null,
                "SUPERADMINISTRACION",
                "EMPRESA_CREADA",
                "EMPRESA",
                empresa.getId(),
                "Empresa creada con administrador inicial",
                null,
                respuesta(empresa)
        );
        return respuesta(empresa);
    }

    @Transactional
    public AdministracionDtos.EmpresaResponse actualizar(
            Long actorId,
            Long empresaId,
            AdministracionDtos.EmpresaActualizarRequest request
    ) {
        Empresa empresa = buscar(empresaId);
        if (!empresa.getVersion().equals(request.version())) {
            throw new ConflictoException(
                    "La empresa fue modificada por otro usuario; recarga la información"
            );
        }
        long proyectos = proyectoRepository.countByEmpresaId(empresaId);
        long usuarios = usuarioRepository.countByEmpresaId(empresaId);
        if (request.limiteProyectos() < proyectos
                || request.limiteUsuarios() < usuarios) {
            throw new ReglaNegocioException(
                    "Los límites no pueden ser menores a los registros existentes"
            );
        }

        String codigo = requerido(request.codigo()).toUpperCase(Locale.ROOT);
        String nit = requerido(request.nit()).toUpperCase(Locale.ROOT);
        validarUnicidad(codigo, nit, empresaId);
        AdministracionDtos.EmpresaResponse anterior = respuesta(empresa);
        empresa.actualizar(
                codigo,
                requerido(request.nombre()),
                nit,
                opcional(request.direccion()),
                opcional(request.ciudad()),
                opcional(request.departamento()),
                opcional(request.telefono()),
                correoOpcional(request.correo()),
                opcional(request.logoUrl()),
                requerido(request.zonaHoraria()),
                requerido(request.moneda()).toUpperCase(Locale.ROOT),
                request.limiteProyectos(),
                request.limiteUsuarios()
        );
        moduloEmpresaService.guardar(empresaId, request.modulos());
        empresaRepository.flush();
        AdministracionDtos.EmpresaResponse nueva = respuesta(empresa);
        auditoria.registrar(
                new ContextoUsuario(actorId, empresaId),
                null,
                "SUPERADMINISTRACION",
                "EMPRESA_ACTUALIZADA",
                "EMPRESA",
                empresaId,
                "Datos y límites de empresa actualizados",
                anterior,
                nueva
        );
        return nueva;
    }

    @Transactional
    public AdministracionDtos.EmpresaResponse cambiarEstado(
            Long actorId,
            Long empresaId,
            AdministracionDtos.CambioEstadoEmpresaRequest request
    ) {
        Empresa empresa = buscar(empresaId);
        if (!empresa.getVersion().equals(request.version())) {
            throw new ConflictoException(
                    "La empresa fue modificada por otro usuario; recarga la información"
            );
        }
        String nuevoEstado = requerido(request.estado()).toUpperCase(Locale.ROOT);
        if (nuevoEstado.equals(empresa.getEstado())) {
            return respuesta(empresa);
        }
        if ("INACTIVA".equals(nuevoEstado)) {
            long proyectosOperativos = contar(
                    "SELECT COUNT(*) FROM proyectos WHERE empresa_id = ? AND estado NOT IN ('FINALIZADO','INACTIVO')",
                    empresaId
            );
            if (proyectosOperativos > 0) {
                throw new ReglaNegocioException(
                        "No se puede inactivar una empresa con proyectos operativos; usa suspensión"
                );
            }
        }
        String anterior = empresa.getEstado();
        empresa.cambiarEstado(nuevoEstado);
        empresaRepository.flush();
        if (!"ACTIVA".equals(nuevoEstado)) {
            jdbcTemplate.update("""
                    UPDATE sesiones_usuario s
                    JOIN usuarios u ON u.id = s.usuario_id
                    SET s.revocada_en = CURRENT_TIMESTAMP(6),
                        s.motivo_revocacion = ?
                    WHERE u.empresa_id = ?
                      AND s.revocada_en IS NULL
                      AND NOT EXISTS (
                        SELECT 1
                        FROM usuario_roles ur
                        JOIN roles r ON r.id = ur.rol_id
                        WHERE ur.usuario_id = u.id
                          AND r.codigo = 'SUPER_ADMIN'
                      )
                    """, "Empresa " + nuevoEstado.toLowerCase(Locale.ROOT), empresaId);
        }
        auditoria.registrar(
                new ContextoUsuario(actorId, empresaId),
                null,
                "SUPERADMINISTRACION",
                "EMPRESA_ESTADO_CAMBIADO",
                "EMPRESA",
                empresaId,
                request.motivo().trim(),
                anterior,
                nuevoEstado
        );
        return respuesta(empresa);
    }

    private void crearAdministradorInicial(
            Empresa empresa,
            Long actorId,
            AdministracionDtos.AdministradorInicialRequest request
    ) {
        String tipoDocumento = requerido(request.tipoDocumento()).toUpperCase(Locale.ROOT);
        String numeroDocumento = requerido(request.numeroDocumento());
        Persona persona = personaRepository
                .findByTipoDocumentoAndNumeroDocumento(tipoDocumento, numeroDocumento)
                .orElseGet(() -> personaRepository.save(new Persona(
                        "NATURAL",
                        tipoDocumento,
                        numeroDocumento,
                        requerido(request.nombres()),
                        opcional(request.apellidos()),
                        empresa.getTelefono(),
                        requerido(request.correo()).toLowerCase(Locale.ROOT),
                        empresa.getDireccion()
                )));
        Usuario usuario = usuarioRepository.saveAndFlush(new Usuario(
                empresa.getId(),
                persona.getId(),
                requerido(request.nombreUsuario()).toLowerCase(Locale.ROOT),
                requerido(request.correo()).toLowerCase(Locale.ROOT),
                passwordEncoder.encode(request.passwordTemporal())
        ));
        int roles = jdbcTemplate.update("""
                INSERT INTO usuario_roles
                    (usuario_id, rol_id, principal, asignado_por,
                     creado_en, actualizado_en)
                SELECT ?, r.id, 1, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                FROM roles r
                WHERE r.codigo = 'ADMINISTRADOR'
                  AND r.empresa_id IS NULL
                """, usuario.getId(), actorId);
        if (roles != 1) {
            throw new IllegalStateException(
                    "No fue posible asignar el rol ADMINISTRADOR"
            );
        }
        jdbcTemplate.update("""
                INSERT INTO usuario_alcances
                    (usuario_id, empresa_id, proyecto_id, tipo_alcance,
                     asignado_por, vigente_desde, creado_en, actualizado_en)
                VALUES (?, ?, NULL, 'EMPRESA', ?, CURRENT_TIMESTAMP(6),
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, usuario.getId(), empresa.getId(), actorId);
    }

    private void concederAlcanceSuperAdministradores(Long empresaId) {
        jdbcTemplate.update("""
                INSERT INTO usuario_alcances
                    (usuario_id, empresa_id, proyecto_id, tipo_alcance,
                     asignado_por, vigente_desde, creado_en, actualizado_en)
                SELECT u.id, ?, NULL, 'EMPRESA', u.id, CURRENT_TIMESTAMP(6),
                       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                FROM usuarios u
                JOIN usuario_roles ur ON ur.usuario_id = u.id
                JOIN roles r ON r.id = ur.rol_id
                WHERE r.codigo = 'SUPER_ADMIN'
                  AND r.empresa_id IS NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM usuario_alcances ua
                    WHERE ua.usuario_id = u.id
                      AND ua.empresa_id = ?
                      AND ua.proyecto_id IS NULL
                      AND ua.tipo_alcance = 'EMPRESA'
                  )
                """, empresaId, empresaId);
    }

    private void validarAdministradorDisponible(
            AdministracionDtos.AdministradorInicialRequest request
    ) {
        if (usuarioRepository.findByIdentificador(request.nombreUsuario().trim()).isPresent()) {
            throw new ConflictoException("El nombre de usuario ya está registrado");
        }
        if (usuarioRepository.findByIdentificador(request.correo().trim()).isPresent()) {
            throw new ConflictoException("El correo del administrador ya está registrado");
        }
    }

    private void validarUnicidad(String codigo, String nit, Long empresaId) {
        boolean codigoExiste = empresaId == null
                ? empresaRepository.existsByCodigoIgnoreCase(codigo)
                : empresaRepository.existsByCodigoIgnoreCaseAndIdNot(codigo, empresaId);
        boolean nitExiste = empresaId == null
                ? empresaRepository.existsByNitIgnoreCase(nit)
                : empresaRepository.existsByNitIgnoreCaseAndIdNot(nit, empresaId);
        if (codigoExiste) {
            throw new ConflictoException("El código de empresa ya está registrado");
        }
        if (nitExiste) {
            throw new ConflictoException("El NIT ya está registrado");
        }
    }

    private Empresa buscar(Long empresaId) {
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La empresa no existe"
                ));
    }

    private AdministracionDtos.EmpresaResponse respuesta(Empresa empresa) {
        return new AdministracionDtos.EmpresaResponse(
                empresa.getId(),
                empresa.getCodigo(),
                empresa.getNombre(),
                empresa.getNit(),
                empresa.getDireccion(),
                empresa.getCiudad(),
                empresa.getDepartamento(),
                empresa.getTelefono(),
                empresa.getCorreo(),
                empresa.getLogoUrl(),
                empresa.getZonaHoraria(),
                empresa.getMoneda(),
                empresa.getEstado(),
                empresa.getLimiteProyectos(),
                empresa.getLimiteUsuarios(),
                moduloEmpresaService.activos(empresa.getId()),
                proyectoRepository.countByEmpresaId(empresa.getId()),
                usuarioRepository.countByEmpresaId(empresa.getId()),
                empresa.getVersion(),
                empresa.getCreadoEn(),
                empresa.getActualizadoEn()
        );
    }

    private long contar(String sql, Object... argumentos) {
        Long valor = jdbcTemplate.queryForObject(sql, Long.class, argumentos);
        return valor == null ? 0 : valor;
    }

    private String requerido(String valor) {
        return valor.trim();
    }

    private String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private String correoOpcional(String valor) {
        String correo = opcional(valor);
        return correo == null ? null : correo.toLowerCase(Locale.ROOT);
    }
}
