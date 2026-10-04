package com.inmobiliaria.inmobiliaria_api.service.servicio;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.servicio.SolicitudDtos;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.documento.DocumentoVentaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.cliente.Cliente;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.EncuestaSatisfaccion;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.EstadoSolicitud;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.PrioridadServicio;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudAdjunto;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudHistorial;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudMensaje;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudPqrs;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.TipoSolicitud;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.EncuestaSatisfaccionRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.SolicitudAdjuntoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.SolicitudHistorialRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.SolicitudMensajeRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.SolicitudPqrsRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import com.inmobiliaria.inmobiliaria_api.service.notificacion.NotificacionService;
import com.inmobiliaria.inmobiliaria_api.service.ventas.documento.DocumentoVentaService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SolicitudPqrsService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SolicitudPqrsRepository repository;
    private final SolicitudMensajeRepository mensajeRepository;
    private final SolicitudHistorialRepository historialRepository;
    private final SolicitudAdjuntoRepository adjuntoRepository;
    private final EncuestaSatisfaccionRepository encuestaRepository;
    private final ServicioClienteAlcanceService alcanceService;
    private final NotificacionService notificacionService;
    private final DocumentoVentaService documentoVentaService;
    private final JdbcTemplate jdbcTemplate;

    public SolicitudPqrsService(SolicitudPqrsRepository repository,
                                SolicitudMensajeRepository mensajeRepository,
                                SolicitudHistorialRepository historialRepository,
                                SolicitudAdjuntoRepository adjuntoRepository,
                                EncuestaSatisfaccionRepository encuestaRepository,
                                ServicioClienteAlcanceService alcanceService,
                                NotificacionService notificacionService,
                                DocumentoVentaService documentoVentaService,
                                JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.mensajeRepository = mensajeRepository;
        this.historialRepository = historialRepository;
        this.adjuntoRepository = adjuntoRepository;
        this.encuestaRepository = encuestaRepository;
        this.alcanceService = alcanceService;
        this.notificacionService = notificacionService;
        this.documentoVentaService = documentoVentaService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<SolicitudDtos.Response> listar(ContextoUsuario contexto,
                                                         EstadoSolicitud estado,
                                                         TipoSolicitud tipo,
                                                         Long clienteId,
                                                         Long asignadoA,
                                                         int pagina, int tamano) {
        Cliente clienteSesion = alcanceService.clienteAutenticado(contexto).orElse(null);
        Specification<SolicitudPqrs> filtro = (root, query, cb) -> cb.equal(root.get("empresaId"), contexto.empresaId());
        if (clienteSesion != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("clienteId"), clienteSesion.getId()));
        } else {
            AlcanceProyectos alcance = alcanceService.alcanceInterno(contexto);
            if (!alcance.empresaCompleta()) {
                filtro = filtro.and((root, query, cb) -> {
                    var permitidos = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                    if (!alcance.proyectos().isEmpty()) permitidos.add(root.get("proyectoId").in(alcance.proyectos()));
                    if (alcance.registrosPropios()) permitidos.add(cb.equal(root.get("asignadoA"), contexto.usuarioId()));
                    return permitidos.isEmpty() ? cb.disjunction() : cb.or(permitidos.toArray(jakarta.persistence.criteria.Predicate[]::new));
                });
            }
            if (clienteId != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("clienteId"), clienteId));
        }
        if (estado != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        if (tipo != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("tipo"), tipo));
        if (asignadoA != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("asignadoA"), asignadoA));
        Page<SolicitudPqrs> resultado = repository.findAll(filtro, PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "creadoEn")));
        return PaginaResponse.from(resultado, SolicitudDtos.Response::from);
    }

    @Transactional
    public SolicitudDtos.Response crear(ContextoUsuario contexto, SolicitudDtos.CrearRequest request) {
        Cliente cliente = alcanceService.validarCliente(contexto, request.clienteId());
        alcanceService.validarRelacion(contexto, cliente.getId(), request.proyectoId(), request.contratoId());
        PrioridadServicio prioridad = request.prioridad() == null ? PrioridadServicio.MEDIA : request.prioridad();
        LocalDateTime ahora = ahora();
        SolicitudPqrs solicitud = new SolicitudPqrs(contexto.empresaId(), request.proyectoId(),
                codigo("PQRS", ahora), cliente.getId(), request.contratoId(), request.tipo(),
                requerido(request.categoria()), requerido(request.asunto()), requerido(request.descripcion()),
                prioridad, fechaLimite(ahora, prioridad));
        try {
            solicitud = repository.save(solicitud);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException("No fue posible asignar un código único a la solicitud");
        }
        mensajeRepository.save(new SolicitudMensaje(solicitud.getId(), contexto.usuarioId(),
                solicitud.getDescripcion(), false, "PORTAL"));
        return SolicitudDtos.Response.from(solicitud);
    }

    @Transactional(readOnly = true)
    public SolicitudDtos.Response obtener(ContextoUsuario contexto, Long id) {
        return SolicitudDtos.Response.from(obtenerAutorizada(contexto, id, false));
    }

    @Transactional(readOnly = true)
    public List<SolicitudDtos.MensajeResponse> mensajes(ContextoUsuario contexto, Long id) {
        obtenerAutorizada(contexto, id, false);
        boolean cliente = alcanceService.clienteAutenticado(contexto).isPresent();
        List<SolicitudMensaje> mensajes = cliente
                ? mensajeRepository.findBySolicitudIdAndInternoFalseOrderByCreadoEnAsc(id)
                : mensajeRepository.findBySolicitudIdOrderByCreadoEnAsc(id);
        return mensajes.stream().map(SolicitudDtos.MensajeResponse::from).toList();
    }

    @Transactional
    public SolicitudDtos.MensajeResponse responder(ContextoUsuario contexto, Long id,
                                                    SolicitudDtos.MensajeRequest request) {
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id, true);
        boolean cliente = alcanceService.clienteAutenticado(contexto).isPresent();
        if (cliente && request.interno()) {
            throw new ReglaNegocioException("El cliente no puede crear notas internas");
        }
        LocalDateTime fecha = ahora();
        if (!cliente && !request.interno()) {
            solicitud.registrarRespuesta(fecha);
        } else if (cliente && solicitud.getEstado() == EstadoSolicitud.PENDIENTE_CLIENTE) {
            EstadoSolicitud anterior = solicitud.cambiarEstado(EstadoSolicitud.EN_PROCESO, fecha);
            historialRepository.save(new SolicitudHistorial(id, "estado", anterior.name(),
                    solicitud.getEstado().name(), contexto.usuarioId(), "Respuesta del cliente"));
        }
        String canal = normalizarCanal(request.canal(), request.interno());
        SolicitudMensaje mensaje = mensajeRepository.save(new SolicitudMensaje(
                id, contexto.usuarioId(), requerido(request.mensaje()), request.interno(), canal));
        Long destinatario = cliente ? solicitud.getAsignadoA() : usuarioCliente(solicitud.getClienteId());
        if (!request.interno()) {
            notificacionService.crear(contexto.empresaId(), destinatario, "PQRS_RESPUESTA",
                    "Nueva respuesta en " + solicitud.getCodigo(), "La solicitud tiene una nueva respuesta",
                    "SOLICITUD_PQRS", solicitud.getId(), "MEDIA");
        }
        return SolicitudDtos.MensajeResponse.from(mensaje);
    }

    @Transactional
    public SolicitudDtos.Response asignar(ContextoUsuario contexto, Long id,
                                          SolicitudDtos.AsignarRequest request) {
        alcanceService.validarUsuarioEmpresa(contexto, request.usuarioId());
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id, true);
        Long anterior = solicitud.asignar(request.usuarioId());
        historialRepository.save(new SolicitudHistorial(id, "asignado_a", texto(anterior),
                texto(request.usuarioId()), contexto.usuarioId(), normalizar(request.motivo())));
        notificacionService.crear(contexto.empresaId(), request.usuarioId(), "PQRS_ASIGNADA",
                "Solicitud asignada " + solicitud.getCodigo(), solicitud.getAsunto(),
                "SOLICITUD_PQRS", id, solicitud.getPrioridad() == PrioridadServicio.CRITICA ? "ALTA" : "MEDIA");
        return SolicitudDtos.Response.from(solicitud);
    }

    @Transactional
    public SolicitudDtos.Response cambiarEstado(ContextoUsuario contexto, Long id,
                                                 SolicitudDtos.EstadoRequest request) {
        if (request.estado() == EstadoSolicitud.CERRADA) {
            throw new ReglaNegocioException("Use la operación de cierre protegida para cerrar la solicitud");
        }
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id, true);
        EstadoSolicitud anterior = solicitud.cambiarEstado(request.estado(), ahora());
        historialRepository.save(new SolicitudHistorial(id, "estado", anterior.name(),
                request.estado().name(), contexto.usuarioId(), normalizar(request.motivo())));
        notificacionService.crear(contexto.empresaId(), usuarioCliente(solicitud.getClienteId()),
                "PQRS_ESTADO", "Estado actualizado " + solicitud.getCodigo(),
                "La solicitud cambió a " + request.estado().name(), "SOLICITUD_PQRS", id, "MEDIA");
        return SolicitudDtos.Response.from(solicitud);
    }

    @Transactional
    public SolicitudDtos.Response cerrar(ContextoUsuario contexto, Long id, String motivo) {
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id, true);
        EstadoSolicitud anterior = solicitud.cambiarEstado(EstadoSolicitud.CERRADA, ahora());
        historialRepository.save(new SolicitudHistorial(id, "estado", anterior.name(),
                EstadoSolicitud.CERRADA.name(), contexto.usuarioId(), normalizar(motivo)));
        notificacionService.crear(contexto.empresaId(), usuarioCliente(solicitud.getClienteId()),
                "PQRS_CERRADA", "Solicitud cerrada " + solicitud.getCodigo(),
                "La atención fue cerrada", "SOLICITUD_PQRS", id, "MEDIA");
        return SolicitudDtos.Response.from(solicitud);
    }

    @Transactional
    public SolicitudDtos.AdjuntoResponse adjuntar(ContextoUsuario contexto, Long id,
                                                   SolicitudDtos.AdjuntoRequest request) {
        obtenerAutorizada(contexto, id, true);
        Integer archivos = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM archivos
                WHERE id = ? AND empresa_id = ? AND estado = 'ACTIVO'
                """, Integer.class, request.archivoId(), contexto.empresaId());
        if (archivos == null || archivos == 0) {
            throw new ReglaNegocioException("El archivo no existe, no pertenece a la empresa o no está activo");
        }
        if (request.mensajeId() != null && mensajeRepository.findByIdAndSolicitudId(request.mensajeId(), id).isEmpty()) {
            throw new ReglaNegocioException("El mensaje no pertenece a la solicitud");
        }
        if (adjuntoRepository.existsBySolicitudIdAndArchivoId(id, request.archivoId())) {
            throw new ConflictoException("El archivo ya está adjunto a la solicitud");
        }
        return adjuntoResponse(adjuntoRepository.save(
                new SolicitudAdjunto(id, request.mensajeId(), request.archivoId())), contexto.empresaId());
    }

    @Transactional
    public SolicitudDtos.AdjuntoResponse subirAdjunto(ContextoUsuario contexto, Long id,
                                                       Long mensajeId, MultipartFile archivo) {
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id, true);
        if (mensajeId != null && mensajeRepository.findByIdAndSolicitudId(mensajeId, id).isEmpty()) {
            throw new ReglaNegocioException("El mensaje no pertenece a la solicitud");
        }
        DocumentoVentaResponse documento = documentoVentaService
                .guardarAdjuntoSolicitudAutorizada(contexto, solicitud.getProyectoId(), archivo);
        SolicitudAdjunto adjunto = adjuntoRepository.save(
                new SolicitudAdjunto(id, mensajeId, documento.id()));
        return new SolicitudDtos.AdjuntoResponse(adjunto.getId(), adjunto.getMensajeId(),
                adjunto.getArchivoId(), documento.nombreOriginal(), documento.mimeType(),
                documento.tamanoBytes(), adjunto.getCreadoEn());
    }

    @Transactional(readOnly = true)
    public List<SolicitudDtos.AdjuntoResponse> adjuntos(ContextoUsuario contexto, Long id) {
        obtenerAutorizada(contexto, id, false);
        return adjuntoRepository.findBySolicitudIdOrderByCreadoEnAsc(id).stream()
                .map(adjunto -> adjuntoResponse(adjunto, contexto.empresaId())).toList();
    }

    @Transactional(readOnly = true)
    public DocumentoVentaService.Descarga descargarAdjunto(ContextoUsuario contexto, Long id, Long archivoId) {
        obtenerAutorizada(contexto, id, false);
        if (!adjuntoRepository.existsBySolicitudIdAndArchivoId(id, archivoId)) {
            throw new RecursoNoEncontradoException("El archivo no está adjunto a la solicitud");
        }
        return documentoVentaService.descargarEntidadAutorizada(contexto, archivoId);
    }

    @Transactional(readOnly = true)
    public List<SolicitudDtos.HistorialResponse> historial(ContextoUsuario contexto, Long id) {
        obtenerAutorizada(contexto, id, false);
        if (alcanceService.clienteAutenticado(contexto).isPresent()) {
            throw new ReglaNegocioException("El historial interno no está disponible en el portal del cliente");
        }
        return historialRepository.findBySolicitudIdOrderByCreadoEnAsc(id).stream()
                .map(SolicitudDtos.HistorialResponse::from).toList();
    }

    @Transactional
    public void responderEncuesta(ContextoUsuario contexto, Long id, SolicitudDtos.EncuestaRequest request) {
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id, true);
        Cliente cliente = alcanceService.clienteAutenticado(contexto)
                .orElseThrow(() -> new ReglaNegocioException("La encuesta debe responderla el cliente"));
        if (solicitud.getEstado() != EstadoSolicitud.CERRADA && solicitud.getEstado() != EstadoSolicitud.RESUELTA) {
            throw new ReglaNegocioException("La atención debe estar resuelta antes de calificarla");
        }
        if (encuestaRepository.existsBySolicitudIdAndClienteId(id, cliente.getId())) {
            throw new ConflictoException("La solicitud ya fue calificada");
        }
        encuestaRepository.save(new EncuestaSatisfaccion(contexto.empresaId(), cliente.getId(), id,
                request.puntuacion(), normalizar(request.comentario()), ahora()));
    }

    private SolicitudPqrs obtenerAutorizada(ContextoUsuario contexto, Long id, boolean bloqueo) {
        SolicitudPqrs solicitud = (bloqueo ? repository.findForUpdate(id, contexto.empresaId())
                : repository.findByIdAndEmpresaId(id, contexto.empresaId()))
                .orElseThrow(() -> new RecursoNoEncontradoException("La solicitud no existe"));
        alcanceService.validarRelacion(contexto, solicitud.getClienteId(), solicitud.getProyectoId(), solicitud.getContratoId());
        return solicitud;
    }

    private Long usuarioCliente(Long clienteId) {
        return jdbcTemplate.query("SELECT usuario_id FROM clientes WHERE id = ?",
                rs -> rs.next() ? rs.getObject(1, Long.class) : null, clienteId);
    }

    private SolicitudDtos.AdjuntoResponse adjuntoResponse(SolicitudAdjunto adjunto, Long empresaId) {
        return jdbcTemplate.query("""
                        SELECT nombre_original, mime_type, tamano_bytes
                        FROM archivos
                        WHERE id = ? AND empresa_id = ? AND estado = 'ACTIVO'
                        """,
                rs -> rs.next()
                        ? new SolicitudDtos.AdjuntoResponse(
                                adjunto.getId(), adjunto.getMensajeId(), adjunto.getArchivoId(),
                                rs.getString("nombre_original"), rs.getString("mime_type"),
                                rs.getLong("tamano_bytes"), adjunto.getCreadoEn())
                        : SolicitudDtos.AdjuntoResponse.from(adjunto),
                adjunto.getArchivoId(), empresaId);
    }

    private static LocalDateTime fechaLimite(LocalDateTime desde, PrioridadServicio prioridad) {
        long horas = switch (prioridad) {
            case CRITICA -> 4;
            case ALTA -> 24;
            case MEDIA -> 72;
            case BAJA -> 120;
        };
        return desde.plusHours(horas);
    }

    private static String codigo(String prefijo, LocalDateTime fecha) {
        return prefijo + "-" + DIA.format(fecha) + "-" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String requerido(String valor) {
        String normalizado = normalizar(valor);
        if (normalizado == null) throw new ReglaNegocioException("El valor es obligatorio");
        return normalizado;
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String normalizarCanal(String canal, boolean interno) {
        if (interno) return "INTERNO";
        String valor = canal == null ? "PORTAL" : canal.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PORTAL", "EMAIL", "TELEFONO").contains(valor)) {
            throw new ReglaNegocioException("El canal de respuesta no es válido");
        }
        return valor;
    }

    private static String texto(Object valor) { return valor == null ? null : valor.toString(); }
    private static LocalDateTime ahora() { return LocalDateTime.now(BOGOTA); }
}
