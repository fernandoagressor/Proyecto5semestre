package com.inmobiliaria.inmobiliaria_api.service.portal;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.portal.PortalDtos;
import com.inmobiliaria.inmobiliaria_api.entity.cliente.Cliente;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.identidad.Persona;
import com.inmobiliaria.inmobiliaria_api.entity.portal.SolicitudCambioDatos;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import com.inmobiliaria.inmobiliaria_api.repository.identidad.PersonaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.portal.SolicitudCambioDatosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.CitaServicioRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.SolicitudPqrsRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.notificacion.NotificacionService;
import com.inmobiliaria.inmobiliaria_api.service.servicio.ServicioClienteAlcanceService;
import com.inmobiliaria.inmobiliaria_api.service.ventas.documento.DocumentoVentaService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalClienteService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Set<String> CAMPOS = Set.of("CORREO", "TELEFONO", "DIRECCION", "NOMBRES", "APELLIDOS", "DOCUMENTO");
    private final ServicioClienteAlcanceService alcanceService;
    private final SolicitudCambioDatosRepository cambioRepository;
    private final PersonaRepository personaRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;
    private final DocumentoVentaService documentoVentaService;
    private final SolicitudPqrsRepository solicitudRepository;
    private final CitaServicioRepository citaRepository;
    private final JdbcTemplate jdbcTemplate;

    public PortalClienteService(ServicioClienteAlcanceService alcanceService,
                                SolicitudCambioDatosRepository cambioRepository,
                                PersonaRepository personaRepository,
                                UsuarioRepository usuarioRepository,
                                NotificacionService notificacionService,
                                DocumentoVentaService documentoVentaService,
                                SolicitudPqrsRepository solicitudRepository,
                                CitaServicioRepository citaRepository,
                                JdbcTemplate jdbcTemplate) {
        this.alcanceService = alcanceService;
        this.cambioRepository = cambioRepository;
        this.personaRepository = personaRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
        this.documentoVentaService = documentoVentaService;
        this.solicitudRepository = solicitudRepository;
        this.citaRepository = citaRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PortalDtos.Resumen resumen(ContextoUsuario contexto) {
        Cliente cliente = clientePortal(contexto);
        // Las PQRS viven en MongoDB desde la rama 5; se cuentan aparte.
        long solicitudes = solicitudRepository.contarAbiertasDeCliente(contexto.empresaId(), cliente.getId());
        // Las citas de servicio viven en MongoDB desde la rama 10.
        LocalDateTime proximaCita = citaRepository.proximaDeCliente(contexto.empresaId(), cliente.getId(),
                LocalDateTime.now(BOGOTA)).orElse(null);
        return jdbcTemplate.queryForObject("""
                SELECT
                  COUNT(DISTINCT CASE WHEN c.estado = 'VIGENTE' THEN c.id END) contratos_vigentes,
                  COUNT(DISTINCT CASE WHEN q.estado IN ('PENDIENTE','PARCIAL','VENCIDA') THEN q.id END) cuotas_pendientes,
                  COALESCE(SUM(CASE WHEN q.estado IN ('PENDIENTE','PARCIAL','VENCIDA') THEN q.saldo + q.interes_mora ELSE 0 END), 0) saldo_pendiente,
                  (SELECT COUNT(*) FROM documentos d WHERE d.empresa_id = ? AND d.cliente_id = ? AND d.visible_portal = 1 AND d.estado = 'VIGENTE') documentos
                FROM contratos c
                LEFT JOIN cuotas q ON q.contrato_id = c.id
                WHERE c.empresa_id = ?
                  AND (c.cliente_principal_id = ? OR EXISTS (
                    SELECT 1 FROM contrato_clientes cc
                    WHERE cc.contrato_id = c.id AND cc.cliente_id = ?
                  ))
                """, (rs, rowNum) -> new PortalDtos.Resumen(cliente.getId(), cliente.getCodigoCliente(),
                rs.getLong("contratos_vigentes"), rs.getLong("cuotas_pendientes"),
                rs.getBigDecimal("saldo_pendiente"), rs.getLong("documentos"),
                solicitudes, proximaCita),
                contexto.empresaId(), cliente.getId(),
                contexto.empresaId(), cliente.getId(), cliente.getId());
    }

    @Transactional(readOnly = true)
    public PaginaResponse<PortalDtos.Documento> documentos(ContextoUsuario contexto, int pagina, int tamano) {
        Cliente cliente = clientePortal(contexto);
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamano, 1), 100);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM documentos
                WHERE empresa_id = ? AND cliente_id = ? AND visible_portal = 1 AND estado = 'VIGENTE'
                """, Long.class, contexto.empresaId(), cliente.getId());
        List<PortalDtos.Documento> contenido = jdbcTemplate.query("""
                SELECT d.id, d.codigo, d.tipo_documento, d.nombre, d.proyecto_id,
                       d.entidad_tipo, d.entidad_id, d.actualizado_en,
                       dv.numero_version, a.id archivo_id, a.nombre_original,
                       a.mime_type, a.tamano_bytes, a.sha256
                FROM documentos d
                LEFT JOIN documento_versiones dv ON dv.id = (
                    SELECT dv2.id FROM documento_versiones dv2
                    WHERE dv2.documento_id = d.id AND dv2.vigente = 1
                    ORDER BY dv2.numero_version DESC LIMIT 1
                )
                LEFT JOIN archivos a ON a.id = dv.archivo_id AND a.estado = 'ACTIVO'
                WHERE d.empresa_id = ? AND d.cliente_id = ?
                  AND d.visible_portal = 1 AND d.estado = 'VIGENTE'
                ORDER BY d.actualizado_en DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new PortalDtos.Documento(rs.getLong("id"), rs.getString("codigo"),
                rs.getString("tipo_documento"), rs.getString("nombre"), rs.getObject("proyecto_id", Long.class),
                rs.getString("entidad_tipo"), rs.getLong("entidad_id"), rs.getObject("numero_version", Integer.class),
                rs.getObject("archivo_id", Long.class), rs.getString("nombre_original"), rs.getString("mime_type"),
                rs.getObject("tamano_bytes", Long.class), rs.getString("sha256"),
                rs.getTimestamp("actualizado_en").toLocalDateTime()), contexto.empresaId(), cliente.getId(), t, p * t);
        long n = total == null ? 0 : total;
        int totalPaginas = (int) Math.ceil(n / (double) t);
        return new PaginaResponse<>(contenido, p, t, n, totalPaginas, p == 0, p + 1 >= totalPaginas);
    }

    @Transactional(readOnly = true)
    public DocumentoVentaService.Descarga descargarDocumento(ContextoUsuario contexto, Long archivoId) {
        Cliente cliente = clientePortal(contexto);
        Integer disponibles = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM documentos d
                JOIN documento_versiones dv ON dv.documento_id = d.id
                WHERE d.empresa_id = ? AND d.cliente_id = ?
                  AND d.visible_portal = 1 AND d.estado = 'VIGENTE'
                  AND dv.vigente = 1 AND dv.archivo_id = ?
                """, Integer.class, contexto.empresaId(), cliente.getId(), archivoId);
        if (disponibles == null || disponibles == 0) {
            throw new RecursoNoEncontradoException("El documento no está disponible en el portal");
        }
        return documentoVentaService.descargarEntidadAutorizada(contexto, archivoId);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<PortalDtos.CambioResponse> cambios(ContextoUsuario contexto, String estado,
                                                             Long clienteId, int pagina, int tamano) {
        Cliente clienteSesion = alcanceService.clienteAutenticado(contexto).orElse(null);
        Specification<SolicitudCambioDatos> filtro = (root, query, cb) -> cb.equal(root.get("empresaId"), contexto.empresaId());
        if (clienteSesion != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("clienteId"), clienteSesion.getId()));
        else if (clienteId != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("clienteId"), clienteId));
        if (estado != null && !estado.isBlank()) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estado"), estado.toUpperCase(Locale.ROOT)));
        Page<SolicitudCambioDatos> resultado = cambioRepository.findAll(filtro, PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100), Sort.by(Sort.Direction.DESC, "creadoEn")));
        return PaginaResponse.from(resultado, PortalDtos.CambioResponse::from);
    }

    @Transactional
    public PortalDtos.CambioResponse solicitarCambio(ContextoUsuario contexto, PortalDtos.CambioCrearRequest request) {
        Cliente cliente = clientePortal(contexto);
        String campo = requerido(request.campo()).toUpperCase(Locale.ROOT);
        if (!CAMPOS.contains(campo)) throw new ReglaNegocioException("El campo no admite solicitudes de cambio");
        if (cambioRepository.existsByClienteIdAndCampoAndEstado(cliente.getId(), campo, "PENDIENTE")) {
            throw new ConflictoException("Ya existe una solicitud pendiente para ese campo");
        }
        Persona persona = personaRepository.findById(cliente.getPersonaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Los datos personales no existen"));
        String nuevo = validarValor(campo, request.valorSolicitado());
        String anterior = valorActual(persona, campo);
        if (nuevo.equalsIgnoreCase(anterior == null ? "" : anterior)) {
            throw new ReglaNegocioException("El valor solicitado es igual al actual");
        }
        SolicitudCambioDatos solicitud = new SolicitudCambioDatos(contexto.empresaId(), cliente.getId(),
                contexto.usuarioId(), campo, anterior, nuevo, requerido(request.motivo()));
        return PortalDtos.CambioResponse.from(cambioRepository.save(solicitud));
    }

    @Transactional
    public PortalDtos.CambioResponse aprobar(ContextoUsuario contexto, Long id, String comentario) {
        SolicitudCambioDatos solicitud = obtener(contexto, id);
        Cliente cliente = alcanceService.validarCliente(contexto, solicitud.getClienteId());
        if (alcanceService.clienteAutenticado(contexto).isPresent()) {
            throw new AccesoDenegadoException("El cliente no puede aprobar su propia solicitud");
        }
        Persona persona = personaRepository.findById(cliente.getPersonaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Los datos personales no existen"));
        String actual = valorActual(persona, solicitud.getCampo());
        if (!iguales(actual, solicitud.getValorAnterior())) {
            throw new ConflictoException("El dato cambió después de la solicitud; debe revisarse nuevamente");
        }
        persona.actualizarDato(solicitud.getCampo(), solicitud.getValorSolicitado());
        if ("CORREO".equals(solicitud.getCampo()) && cliente.getUsuarioId() != null) {
            Usuario usuario = usuarioRepository.findById(cliente.getUsuarioId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("La cuenta del cliente no existe"));
            usuario.actualizarCorreo(solicitud.getValorSolicitado());
        }
        solicitud.aprobar(contexto.usuarioId(), normalizar(comentario), LocalDateTime.now(BOGOTA));
        try {
            personaRepository.flush();
            usuarioRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException("El nuevo documento o correo ya está registrado");
        }
        notificacionService.crear(contexto.empresaId(), solicitud.getSolicitadoPor(), "DATOS_APROBADOS",
                "Cambio de datos aprobado", "Se aprobó el cambio de " + solicitud.getCampo(),
                "SOLICITUD_CAMBIO_DATOS", solicitud.getId(), "MEDIA");
        return PortalDtos.CambioResponse.from(solicitud);
    }

    @Transactional
    public PortalDtos.CambioResponse rechazar(ContextoUsuario contexto, Long id, String comentario) {
        SolicitudCambioDatos solicitud = obtener(contexto, id);
        if (alcanceService.clienteAutenticado(contexto).isPresent()) {
            throw new AccesoDenegadoException("El cliente no puede revisar solicitudes");
        }
        solicitud.rechazar(contexto.usuarioId(), comentario, LocalDateTime.now(BOGOTA));
        notificacionService.crear(contexto.empresaId(), solicitud.getSolicitadoPor(), "DATOS_RECHAZADOS",
                "Cambio de datos rechazado", comentario, "SOLICITUD_CAMBIO_DATOS", solicitud.getId(), "MEDIA");
        return PortalDtos.CambioResponse.from(solicitud);
    }

    @Transactional
    public PortalDtos.CambioResponse cancelar(ContextoUsuario contexto, Long id) {
        SolicitudCambioDatos solicitud = obtener(contexto, id);
        solicitud.cancelar(contexto.usuarioId());
        return PortalDtos.CambioResponse.from(solicitud);
    }

    private Cliente clientePortal(ContextoUsuario contexto) {
        return alcanceService.clienteAutenticado(contexto)
                .orElseThrow(() -> new AccesoDenegadoException("La operación está disponible únicamente en el portal del cliente"));
    }

    private SolicitudCambioDatos obtener(ContextoUsuario contexto, Long id) {
        SolicitudCambioDatos s = cambioRepository.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La solicitud de cambio no existe"));
        Cliente cliente = alcanceService.clienteAutenticado(contexto).orElse(null);
        if (cliente != null && !cliente.getId().equals(s.getClienteId())) {
            throw new AccesoDenegadoException("La solicitud no pertenece al cliente autenticado");
        }
        return s;
    }

    private static String valorActual(Persona p, String campo) {
        return switch (campo) {
            case "CORREO" -> p.getCorreo();
            case "TELEFONO" -> p.getTelefono();
            case "DIRECCION" -> p.getDireccion();
            case "NOMBRES" -> p.getNombres();
            case "APELLIDOS" -> p.getApellidos();
            case "DOCUMENTO" -> p.getNumeroDocumento();
            default -> null;
        };
    }

    private static String validarValor(String campo, String valor) {
        String n = requerido(valor);
        if ("CORREO".equals(campo) && !n.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ReglaNegocioException("El correo no tiene un formato válido");
        }
        return "CORREO".equals(campo) ? n.toLowerCase(Locale.ROOT) : n;
    }
    private static boolean iguales(String a, String b) { return a == null ? b == null : a.equals(b); }
    private static String requerido(String valor) { String n = normalizar(valor); if (n == null) throw new ReglaNegocioException("El valor es obligatorio"); return n; }
    private static String normalizar(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
}
