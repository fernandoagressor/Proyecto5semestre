package com.inmobiliaria.inmobiliaria_api.entity.servicio;

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
@Table(name = "solicitudes_pqrs")
public class SolicitudPqrs extends EntidadAuditable {
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "proyecto_id")
    private Long proyectoId;

    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "contrato_id")
    private Long contratoId;

    @Column(name = "asignado_a")
    private Long asignadoA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoSolicitud tipo;

    @Column(nullable = false, length = 60)
    private String categoria;

    @Column(nullable = false, length = 200)
    private String asunto;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PrioridadServicio prioridad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoSolicitud estado;

    @Column(name = "fecha_limite")
    private LocalDateTime fechaLimite;

    @Column(name = "fecha_primera_respuesta")
    private LocalDateTime fechaPrimeraRespuesta;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Version
    @Column(nullable = false)
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
    public Long getVersion() { return version; }
}
