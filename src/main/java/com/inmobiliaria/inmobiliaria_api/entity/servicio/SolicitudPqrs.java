package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
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
 * Solicitud PQRS (petición, queja, reclamo, sugerencia o solicitud) guardada en MongoDB.
 *
 * <p>Es un agregado: la conversación ({@code mensajes}), los {@code adjuntos}, el
 * {@code historial} de cambios y la {@code encuesta} de satisfacción viven embebidos en el
 * mismo documento, así cada operación se guarda con una sola escritura atómica.</p>
 *
 * <p>Los identificadores son numéricos y salen de la colección {@code contadores}; los
 * documentos migrados conservan el id que tenían en MySQL. La concurrencia se controla con
 * {@code version}: si dos agentes guardan la misma solicitud, el segundo recibe un conflicto.</p>
 *
 * <p>Los nombres de los campos son parte del contrato con {@code ProyectorSolicitudesPqrs}
 * (tabla espejo {@code lectura_solicitudes_pqrs}): no se deben renombrar sin cambiar el proyector.</p>
 */
@Document(collection = SolicitudPqrs.COLECCION)
public class SolicitudPqrs {

    public static final String COLECCION = "solicitudes_pqrs";

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Map<EstadoSolicitud, Set<EstadoSolicitud>> TRANSICIONES = Map.of(
            EstadoSolicitud.ABIERTA, EnumSet.of(EstadoSolicitud.EN_PROCESO, EstadoSolicitud.CANCELADA),
            EstadoSolicitud.EN_PROCESO, EnumSet.of(EstadoSolicitud.PENDIENTE_CLIENTE, EstadoSolicitud.PENDIENTE_TERCERO, EstadoSolicitud.RESUELTA, EstadoSolicitud.CANCELADA),
            EstadoSolicitud.PENDIENTE_CLIENTE, EnumSet.of(EstadoSolicitud.EN_PROCESO, EstadoSolicitud.RESUELTA, EstadoSolicitud.CANCELADA),
            EstadoSolicitud.PENDIENTE_TERCERO, EnumSet.of(EstadoSolicitud.EN_PROCESO, EstadoSolicitud.RESUELTA, EstadoSolicitud.CANCELADA),
            EstadoSolicitud.RESUELTA, EnumSet.of(EstadoSolicitud.CERRADA, EstadoSolicitud.REABIERTA),
            EstadoSolicitud.CERRADA, EnumSet.of(EstadoSolicitud.REABIERTA),
            EstadoSolicitud.REABIERTA, EnumSet.of(EstadoSolicitud.EN_PROCESO, EstadoSolicitud.RESUELTA, EstadoSolicitud.CANCELADA),
            EstadoSolicitud.CANCELADA, EnumSet.noneOf(EstadoSolicitud.class)
    );

