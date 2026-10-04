package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "tareas")
public class Tarea extends EntidadAuditable {
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "proyecto_id")
    private Long proyectoId;
    @Column(name = "asignado_a", nullable = false)
    private Long asignadoA;
    @Column(name = "creado_por")
    private Long creadoPor;
    @Column(nullable = false, length = 180)
    private String titulo;
    @Column(columnDefinition = "TEXT")
    private String descripcion;
    @Column(nullable = false, length = 40)
    private String tipo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PrioridadTarea prioridad;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTarea estado;
    @Column(name = "fecha_vencimiento")
    private LocalDateTime fechaVencimiento;
    @Column(name = "entidad_tipo", length = 50)
    private String entidadTipo;
    @Column(name = "entidad_id")
    private Long entidadId;
    @Column(name = "regla_origen", length = 100)
    private String reglaOrigen;
    @Column(name = "completada_en")
    private LocalDateTime completadaEn;
    @Version
    @Column(nullable = false)
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

    public void marcarVencida(LocalDateTime ahora) {
        if ((estado == EstadoTarea.PENDIENTE || estado == EstadoTarea.EN_PROCESO)
                && fechaVencimiento != null && fechaVencimiento.isBefore(ahora)) {
            estado = EstadoTarea.VENCIDA;
        }
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
}
