package com.inmobiliaria.inmobiliaria_api.service.soporte;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.soporte.SoporteDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.PrioridadServicio;
import com.inmobiliaria.inmobiliaria_api.entity.soporte.EstadoTicket;
import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketMensaje;
import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketSoporte;
import com.inmobiliaria.inmobiliaria_api.repository.soporte.TicketSoporteRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import com.inmobiliaria.inmobiliaria_api.service.notificacion.NotificacionService;
import com.inmobiliaria.inmobiliaria_api.service.servicio.ServicioClienteAlcanceService;
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

/**
 * Tickets de soporte técnico. Desde la rama 6 viven en MongoDB (colección
 * {@code tickets_soporte}) con su conversación, adjuntos e historial embebidos.
 *
 * <p>Cada operación lee el documento, aplica la regla y lo guarda con una sola escritura; si
 * otro agente lo modificó entretanto, la versión no coincide y se responde 409. Las consultas de
 * alcance y de archivos siguen en MySQL dentro de la transacción JPA de cada método.</p>
 */
@Service
public class TicketSoporteService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyyMMdd");
    static final String SECUENCIA_TICKETS = "tickets_soporte";
    static final String SECUENCIA_MENSAJES = "ticket_mensajes";
    static final String SECUENCIA_ADJUNTOS = "ticket_adjuntos";
    static final String SECUENCIA_HISTORIAL = "ticket_historial";

    private final TicketSoporteRepository repository;
    private final SecuenciaService secuencias;
    private final ServicioClienteAlcanceService alcanceService;
    private final NotificacionService notificacionService;
    private final JdbcTemplate jdbcTemplate;

    public TicketSoporteService(TicketSoporteRepository repository,
                                SecuenciaService secuencias,
                                ServicioClienteAlcanceService alcanceService,
                                NotificacionService notificacionService,
                                JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.secuencias = secuencias;
        this.alcanceService = alcanceService;
        this.notificacionService = notificacionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<SoporteDtos.Response> listar(ContextoUsuario contexto, EstadoTicket estado,
                                                       Long asignadoA, int pagina, int tamano) {
        boolean cliente = alcanceService.clienteAutenticado(contexto).isPresent();
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(where("empresaId").is(contexto.empresaId()));
        if (cliente) {
            condiciones.add(where("solicitanteId").is(contexto.usuarioId()));
        } else {
            AlcanceProyectos alcance = alcanceService.alcanceInterno(contexto);
            if (!alcance.empresaCompleta()) {
                List<Criteria> permitidos = new ArrayList<>();
                if (!alcance.proyectos().isEmpty()) permitidos.add(where("proyectoId").in(alcance.proyectos()));
                if (alcance.registrosPropios()) {
                    permitidos.add(where("solicitanteId").is(contexto.usuarioId()));
                    permitidos.add(where("asignadoA").is(contexto.usuarioId()));
                }
                condiciones.add(permitidos.isEmpty()
                        ? where("id").in(List.of())
                        : new Criteria().orOperator(permitidos));
            }
        }
        if (estado != null) condiciones.add(where("estado").is(estado.name()));
        if (asignadoA != null) condiciones.add(where("asignadoA").is(asignadoA));
        Page<TicketSoporte> resultado = repository.buscarResumen(
                new Criteria().andOperator(condiciones),
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                        Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id"))));
        return PaginaResponse.from(resultado, SoporteDtos.Response::from);
    }

    @Transactional
    public SoporteDtos.Response crear(ContextoUsuario contexto, SoporteDtos.CrearRequest request) {
        var cliente = alcanceService.clienteAutenticado(contexto).orElse(null);
        if (cliente != null) {
            if (request.proyectoId() != null) {
                alcanceService.validarRelacion(contexto, cliente.getId(), request.proyectoId(), null);
            }
        } else {
            AlcanceProyectos alcance = alcanceService.alcanceInterno(contexto);
            boolean proyectoPermitido = request.proyectoId() != null
                    && alcance.proyectos().contains(request.proyectoId());
            if (!alcance.empresaCompleta() && !alcance.registrosPropios() && !proyectoPermitido) {
                throw new AccesoDenegadoException("El usuario no tiene alcance para crear el ticket");
            }
        }
        if (request.proyectoId() != null) {
            Integer proyectos = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM proyectos WHERE id = ? AND empresa_id = ?",
                    Integer.class, request.proyectoId(), contexto.empresaId());
            if (proyectos == null || proyectos == 0) throw new ReglaNegocioException("El proyecto no existe en la empresa");
        }
        LocalDateTime ahora = ahora();
        TicketSoporte ticket = new TicketSoporte(contexto.empresaId(), request.proyectoId(), codigo(ahora),
                contexto.usuarioId(), requerido(request.asunto()), requerido(request.categoria()),
                request.prioridad() == null ? PrioridadServicio.MEDIA : request.prioridad(),
                requerido(request.descripcion()));
        ticket.identificar(secuencias.siguiente(SECUENCIA_TICKETS));
        ticket.agregarMensaje(secuencias.siguiente(SECUENCIA_MENSAJES), contexto.usuarioId(), ticket.getDescripcion(), false);
        try { ticket = repository.insertar(ticket); }
        catch (DataIntegrityViolationException ex) { throw new ConflictoException("No fue posible asignar un código único al ticket"); }
        return SoporteDtos.Response.from(ticket);
    }

    @Transactional(readOnly = true)
    public SoporteDtos.Response obtener(ContextoUsuario contexto, Long id) {
        return SoporteDtos.Response.from(autorizado(contexto, id));
    }

    @Transactional(readOnly = true)
    public List<SoporteDtos.MensajeResponse> mensajes(ContextoUsuario contexto, Long id) {
        TicketSoporte ticket = autorizado(contexto, id);
        boolean cliente = alcanceService.clienteAutenticado(contexto).isPresent();
        return ticket.mensajes(!cliente).stream().map(SoporteDtos.MensajeResponse::from).toList();
    }

    @Transactional
    public SoporteDtos.MensajeResponse responder(ContextoUsuario contexto, Long id, SoporteDtos.MensajeRequest request) {
        TicketSoporte ticket = autorizado(contexto, id);
        boolean cliente = alcanceService.clienteAutenticado(contexto).isPresent();
        if (cliente && request.interno()) throw new AccesoDenegadoException("El cliente no puede crear notas internas");
        if (!cliente && !request.interno()) ticket.registrarRespuesta(ahora());
        TicketMensaje mensaje = ticket.agregarMensaje(secuencias.siguiente(SECUENCIA_MENSAJES), contexto.usuarioId(),
                requerido(request.mensaje()), request.interno());
        repository.guardar(ticket);
        Long destino = cliente ? ticket.getAsignadoA() : ticket.getSolicitanteId();
        if (!request.interno()) notificacionService.crear(contexto.empresaId(), destino, "SOPORTE_RESPUESTA",
                "Nueva respuesta en " + ticket.getCodigo(), ticket.getAsunto(), "TICKET_SOPORTE", id, "MEDIA");
        return SoporteDtos.MensajeResponse.from(mensaje);
    }

    @Transactional
    public SoporteDtos.Response asignar(ContextoUsuario contexto, Long id, SoporteDtos.AsignarRequest request) {
        alcanceService.validarUsuarioEmpresa(contexto, request.usuarioId());
        TicketSoporte ticket = autorizado(contexto, id);
        Long anterior = ticket.asignar(request.usuarioId());
        ticket.registrarCambio(secuencias.siguiente(SECUENCIA_HISTORIAL), "asignado_a", texto(anterior),
                texto(request.usuarioId()), contexto.usuarioId(), normalizar(request.motivo()));
        ticket = repository.guardar(ticket);
        notificacionService.crear(contexto.empresaId(), request.usuarioId(), "SOPORTE_ASIGNADO",
                "Ticket asignado " + ticket.getCodigo(), ticket.getAsunto(), "TICKET_SOPORTE", id, "ALTA");
        return SoporteDtos.Response.from(ticket);
    }

    @Transactional
    public SoporteDtos.Response cambiarEstado(ContextoUsuario contexto, Long id, SoporteDtos.EstadoRequest request) {
        TicketSoporte ticket = autorizado(contexto, id);
        EstadoTicket anterior = ticket.cambiarEstado(request.estado(), ahora());
        ticket.registrarCambio(secuencias.siguiente(SECUENCIA_HISTORIAL), "estado", anterior.name(),
                request.estado().name(), contexto.usuarioId(), normalizar(request.motivo()));
        ticket = repository.guardar(ticket);
        notificacionService.crear(contexto.empresaId(), ticket.getSolicitanteId(), "SOPORTE_ESTADO",
                "Estado actualizado " + ticket.getCodigo(), "El ticket cambió a " + request.estado().name(),
                "TICKET_SOPORTE", id, "MEDIA");
        return SoporteDtos.Response.from(ticket);
    }

    @Transactional
    public SoporteDtos.AdjuntoResponse adjuntar(ContextoUsuario contexto, Long id, SoporteDtos.AdjuntoRequest request) {
        TicketSoporte ticket = autorizado(contexto, id);
        Integer archivos = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM archivos WHERE id = ? AND empresa_id = ? AND estado = 'ACTIVO'",
                Integer.class, request.archivoId(), contexto.empresaId());
        if (archivos == null || archivos == 0) throw new ReglaNegocioException("El archivo no existe o no está activo");
        if (request.mensajeId() != null && !ticket.tieneMensaje(request.mensajeId())) {
            throw new ReglaNegocioException("El mensaje no pertenece al ticket");
        }
        if (ticket.tieneArchivo(request.archivoId())) {
            throw new ConflictoException("El archivo ya está adjunto al ticket");
        }
        var adjunto = ticket.adjuntar(secuencias.siguiente(SECUENCIA_ADJUNTOS), request.mensajeId(), request.archivoId());
        repository.guardar(ticket);
        return SoporteDtos.AdjuntoResponse.from(adjunto);
    }

    @Transactional(readOnly = true)
    public List<SoporteDtos.AdjuntoResponse> adjuntos(ContextoUsuario contexto, Long id) {
        return autorizado(contexto, id).getAdjuntos().stream().map(SoporteDtos.AdjuntoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SoporteDtos.HistorialResponse> historial(ContextoUsuario contexto, Long id) {
        TicketSoporte ticket = autorizado(contexto, id);
        if (alcanceService.clienteAutenticado(contexto).isPresent()) {
            throw new AccesoDenegadoException("El historial interno no está disponible en el portal");
        }
        return ticket.getHistorial().stream().map(SoporteDtos.HistorialResponse::from).toList();
    }

    private TicketSoporte autorizado(ContextoUsuario contexto, Long id) {
        TicketSoporte ticket = repository.findByIdAndEmpresaId(id, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El ticket no existe"));
        if (alcanceService.clienteAutenticado(contexto).isPresent()
                && !contexto.usuarioId().equals(ticket.getSolicitanteId())) {
            throw new AccesoDenegadoException("El ticket no pertenece al usuario autenticado");
        }
        if (alcanceService.clienteAutenticado(contexto).isEmpty()) {
            AlcanceProyectos alcance = alcanceService.alcanceInterno(contexto);
            boolean permitido = alcance.empresaCompleta()
                    || (ticket.getProyectoId() != null && alcance.proyectos().contains(ticket.getProyectoId()))
                    || (alcance.registrosPropios() && (contexto.usuarioId().equals(ticket.getSolicitanteId())
                    || contexto.usuarioId().equals(ticket.getAsignadoA())));
            if (!permitido) throw new AccesoDenegadoException("El usuario no tiene alcance sobre el ticket");
        }
        return ticket;
    }

    private static String codigo(LocalDateTime fecha) {
        return "SUP-" + DIA.format(fecha) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
    private static String requerido(String valor) { String n = normalizar(valor); if (n == null) throw new ReglaNegocioException("El valor es obligatorio"); return n; }
    private static String normalizar(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
    private static String texto(Object valor) { return valor == null ? null : valor.toString(); }
    private static LocalDateTime ahora() { return LocalDateTime.now(BOGOTA); }
}
