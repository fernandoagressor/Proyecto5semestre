package com.inmobiliaria.inmobiliaria_api.service.servicio;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.servicio.SolicitudDtos;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.documento.DocumentoVentaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.cliente.Cliente;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.EstadoSolicitud;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.PrioridadServicio;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudAdjunto;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudMensaje;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudPqrs;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.TipoSolicitud;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.SolicitudPqrsRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import com.inmobiliaria.inmobiliaria_api.service.notificacion.NotificacionService;
import com.inmobiliaria.inmobiliaria_api.service.ventas.documento.DocumentoVentaService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Atención de PQRS. Desde la rama 5 las solicitudes viven en MongoDB (colección
 * {@code solicitudes_pqrs}) con su conversación, adjuntos, historial y encuesta embebidos.
 *
 * <p>Cada operación lee el documento, aplica la regla de negocio y lo guarda con una sola
 * escritura; si otro agente lo modificó entretanto, la versión no coincide y se responde 409.
 * Las notificaciones, los clientes y los archivos siguen en MySQL: los métodos de escritura
 * conservan {@code @Transactional} (JPA) para esas tablas, pero esa transacción no cubre a MongoDB.
 * Por eso el documento se guarda antes de crear las notificaciones.</p>
 */
@Service
public class SolicitudPqrsService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SolicitudPqrsRepository repository;
    private final SecuenciaService secuencias;
    private final ServicioClienteAlcanceService alcanceService;
    private final NotificacionService notificacionService;
    private final DocumentoVentaService documentoVentaService;
    private final JdbcTemplate jdbcTemplate;

    public SolicitudPqrsService(SolicitudPqrsRepository repository,
                                SecuenciaService secuencias,
                                ServicioClienteAlcanceService alcanceService,
                                NotificacionService notificacionService,
                                DocumentoVentaService documentoVentaService,
                                JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.secuencias = secuencias;
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
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(where("empresaId").is(contexto.empresaId()));
        if (clienteSesion != null) {
            condiciones.add(where("clienteId").is(clienteSesion.getId()));
        } else {
            AlcanceProyectos alcance = alcanceService.alcanceInterno(contexto);
            if (!alcance.empresaCompleta()) {
                List<Criteria> permitidos = new ArrayList<>();
                if (!alcance.proyectos().isEmpty()) permitidos.add(where("proyectoId").in(alcance.proyectos()));
                if (alcance.registrosPropios()) permitidos.add(where("asignadoA").is(contexto.usuarioId()));
                condiciones.add(permitidos.isEmpty()
                        ? where("id").in(List.of())
                        : new Criteria().orOperator(permitidos));
            }
            if (clienteId != null) condiciones.add(where("clienteId").is(clienteId));
        }
        if (estado != null) condiciones.add(where("estado").is(estado.name()));
        if (tipo != null) condiciones.add(where("tipo").is(tipo.name()));
        if (asignadoA != null) condiciones.add(where("asignadoA").is(asignadoA));
        Page<SolicitudPqrs> resultado = repository.buscarResumen(
                new Criteria().andOperator(condiciones),
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                        Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id"))));
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
        solicitud.identificar(secuencias.siguiente(SecuenciasPqrs.SOLICITUDES));
        solicitud.agregarMensaje(secuencias.siguiente(SecuenciasPqrs.MENSAJES), contexto.usuarioId(),
                solicitud.getDescripcion(), false, "PORTAL");
        try {
            solicitud = repository.insertar(solicitud);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException("No fue posible asignar un código único a la solicitud");
        }
        return SolicitudDtos.Response.from(solicitud);
    }

    @Transactional(readOnly = true)
    public SolicitudDtos.Response obtener(ContextoUsuario contexto, Long id) {
        return SolicitudDtos.Response.from(obtenerAutorizada(contexto, id));
    }

    @Transactional(readOnly = true)
    public List<SolicitudDtos.MensajeResponse> mensajes(ContextoUsuario contexto, Long id) {
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id);
        boolean cliente = alcanceService.clienteAutenticado(contexto).isPresent();
        return solicitud.mensajes(!cliente).stream().map(SolicitudDtos.MensajeResponse::from).toList();
    }

    @Transactional
    public SolicitudDtos.MensajeResponse responder(ContextoUsuario contexto, Long id,
                                                    SolicitudDtos.MensajeRequest request) {
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id);
        boolean cliente = alcanceService.clienteAutenticado(contexto).isPresent();
        if (cliente && request.interno()) {
            throw new ReglaNegocioException("El cliente no puede crear notas internas");
        }
        LocalDateTime fecha = ahora();
        if (!cliente && !request.interno()) {
            solicitud.registrarRespuesta(fecha);
        } else if (cliente && solicitud.getEstado() == EstadoSolicitud.PENDIENTE_CLIENTE) {
            EstadoSolicitud anterior = solicitud.cambiarEstado(EstadoSolicitud.EN_PROCESO, fecha);
            solicitud.registrarCambio(secuencias.siguiente(SecuenciasPqrs.HISTORIAL), "estado", anterior.name(),
                    solicitud.getEstado().name(), contexto.usuarioId(), "Respuesta del cliente");
        }
        String canal = normalizarCanal(request.canal(), request.interno());
        SolicitudMensaje mensaje = solicitud.agregarMensaje(secuencias.siguiente(SecuenciasPqrs.MENSAJES),
                contexto.usuarioId(), requerido(request.mensaje()), request.interno(), canal);
        repository.guardar(solicitud);
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
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id);
        Long anterior = solicitud.asignar(request.usuarioId());
        solicitud.registrarCambio(secuencias.siguiente(SecuenciasPqrs.HISTORIAL), "asignado_a", texto(anterior),
                texto(request.usuarioId()), contexto.usuarioId(), normalizar(request.motivo()));
        solicitud = repository.guardar(solicitud);
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
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id);
        EstadoSolicitud anterior = solicitud.cambiarEstado(request.estado(), ahora());
        solicitud.registrarCambio(secuencias.siguiente(SecuenciasPqrs.HISTORIAL), "estado", anterior.name(),
                request.estado().name(), contexto.usuarioId(), normalizar(request.motivo()));
        solicitud = repository.guardar(solicitud);
        notificacionService.crear(contexto.empresaId(), usuarioCliente(solicitud.getClienteId()),
                "PQRS_ESTADO", "Estado actualizado " + solicitud.getCodigo(),
                "La solicitud cambió a " + request.estado().name(), "SOLICITUD_PQRS", id, "MEDIA");
        return SolicitudDtos.Response.from(solicitud);
    }

    @Transactional
    public SolicitudDtos.Response cerrar(ContextoUsuario contexto, Long id, String motivo) {
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id);
        EstadoSolicitud anterior = solicitud.cambiarEstado(EstadoSolicitud.CERRADA, ahora());
        solicitud.registrarCambio(secuencias.siguiente(SecuenciasPqrs.HISTORIAL), "estado", anterior.name(),
                EstadoSolicitud.CERRADA.name(), contexto.usuarioId(), normalizar(motivo));
        solicitud = repository.guardar(solicitud);
        notificacionService.crear(contexto.empresaId(), usuarioCliente(solicitud.getClienteId()),
                "PQRS_CERRADA", "Solicitud cerrada " + solicitud.getCodigo(),
                "La atención fue cerrada", "SOLICITUD_PQRS", id, "MEDIA");
        return SolicitudDtos.Response.from(solicitud);
    }

    @Transactional
    public SolicitudDtos.AdjuntoResponse adjuntar(ContextoUsuario contexto, Long id,
                                                   SolicitudDtos.AdjuntoRequest request) {
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id);
        Integer archivos = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM archivos
                WHERE id = ? AND empresa_id = ? AND estado = 'ACTIVO'
                """, Integer.class, request.archivoId(), contexto.empresaId());
        if (archivos == null || archivos == 0) {
            throw new ReglaNegocioException("El archivo no existe, no pertenece a la empresa o no está activo");
        }
        if (request.mensajeId() != null && !solicitud.tieneMensaje(request.mensajeId())) {
            throw new ReglaNegocioException("El mensaje no pertenece a la solicitud");
        }
        if (solicitud.tieneArchivo(request.archivoId())) {
            throw new ConflictoException("El archivo ya está adjunto a la solicitud");
        }
        SolicitudAdjunto adjunto = solicitud.adjuntar(secuencias.siguiente(SecuenciasPqrs.ADJUNTOS),
                request.mensajeId(), request.archivoId());
        repository.guardar(solicitud);
        return adjuntoResponse(adjunto, contexto.empresaId());
    }

    @Transactional
    public SolicitudDtos.AdjuntoResponse subirAdjunto(ContextoUsuario contexto, Long id,
                                                       Long mensajeId, MultipartFile archivo) {
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id);
        if (mensajeId != null && !solicitud.tieneMensaje(mensajeId)) {
            throw new ReglaNegocioException("El mensaje no pertenece a la solicitud");
        }
        DocumentoVentaResponse documento = documentoVentaService
                .guardarAdjuntoSolicitudAutorizada(contexto, solicitud.getProyectoId(), archivo);
        SolicitudAdjunto adjunto = solicitud.adjuntar(secuencias.siguiente(SecuenciasPqrs.ADJUNTOS),
                mensajeId, documento.id());
        repository.guardar(solicitud);
        return new SolicitudDtos.AdjuntoResponse(adjunto.getId(), adjunto.getMensajeId(),
                adjunto.getArchivoId(), documento.nombreOriginal(), documento.mimeType(),
                documento.tamanoBytes(), adjunto.getCreadoEn());
    }

    @Transactional(readOnly = true)
    public List<SolicitudDtos.AdjuntoResponse> adjuntos(ContextoUsuario contexto, Long id) {
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id);
        return solicitud.getAdjuntos().stream()
                .map(adjunto -> adjuntoResponse(adjunto, contexto.empresaId())).toList();
    }

    @Transactional(readOnly = true)
    public DocumentoVentaService.Descarga descargarAdjunto(ContextoUsuario contexto, Long id, Long archivoId) {
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id);
        if (!solicitud.tieneArchivo(archivoId)) {
            throw new RecursoNoEncontradoException("El archivo no está adjunto a la solicitud");
        }
        return documentoVentaService.descargarEntidadAutorizada(contexto, archivoId);
    }

    @Transactional(readOnly = true)
    public List<SolicitudDtos.HistorialResponse> historial(ContextoUsuario contexto, Long id) {
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id);
        if (alcanceService.clienteAutenticado(contexto).isPresent()) {
            throw new ReglaNegocioException("El historial interno no está disponible en el portal del cliente");
        }
        return solicitud.getHistorial().stream().map(SolicitudDtos.HistorialResponse::from).toList();
    }

    @Transactional
    public void responderEncuesta(ContextoUsuario contexto, Long id, SolicitudDtos.EncuestaRequest request) {
        SolicitudPqrs solicitud = obtenerAutorizada(contexto, id);
        Cliente cliente = alcanceService.clienteAutenticado(contexto)
                .orElseThrow(() -> new ReglaNegocioException("La encuesta debe responderla el cliente"));
        if (!solicitud.estaResuelta()) {
            throw new ReglaNegocioException("La atención debe estar resuelta antes de calificarla");
        }
        if (solicitud.estaCalificada()) {
            throw new ConflictoException("La solicitud ya fue calificada");
        }
        solicitud.calificar(cliente.getId(), request.puntuacion(), normalizar(request.comentario()), ahora());
        repository.guardar(solicitud);
    }

    private SolicitudPqrs obtenerAutorizada(ContextoUsuario contexto, Long id) {
        SolicitudPqrs solicitud = repository.findByIdAndEmpresaId(id, contexto.empresaId())
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
