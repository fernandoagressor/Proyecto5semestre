package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Tarea manual o automática, guardada en MongoDB (rama 9) con su historial embebido.
 * La tabla espejo {@code lectura_tareas} alimenta el dashboard gerencial.
 */
@Document(collection = Tarea.COLECCION)
public class Tarea {
    public static final String COLECCION = "tareas";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private static final Map<EstadoTarea, Set<EstadoTarea>> TRANSICIONES = Map.of(
            EstadoTarea.PENDIENTE, EnumSet.of(EstadoTarea.EN_PROCESO, EstadoTarea.COMPLETADA,
                    EstadoTarea.VENCIDA, EstadoTarea.CANCELADA),
            EstadoTarea.EN_PROCESO, EnumSet.of(EstadoTarea.PENDIENTE, EstadoTarea.COMPLETADA,
                    EstadoTarea.VENCIDA, EstadoTarea.CANCELADA),
            EstadoTarea.VENCIDA, EnumSet.of(EstadoTarea.EN_PROCESO, EstadoTarea.COMPLETADA,
                    EstadoTarea.CANCELADA),
            EstadoTarea.COMPLETADA, EnumSet.noneOf(EstadoTarea.class),
            EstadoTarea.CANCELADA, EnumSet.noneOf(EstadoTarea.class)
    );

    @Id
    private Long id;
    private Long empresaId;
    private Long proyectoId;
    private Long asignadoA;
    private Long creadoPor;
    private String titulo;
    private String descripcion;
    private String tipo;
    private PrioridadTarea prioridad;
    private EstadoTarea estado;
    private LocalDateTime fechaVencimiento;
    private String entidadTipo;
    private Long entidadId;
    private String reglaOrigen;
    private LocalDateTime completadaEn;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private List<TareaHistorial> historial = new ArrayList<>();
    @Version
    private Long version;

    protected Tarea() { }

    public Tarea(Long empresaId, Long proyectoId, Long asignadoA, Long creadoPor,
                 String titulo, String descripcion, String tipo, PrioridadTarea prioridad,
                 LocalDateTime fechaVencimiento, String entidadTipo, Long entidadId,
                 String reglaOrigen) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.asignadoA = asignadoA;
        this.creadoPor = creadoPor;
        this.titulo = requerido(titulo, "El título de la tarea es obligatorio");
        this.descripcion = normalizar(descripcion);
        this.tipo = requerido(tipo, "El tipo de tarea es obligatorio");
        this.prioridad = prioridad == null ? PrioridadTarea.MEDIA : prioridad;
        this.fechaVencimiento = fechaVencimiento;
        this.entidadTipo = normalizar(entidadTipo);
        this.entidadId = entidadId;
        this.reglaOrigen = normalizar(reglaOrigen);
        this.estado = EstadoTarea.PENDIENTE;
        this.creadoEn = LocalDateTime.now(BOGOTA);
        this.actualizadoEn = creadoEn;
        if ((this.entidadTipo == null) != (entidadId == null)) {
            throw new ReglaNegocioException("El tipo y el id de la entidad relacionada deben enviarse juntos");
        }
    }

    public Long reasignar(Long usuarioId) {
        if (terminal()) throw new ReglaNegocioException("La tarea finalizada no puede reasignarse");
        if (usuarioId == null || usuarioId.equals(asignadoA)) {
            throw new ReglaNegocioException("Seleccione un responsable diferente");
        }
        Long anterior = asignadoA;
        asignadoA = usuarioId;
        return anterior;
    }

    public EstadoTarea cambiarEstado(EstadoTarea nuevo, LocalDateTime fecha) {
        if (nuevo == null || nuevo == estado) {
            throw new ReglaNegocioException("El nuevo estado debe ser diferente del actual");
        }
        if (!TRANSICIONES.getOrDefault(estado, Set.of()).contains(nuevo)) {
            throw new ReglaNegocioException("La transición de la tarea no está permitida");
        }
        EstadoTarea anterior = estado;
        estado = nuevo;
        completadaEn = nuevo == EstadoTarea.COMPLETADA ? fecha : null;
        return anterior;
    }

    /** @return si la tarea pasó a VENCIDA */
    public boolean marcarVencida(LocalDateTime ahora) {
        if ((estado == EstadoTarea.PENDIENTE || estado == EstadoTarea.EN_PROCESO)
                && fechaVencimiento != null && fechaVencimiento.isBefore(ahora)) {
            estado = EstadoTarea.VENCIDA;
            return true;
        }
        return false;
    }

    /** Asigna el identificador de una tarea nueva. Solo puede hacerse una vez. */
    public void identificar(long nuevoId) {
        if (id != null) {
            throw new IllegalStateException("La tarea ya tiene identificador " + id);
        }
        id = nuevoId;
    }

    /** Agrega un registro al historial embebido. */
    public TareaHistorial registrarHistorial(long historialId, String estadoAnterior, String estadoNuevo,
                                             Long usuarioId, String comentario, LocalDateTime fecha) {
        TareaHistorial registro = new TareaHistorial(historialId, id, estadoAnterior, estadoNuevo, usuarioId,
                comentario, fecha);
        historial.add(registro);
        return registro;
    }

    /** Marca la fecha de la última modificación; lo llama el repositorio antes de guardar. */
    public void tocar() {
        actualizadoEn = LocalDateTime.now(BOGOTA);
    }

    private boolean terminal() {
        return estado == EstadoTarea.COMPLETADA || estado == EstadoTarea.CANCELADA;
    }

    private static String requerido(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) throw new ReglaNegocioException(mensaje);
        return valor.trim();
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public Long getAsignadoA() { return asignadoA; }
    public Long getCreadoPor() { return creadoPor; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public String getTipo() { return tipo; }
    public PrioridadTarea getPrioridad() { return prioridad; }
    public EstadoTarea getEstado() { return estado; }
    public LocalDateTime getFechaVencimiento() { return fechaVencimiento; }
    public String getEntidadTipo() { return entidadTipo; }
    public Long getEntidadId() { return entidadId; }
    public String getReglaOrigen() { return reglaOrigen; }
    public LocalDateTime getCompletadaEn() { return completadaEn; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public List<TareaHistorial> getHistorial() { return Collections.unmodifiableList(historial); }
}
