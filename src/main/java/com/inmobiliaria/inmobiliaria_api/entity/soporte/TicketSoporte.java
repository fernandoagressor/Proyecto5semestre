package com.inmobiliaria.inmobiliaria_api.entity.soporte;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.PrioridadServicio;
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
@Table(name = "tickets_soporte")
public class TicketSoporte extends EntidadAuditable {
    private static final Map<EstadoTicket, Set<EstadoTicket>> TRANSICIONES = Map.of(
            EstadoTicket.ABIERTO, EnumSet.of(EstadoTicket.EN_PROCESO, EstadoTicket.RESUELTO),
            EstadoTicket.EN_PROCESO, EnumSet.of(EstadoTicket.EN_ESPERA, EstadoTicket.RESUELTO),
            EstadoTicket.EN_ESPERA, EnumSet.of(EstadoTicket.EN_PROCESO, EstadoTicket.RESUELTO),
            EstadoTicket.RESUELTO, EnumSet.of(EstadoTicket.CERRADO, EstadoTicket.REABIERTO),
            EstadoTicket.CERRADO, EnumSet.of(EstadoTicket.REABIERTO),
            EstadoTicket.REABIERTO, EnumSet.of(EstadoTicket.EN_PROCESO, EstadoTicket.RESUELTO)
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id")
    private Long empresaId;
    @Column(name = "proyecto_id")
    private Long proyectoId;
    @Column(nullable = false, length = 50)
    private String codigo;
    @Column(name = "solicitante_id", nullable = false)
    private Long solicitanteId;
    @Column(name = "asignado_a")
    private Long asignadoA;
    @Column(nullable = false, length = 200)
    private String asunto;
    @Column(nullable = false, length = 60)
    private String categoria;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PrioridadServicio prioridad;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTicket estado;
    @Column(name = "fecha_primera_respuesta")
    private LocalDateTime fechaPrimeraRespuesta;
    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;
    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;
    @Version
    @Column(nullable = false)
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
}
