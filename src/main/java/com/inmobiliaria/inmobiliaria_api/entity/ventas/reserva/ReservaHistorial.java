package com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva;

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
@Table(name = "reserva_historial")
public class ReservaHistorial extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private EstadoReserva estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private EstadoReserva estadoNuevo;

    @Column(length = 400)
    private String motivo;

    @Column(name = "usuario_id")
    private Long usuarioId;

    protected ReservaHistorial() {
    }

    public ReservaHistorial(
            Long reservaId,
            EstadoReserva estadoAnterior,
            EstadoReserva estadoNuevo,
            String motivo,
            Long usuarioId
    ) {
        this.reservaId = reservaId;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.motivo = motivo;
        this.usuarioId = usuarioId;
    }

    public Long getId() { return id; }
    public EstadoReserva getEstadoAnterior() { return estadoAnterior; }
    public EstadoReserva getEstadoNuevo() { return estadoNuevo; }
    public String getMotivo() { return motivo; }
    public Long getUsuarioId() { return usuarioId; }
}
