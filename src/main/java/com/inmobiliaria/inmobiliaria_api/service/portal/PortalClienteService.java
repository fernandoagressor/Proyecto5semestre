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
import java.time.LocalDate;
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
import org.springframework.web.multipart.MultipartFile;
import com.inmobiliaria.inmobiliaria_api.service.ventas.contrato.ContratoService;

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
    private final ContratoService contratoService;

    public PortalClienteService(ServicioClienteAlcanceService alcanceService,
                                SolicitudCambioDatosRepository cambioRepository,
                                PersonaRepository personaRepository,
                                UsuarioRepository usuarioRepository,
                                NotificacionService notificacionService,
                                DocumentoVentaService documentoVentaService,
                                SolicitudPqrsRepository solicitudRepository,
                                CitaServicioRepository citaRepository,
                                JdbcTemplate jdbcTemplate,
                                ContratoService contratoService) {
        this.alcanceService = alcanceService;
        this.cambioRepository = cambioRepository;
        this.personaRepository = personaRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
        this.documentoVentaService = documentoVentaService;
        this.solicitudRepository = solicitudRepository;
        this.citaRepository = citaRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.contratoService = contratoService;
    }

    /** El cliente sube su contrato firmado; queda en revisión en Ventas. */
    public void subirContratoFirmado(ContextoUsuario contexto, Long contratoId, MultipartFile archivo) {
        contratoService.recibirFirmaDelCliente(contexto, clientePortal(contexto), contratoId, archivo);
    }

    @Transactional(readOnly = true)
    public PortalDtos.Resumen resumen(ContextoUsuario contexto) {
        Cliente cliente = clientePortal(contexto);
        String nombres = personaRepository.findById(cliente.getPersonaId()).map(Persona::getNombres).orElse(null);
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
                solicitudes, proximaCita, nombres),
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
            // También el contrato del cliente (para firmar o ya firmado): la versión vigente.
            disponibles = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM contrato_versiones v
                    JOIN contratos c ON c.id = v.contrato_id
                    WHERE c.empresa_id = ? AND v.vigente = 1 AND v.archivo_id = ?
                      AND c.estado NOT IN ('BORRADOR','ANULADO')
                      AND (c.cliente_principal_id = ? OR EXISTS (
                        SELECT 1 FROM contrato_clientes cc WHERE cc.contrato_id = c.id AND cc.cliente_id = ?))
                    """, Integer.class, contexto.empresaId(), archivoId, cliente.getId(), cliente.getId());
        }
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

    /** Contratos del cliente de la sesión (titular o cotitular) con su avance de pago. */
    @Transactional(readOnly = true)
    public List<PortalDtos.Contrato> contratos(ContextoUsuario contexto) {
        Cliente cliente = clientePortal(contexto);
        return jdbcTemplate.query("""
                SELECT c.id, c.codigo, c.estado, p.nombre proyecto, p.ciudad, l.codigo lote, l.area_m2,
                       c.valor_total, c.fecha_firma,
                       COALESCE(SUM(q.valor_pagado), 0) pagado,
                       COALESCE(SUM(CASE WHEN q.estado IN ('PENDIENTE','PARCIAL','VENCIDA') THEN q.saldo + q.interes_mora ELSE 0 END), 0) saldo_pendiente,
                       COALESCE(SUM(CASE WHEN q.estado = 'VENCIDA' THEN q.saldo + q.interes_mora ELSE 0 END), 0) saldo_vencido,
                       COUNT(CASE WHEN q.estado = 'PAGADA' THEN 1 END) cuotas_pagadas,
                       COUNT(q.id) cuotas_totales,
                       MIN(CASE WHEN q.estado IN ('PENDIENTE','PARCIAL','VENCIDA') THEN q.fecha_vencimiento END) proxima_fecha
                FROM contratos c
                JOIN proyectos p ON p.id = c.proyecto_id
                JOIN lotes l ON l.id = c.lote_id
                LEFT JOIN planes_pago pp ON pp.contrato_id = c.id AND pp.estado = 'ACTIVO'
                LEFT JOIN cuotas q ON q.plan_pago_id = pp.id
                WHERE c.empresa_id = ?
                  AND c.estado NOT IN ('BORRADOR','ANULADO')
                  AND (c.cliente_principal_id = ? OR EXISTS (
                    SELECT 1 FROM contrato_clientes cc WHERE cc.contrato_id = c.id AND cc.cliente_id = ?))
                GROUP BY c.id, c.codigo, c.estado, p.nombre, p.ciudad, l.codigo, l.area_m2, c.valor_total, c.fecha_firma
                ORDER BY c.fecha_firma DESC, c.id DESC
                """, (rs, rowNum) -> {
                    LocalDate proxima = rs.getObject("proxima_fecha", LocalDate.class);
                    long contratoId = rs.getLong("id");
                    BigDecimal valorProxima = proxima == null ? null : jdbcTemplate.queryForObject("""
                            SELECT COALESCE(SUM(q.saldo + q.interes_mora), 0)
                            FROM cuotas q JOIN planes_pago pp ON pp.id = q.plan_pago_id AND pp.estado = 'ACTIVO'
                            WHERE q.contrato_id = ? AND q.fecha_vencimiento = ?
                              AND q.estado IN ('PENDIENTE','PARCIAL','VENCIDA')
                            """, BigDecimal.class, contratoId, proxima);
                    java.sql.Date firma = rs.getDate("fecha_firma");
                    String[] firmaCliente = estadoFirmaDelCliente(contratoId, rs.getString("estado"), cliente.getPersonaId());
                    Long archivoContrato = jdbcTemplate.query(
                            "SELECT archivo_id FROM contrato_versiones WHERE contrato_id = ? AND vigente = 1 AND archivo_id IS NOT NULL",
                            (r, i) -> r.getLong("archivo_id"), contratoId).stream().findFirst().orElse(null);
                    return new PortalDtos.Contrato(contratoId, rs.getString("codigo"), rs.getString("estado"),
                            rs.getString("proyecto"), rs.getString("ciudad"), rs.getString("lote"),
                            rs.getBigDecimal("area_m2"), rs.getBigDecimal("valor_total"), rs.getBigDecimal("pagado"),
                            rs.getBigDecimal("saldo_pendiente"), rs.getBigDecimal("saldo_vencido"),
                            rs.getLong("cuotas_pagadas"), rs.getLong("cuotas_totales"), proxima, valorProxima,
                            firma == null ? null : firma.toLocalDate(), archivoContrato, firmaCliente[0], firmaCliente[1]);
                }, contexto.empresaId(), cliente.getId(), cliente.getId());
    }

    /**
     * Estado de la firma del cliente en su contrato: EN_PREPARACION, POR_FIRMAR, EN_REVISION,
     * RECHAZADA (con el motivo) o FIRMADO.
     */
    private String[] estadoFirmaDelCliente(long contratoId, String estadoContrato, Long personaId) {
        if ("VIGENTE".equals(estadoContrato) || "FINALIZADO".equals(estadoContrato)) {
            return new String[] {"FIRMADO", null};
        }
        if (!"PENDIENTE_FIRMA".equals(estadoContrato)) {
            return new String[] {"EN_PREPARACION", null};
        }
        List<String[]> filas = jdbcTemplate.query("""
                SELECT f.estado, fc.estado_validacion, fc.motivo_rechazo
                FROM contrato_firmantes f
                LEFT JOIN firmas_contrato fc ON fc.id = (
                    SELECT MAX(x.id) FROM firmas_contrato x WHERE x.contrato_firmante_id = f.id)
                WHERE f.contrato_id = ? AND f.persona_id = ?
                """, (r, i) -> new String[] {r.getString(1), r.getString(2), r.getString(3)}, contratoId, personaId);
        if (filas.isEmpty()) {
            return new String[] {"EN_PREPARACION", null};
        }
        String[] f = filas.get(0);
        if ("FIRMADA".equals(f[0])) return new String[] {"FIRMADO", null};
        if ("PENDIENTE".equals(f[1])) return new String[] {"EN_REVISION", null};
        if ("INVALIDA".equals(f[1])) return new String[] {"RECHAZADA", f[2]};
        return new String[] {"POR_FIRMAR", null};
    }

    /** Plan de pagos vigente de un contrato del cliente de la sesión. */
    @Transactional(readOnly = true)
    public List<PortalDtos.Cuota> cuotas(ContextoUsuario contexto, Long contratoId) {
        Cliente cliente = clientePortal(contexto);
        verificarContratoDelCliente(contexto, cliente, contratoId);
        return jdbcTemplate.query("""
                SELECT q.id, q.numero, q.fecha_vencimiento, q.valor_programado, q.valor_pagado, q.saldo,
                       q.interes_mora, q.dias_mora, q.estado
                FROM cuotas q
                JOIN planes_pago pp ON pp.id = q.plan_pago_id AND pp.estado = 'ACTIVO'
                WHERE q.empresa_id = ? AND q.contrato_id = ?
                ORDER BY q.numero
                """, (rs, rowNum) -> new PortalDtos.Cuota(rs.getLong("id"), rs.getInt("numero"),
                rs.getObject("fecha_vencimiento", LocalDate.class), rs.getBigDecimal("valor_programado"),
                rs.getBigDecimal("valor_pagado"), rs.getBigDecimal("saldo"), rs.getBigDecimal("interes_mora"),
                rs.getInt("dias_mora"), rs.getString("estado")), contexto.empresaId(), contratoId);
    }

    /** Últimos pagos del cliente de la sesión. */
    @Transactional(readOnly = true)
    public List<PortalDtos.Pago> pagos(ContextoUsuario contexto) {
        Cliente cliente = clientePortal(contexto);
        return jdbcTemplate.query("""
                SELECT pg.id, pg.codigo, c.codigo contrato, pg.fecha_pago, pg.valor, mp.nombre metodo,
                       pg.referencia, pg.estado
                FROM pagos pg
                JOIN metodos_pago mp ON mp.id = pg.metodo_pago_id
                LEFT JOIN contratos c ON c.id = pg.contrato_id
                WHERE pg.empresa_id = ? AND pg.cliente_id = ?
                ORDER BY pg.fecha_pago DESC
                LIMIT 50
                """, (rs, rowNum) -> new PortalDtos.Pago(rs.getLong("id"), rs.getString("codigo"),
                rs.getString("contrato"), rs.getTimestamp("fecha_pago").toLocalDateTime(), rs.getBigDecimal("valor"),
                rs.getString("metodo"), rs.getString("referencia"), rs.getString("estado")),
                contexto.empresaId(), cliente.getId());
    }

    private void verificarContratoDelCliente(ContextoUsuario contexto, Cliente cliente, Long contratoId) {
        Long propios = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM contratos c
                WHERE c.id = ? AND c.empresa_id = ?
                  AND (c.cliente_principal_id = ? OR EXISTS (
                    SELECT 1 FROM contrato_clientes cc WHERE cc.contrato_id = c.id AND cc.cliente_id = ?))
                """, Long.class, contratoId, contexto.empresaId(), cliente.getId(), cliente.getId());
        if (propios == null || propios == 0) {
            throw new RecursoNoEncontradoException("El contrato no existe");
        }
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
