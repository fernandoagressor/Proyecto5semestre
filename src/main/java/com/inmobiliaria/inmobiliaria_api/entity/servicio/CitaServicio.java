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

@Entity
@Table(name = "citas_servicio")
public class CitaServicio extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "proyecto_id")
    private Long proyectoId;
    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;
    @Column(name = "solicitud_id")
    private Long solicitudId;
    @Column(name = "responsable_id")
    private Long responsableId;
    @Column(nullable = false, length = 30)
    private String tipo;
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;
    @Column(name = "fecha_fin", nullable = false)
    private LocalDateTime fechaFin;
    @Column(length = 300)
    private String lugar;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCitaServicio estado;
    @Column(columnDefinition = "TEXT")
    private String resultado;
    @Version
    @Column(nullable = false)
    private Long version;

    protected CitaServicio() { }

    public CitaServicio(Long empresaId, Long proyectoId, Long clienteId, Long solicitudId,
                        Long responsableId, String tipo, LocalDateTime inicio,
                        LocalDateTime fin, String lugar) {
        validarFechas(inicio, fin);
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.clienteId = clienteId;
        this.solicitudId = solicitudId;
        this.responsableId = responsableId;
        this.tipo = tipo;
        this.fechaInicio = inicio;
        this.fechaFin = fin;
        this.lugar = lugar;
        this.estado = EstadoCitaServicio.PROGRAMADA;
    }

    public void reprogramar(LocalDateTime inicio, LocalDateTime fin, String lugar) {
        if (estado == EstadoCitaServicio.REALIZADA || estado == EstadoCitaServicio.CANCELADA) {
            throw new ReglaNegocioException("La cita finalizada no puede reprogramarse");
        }
        validarFechas(inicio, fin);
        fechaInicio = inicio;
        fechaFin = fin;
        this.lugar = lugar;
        estado = EstadoCitaServicio.REPROGRAMADA;
    }

    public void confirmar() {
        if (estado != EstadoCitaServicio.PROGRAMADA && estado != EstadoCitaServicio.REPROGRAMADA) {
            throw new ReglaNegocioException("La cita no puede confirmarse en su estado actual");
        }
        estado = EstadoCitaServicio.CONFIRMADA;
    }

    public void completar(String resultado) {
        if (estado != EstadoCitaServicio.CONFIRMADA && estado != EstadoCitaServicio.PROGRAMADA) {
            throw new ReglaNegocioException("La cita no puede marcarse como realizada");
        }
        if (resultado == null || resultado.isBlank()) {
            throw new ReglaNegocioException("El resultado de la cita es obligatorio");
        }
        this.resultado = resultado.trim();
        estado = EstadoCitaServicio.REALIZADA;
    }

    public void cancelar(String motivo) {
        if (estado == EstadoCitaServicio.REALIZADA || estado == EstadoCitaServicio.CANCELADA) {
            throw new ReglaNegocioException("La cita no puede cancelarse en su estado actual");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("El motivo de cancelación es obligatorio");
        }
        resultado = motivo.trim();
        estado = EstadoCitaServicio.CANCELADA;
    }

    private static void validarFechas(LocalDateTime inicio, LocalDateTime fin) {
        if (inicio == null || fin == null || !fin.isAfter(inicio)) {
            throw new ReglaNegocioException("La fecha final debe ser posterior a la inicial");
        }
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public Long getClienteId() { return clienteId; }
    public Long getSolicitudId() { return solicitudId; }
    public Long getResponsableId() { return responsableId; }
    public String getTipo() { return tipo; }
    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public LocalDateTime getFechaFin() { return fechaFin; }
    public String getLugar() { return lugar; }
    public EstadoCitaServicio getEstado() { return estado; }
    public String getResultado() { return resultado; }
    public Long getVersion() { return version; }
}
