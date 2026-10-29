package com.inmobiliaria.inmobiliaria_api.entity.soporte;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.PrioridadServicio;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Ticket de soporte técnico guardado en MongoDB (rama 6).
 *
 * <p>Agregado con la conversación ({@code mensajes}), los {@code adjuntos} y el {@code historial}
 * embebidos. Ids numéricos desde {@code contadores}; los migrados conservan el id de MySQL.
 * La concurrencia se controla con {@code version}.</p>
 *
 * <p>{@code empresaId}, {@code proyectoId} y {@code estado} son parte del contrato con
 * {@code ProyectorTicketsSoporte} (tabla espejo {@code lectura_tickets_soporte}).</p>
 */
@Document(collection = TicketSoporte.COLECCION)
public class TicketSoporte {

    public static final String COLECCION = "tickets_soporte";

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Map<EstadoTicket, Set<EstadoTicket>> TRANSICIONES = Map.of(
            EstadoTicket.ABIERTO, EnumSet.of(EstadoTicket.EN_PROCESO, EstadoTicket.RESUELTO),
            EstadoTicket.EN_PROCESO, EnumSet.of(EstadoTicket.EN_ESPERA, EstadoTicket.RESUELTO),
            EstadoTicket.EN_ESPERA, EnumSet.of(EstadoTicket.EN_PROCESO, EstadoTicket.RESUELTO),
            EstadoTicket.RESUELTO, EnumSet.of(EstadoTicket.CERRADO, EstadoTicket.REABIERTO),
            EstadoTicket.CERRADO, EnumSet.of(EstadoTicket.REABIERTO),
            EstadoTicket.REABIERTO, EnumSet.of(EstadoTicket.EN_PROCESO, EstadoTicket.RESUELTO)
    );
    private static final Comparator<TicketMensaje> ORDEN_MENSAJES = Comparator
            .comparing(TicketMensaje::getCreadoEn, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparing(TicketMensaje::getId, Comparator.nullsFirst(Comparator.<Long>naturalOrder()));
    private static final Comparator<TicketAdjunto> ORDEN_ADJUNTOS = Comparator
            .comparing(TicketAdjunto::getCreadoEn, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparing(TicketAdjunto::getId, Comparator.nullsFirst(Comparator.<Long>naturalOrder()));
    private static final Comparator<TicketHistorial> ORDEN_HISTORIAL = Comparator
            .comparing(TicketHistorial::getCreadoEn, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparing(TicketHistorial::getId, Comparator.nullsFirst(Comparator.<Long>naturalOrder()));

    @Id
    private Long id;
    private Long empresaId;
    private Long proyectoId;
    private String codigo;
    private Long solicitanteId;
    private Long asignadoA;
    private String asunto;
    private String categoria;
    private PrioridadServicio prioridad;
    private String descripcion;
    private EstadoTicket estado;
    private LocalDateTime fechaPrimeraRespuesta;
    private LocalDateTime fechaResolucion;
    private LocalDateTime fechaCierre;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private List<TicketMensaje> mensajes = new ArrayList<>();
    private List<TicketAdjunto> adjuntos = new ArrayList<>();
    private List<TicketHistorial> historial = new ArrayList<>();

    @Version
    private Long version;

    protected TicketSoporte() { }

    public TicketSoporte(Long empresaId, Long proyectoId, String codigo, Long solicitanteId,
                         String asunto, String categoria, PrioridadServicio prioridad,
                         String descripcion) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.codigo = codigo;
        this.solicitanteId = solicitanteId;
        this.asunto = asunto;
        this.categoria = categoria;
        this.prioridad = prioridad;
        this.descripcion = descripcion;
        this.estado = EstadoTicket.ABIERTO;
        this.creadoEn = ahora();
        this.actualizadoEn = creadoEn;
    }

    /** Asigna el identificador de un ticket nuevo. Solo puede hacerse una vez. */
    public void identificar(long nuevoId) {
        if (id != null) {
            throw new IllegalStateException("El ticket ya tiene identificador " + id);
        }
        id = nuevoId;
    }

    /** Marca la fecha de la última modificación; lo llama el repositorio antes de guardar. */
    public void tocar() {
        actualizadoEn = ahora();
    }

    public Long asignar(Long usuarioId) {
        Long anterior = asignadoA;
        asignadoA = usuarioId;
        if (estado == EstadoTicket.ABIERTO || estado == EstadoTicket.REABIERTO) {
            estado = EstadoTicket.EN_PROCESO;
        }
        return anterior;
    }

    public void registrarRespuesta(LocalDateTime fecha) {
        if (estado == EstadoTicket.CERRADO) {
            throw new ReglaNegocioException("El ticket cerrado no admite respuestas");
        }
        if (fechaPrimeraRespuesta == null) {
            fechaPrimeraRespuesta = fecha;
        }
        if (estado == EstadoTicket.ABIERTO || estado == EstadoTicket.REABIERTO) {
            estado = EstadoTicket.EN_PROCESO;
        }
    }

    public EstadoTicket cambiarEstado(EstadoTicket nuevo, LocalDateTime fecha) {
        if (nuevo == null || nuevo == estado || !TRANSICIONES.getOrDefault(estado, Set.of()).contains(nuevo)) {
            throw new ReglaNegocioException("La transición del ticket no está permitida");
        }
        EstadoTicket anterior = estado;
        estado = nuevo;
        if (nuevo == EstadoTicket.RESUELTO) {
            fechaResolucion = fecha;
        } else if (nuevo == EstadoTicket.CERRADO) {
            if (fechaResolucion == null) {
                throw new ReglaNegocioException("El ticket debe resolverse antes de cerrarse");
            }
            fechaCierre = fecha;
        } else if (nuevo == EstadoTicket.REABIERTO) {
            fechaResolucion = null;
            fechaCierre = null;
        }
        return anterior;
    }

    public TicketMensaje agregarMensaje(long mensajeId, Long usuarioId, String texto, boolean interno) {
        TicketMensaje mensaje = new TicketMensaje(mensajeId, usuarioId, texto, interno, ahora());
        mensajes.add(mensaje);
        return mensaje;
    }

    /** Conversación en orden cronológico; el solicitante no ve las notas internas. */
    public List<TicketMensaje> mensajes(boolean incluirInternos) {
        return mensajes.stream()
                .filter(mensaje -> incluirInternos || !mensaje.isInterno())
                .sorted(ORDEN_MENSAJES)
                .toList();
    }

    public boolean tieneMensaje(Long mensajeId) {
        return mensajeId != null && mensajes.stream().anyMatch(mensaje -> mensajeId.equals(mensaje.getId()));
    }

    public boolean tieneArchivo(Long archivoId) {
        return archivoId != null && adjuntos.stream().anyMatch(adjunto -> archivoId.equals(adjunto.getArchivoId()));
    }

    public TicketAdjunto adjuntar(long adjuntoId, Long mensajeId, Long archivoId) {
        Objects.requireNonNull(archivoId, "archivoId");
        if (mensajeId != null && !tieneMensaje(mensajeId)) {
            throw new ReglaNegocioException("El mensaje no pertenece al ticket");
        }
        if (tieneArchivo(archivoId)) {
            throw new ReglaNegocioException("El archivo ya está adjunto al ticket");
        }
        TicketAdjunto adjunto = new TicketAdjunto(adjuntoId, mensajeId, archivoId, ahora());
        adjuntos.add(adjunto);
        return adjunto;
    }

    public TicketHistorial registrarCambio(long historialId, String campo, String valorAnterior,
                                           String valorNuevo, Long usuarioId, String motivo) {
        TicketHistorial cambio = new TicketHistorial(historialId, campo, valorAnterior, valorNuevo,
                usuarioId, motivo, ahora());
        historial.add(cambio);
        return cambio;
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public String getCodigo() { return codigo; }
    public Long getSolicitanteId() { return solicitanteId; }
    public Long getAsignadoA() { return asignadoA; }
    public String getAsunto() { return asunto; }
    public String getCategoria() { return categoria; }
    public PrioridadServicio getPrioridad() { return prioridad; }
    public String getDescripcion() { return descripcion; }
    public EstadoTicket getEstado() { return estado; }
    public LocalDateTime getFechaPrimeraRespuesta() { return fechaPrimeraRespuesta; }
    public LocalDateTime getFechaResolucion() { return fechaResolucion; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }

    public List<TicketAdjunto> getAdjuntos() {
        return adjuntos.stream().sorted(ORDEN_ADJUNTOS).toList();
    }

    public List<TicketHistorial> getHistorial() {
        return historial.stream().sorted(ORDEN_HISTORIAL).toList();
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(BOGOTA);
    }
}
