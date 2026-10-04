package com.inmobiliaria.inmobiliaria_api.entity.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pago_historial_estados")
public class PagoHistorialEstado extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "pago_id", nullable = false)
    private Long pagoId;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private EstadoPago estadoAnterior;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private EstadoPago estadoNuevo;
    @Column(length = 500)
    private String motivo;
    @Column(name = "usuario_id")
    private Long usuarioId;

    protected PagoHistorialEstado() {
    }

    public PagoHistorialEstado(Long pagoId, EstadoPago estadoAnterior, EstadoPago estadoNuevo, String motivo, Long usuarioId) {
        this.pagoId = pagoId;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.motivo = motivo;
        this.usuarioId = usuarioId;
    }

    public Long getId() { return id; }
    public Long getPagoId() { return pagoId; }
    public EstadoPago getEstadoAnterior() { return estadoAnterior; }
    public EstadoPago getEstadoNuevo() { return estadoNuevo; }
    public String getMotivo() { return motivo; }
    public Long getUsuarioId() { return usuarioId; }
}