    private static final Comparator<SolicitudMensaje> ORDEN_MENSAJES = Comparator
            .comparing(SolicitudMensaje::getCreadoEn, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparing(SolicitudMensaje::getId, Comparator.nullsFirst(Comparator.<Long>naturalOrder()));
    private static final Comparator<SolicitudAdjunto> ORDEN_ADJUNTOS = Comparator
            .comparing(SolicitudAdjunto::getCreadoEn, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparing(SolicitudAdjunto::getId, Comparator.nullsFirst(Comparator.<Long>naturalOrder()));
    private static final Comparator<SolicitudHistorial> ORDEN_HISTORIAL = Comparator
            .comparing(SolicitudHistorial::getCreadoEn, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparing(SolicitudHistorial::getId, Comparator.nullsFirst(Comparator.<Long>naturalOrder()));

    @Id
    private Long id;
    private Long empresaId;
    private Long proyectoId;
    private String codigo;
    private Long clienteId;
    private Long contratoId;
    private Long asignadoA;
    private TipoSolicitud tipo;
    private String categoria;
    private String asunto;
    private String descripcion;
    private PrioridadServicio prioridad;
    private EstadoSolicitud estado;
    private LocalDateTime fechaLimite;
    private LocalDateTime fechaPrimeraRespuesta;
    private LocalDateTime fechaResolucion;
    private LocalDateTime fechaCierre;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private List<SolicitudMensaje> mensajes = new ArrayList<>();
    private List<SolicitudAdjunto> adjuntos = new ArrayList<>();
    private List<SolicitudHistorial> historial = new ArrayList<>();
    private EncuestaSatisfaccion encuesta;

    @Version
    private Long version;

    protected SolicitudPqrs() {
    }

    public SolicitudPqrs(Long empresaId, Long proyectoId, String codigo, Long clienteId,
                         Long contratoId, TipoSolicitud tipo, String categoria, String asunto,
                         String descripcion, PrioridadServicio prioridad, LocalDateTime fechaLimite) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.codigo = codigo;
        this.clienteId = clienteId;
        this.contratoId = contratoId;
        this.tipo = tipo;
        this.categoria = categoria;
        this.asunto = asunto;
        this.descripcion = descripcion;
        this.prioridad = prioridad;
        this.fechaLimite = fechaLimite;
        this.estado = EstadoSolicitud.ABIERTA;
        this.creadoEn = ahora();
        this.actualizadoEn = creadoEn;
    }

    /** Asigna el identificador de una solicitud nueva. Solo puede hacerse una vez. */
    public void identificar(long nuevoId) {
        if (id != null) {
            throw new IllegalStateException("La solicitud ya tiene identificador " + id);
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
        if (estado == EstadoSolicitud.ABIERTA) {
            estado = EstadoSolicitud.EN_PROCESO;
        }
        return anterior;
    }

    public void registrarRespuesta(LocalDateTime fecha) {
        if (estado == EstadoSolicitud.CERRADA || estado == EstadoSolicitud.CANCELADA) {
            throw new ReglaNegocioException("La solicitud cerrada o cancelada no admite respuestas");
        }
        if (fechaPrimeraRespuesta == null) {
            fechaPrimeraRespuesta = fecha;
        }
        if (estado == EstadoSolicitud.ABIERTA || estado == EstadoSolicitud.REABIERTA) {
            estado = EstadoSolicitud.EN_PROCESO;
        }
    }

    public EstadoSolicitud cambiarEstado(EstadoSolicitud nuevo, LocalDateTime fecha) {
        if (nuevo == null || nuevo == estado) {
            throw new ReglaNegocioException("El nuevo estado debe ser diferente del actual");
        }
        if (!TRANSICIONES.getOrDefault(estado, Set.of()).contains(nuevo)) {
            throw new ReglaNegocioException("La transición de estado solicitada no está permitida");
        }
        EstadoSolicitud anterior = estado;
        estado = nuevo;
        if (nuevo == EstadoSolicitud.RESUELTA) {
            fechaResolucion = fecha;
        } else if (nuevo == EstadoSolicitud.CERRADA) {
            if (fechaResolucion == null) {
                throw new ReglaNegocioException("La solicitud debe resolverse antes de cerrarse");
            }
            fechaCierre = fecha;
        } else if (nuevo == EstadoSolicitud.REABIERTA) {
            fechaResolucion = null;
            fechaCierre = null;
        }
        return anterior;
    }

    public SolicitudMensaje agregarMensaje(long mensajeId, Long usuarioId, String texto,
                                           boolean interno, String canal) {
        SolicitudMensaje mensaje = new SolicitudMensaje(mensajeId, usuarioId, texto, interno, canal, ahora());
        mensajes.add(mensaje);
        return mensaje;
    }

    /** Conversación en orden cronológico; el cliente no ve las notas internas. */
    public List<SolicitudMensaje> mensajes(boolean incluirInternos) {
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

    public SolicitudAdjunto adjuntar(long adjuntoId, Long mensajeId, Long archivoId) {
        Objects.requireNonNull(archivoId, "archivoId");
        if (mensajeId != null && !tieneMensaje(mensajeId)) {
            throw new ReglaNegocioException("El mensaje no pertenece a la solicitud");
        }
        if (tieneArchivo(archivoId)) {
            throw new ReglaNegocioException("El archivo ya está adjunto a la solicitud");
        }
        SolicitudAdjunto adjunto = new SolicitudAdjunto(adjuntoId, mensajeId, archivoId, ahora());
        adjuntos.add(adjunto);
        return adjunto;
    }

    public SolicitudHistorial registrarCambio(long historialId, String campo, String valorAnterior,
                                              String valorNuevo, Long usuarioId, String motivo) {
        SolicitudHistorial cambio = new SolicitudHistorial(historialId, campo, valorAnterior,
                valorNuevo, usuarioId, motivo, ahora());
        historial.add(cambio);
        return cambio;
    }

    public boolean estaResuelta() {
        return estado == EstadoSolicitud.RESUELTA || estado == EstadoSolicitud.CERRADA;
    }

    public boolean estaCalificada() {
        return encuesta != null;
    }

    public void calificar(Long clienteIdEncuesta, int puntuacion, String comentario, LocalDateTime fecha) {
        if (!estaResuelta()) {
            throw new ReglaNegocioException("La atención debe estar resuelta antes de calificarla");
        }
        if (estaCalificada()) {
            throw new ReglaNegocioException("La solicitud ya fue calificada");
        }
        encuesta = new EncuestaSatisfaccion(clienteIdEncuesta, puntuacion, comentario, fecha);
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public String getCodigo() { return codigo; }
    public Long getClienteId() { return clienteId; }
    public Long getContratoId() { return contratoId; }
    public Long getAsignadoA() { return asignadoA; }
    public TipoSolicitud getTipo() { return tipo; }
    public String getCategoria() { return categoria; }
    public String getAsunto() { return asunto; }
    public String getDescripcion() { return descripcion; }
    public PrioridadServicio getPrioridad() { return prioridad; }
    public EstadoSolicitud getEstado() { return estado; }
    public LocalDateTime getFechaLimite() { return fechaLimite; }
    public LocalDateTime getFechaPrimeraRespuesta() { return fechaPrimeraRespuesta; }
    public LocalDateTime getFechaResolucion() { return fechaResolucion; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public EncuestaSatisfaccion getEncuesta() { return encuesta; }
    public Long getVersion() { return version; }

    public List<SolicitudAdjunto> getAdjuntos() {
        return adjuntos.stream()
                .sorted(ORDEN_ADJUNTOS)
                .toList();
    }

    public List<SolicitudHistorial> getHistorial() {
        return historial.stream()
                .sorted(ORDEN_HISTORIAL)
                .toList();
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(BOGOTA);
    }
}
