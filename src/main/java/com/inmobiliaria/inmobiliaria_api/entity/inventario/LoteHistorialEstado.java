package com.inmobiliaria.inmobiliaria_api.entity.inventario;

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
@Table(name = "lote_historial_estados")
public class LoteHistorialEstado extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lote_id", nullable = false)
    private Long loteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private EstadoLote estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private EstadoLote estadoNuevo;

    @Column(name = "entidad_origen", nullable = false, length = 40)
    private String entidadOrigen;

    @Column(name = "entidad_origen_id")
    private Long entidadOrigenId;

    @Column(length = 300)
    private String motivo;

    @Column(name = "cambiado_por")
    private Long cambiadoPor;

    protected LoteHistorialEstado() {
    }

    public LoteHistorialEstado(
            Long loteId,
            EstadoLote estadoAnterior,
            EstadoLote estadoNuevo,
            String entidadOrigen,
            Long entidadOrigenId,
            String motivo,
            Long cambiadoPor
    ) {
        this.loteId = loteId;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.entidadOrigen = entidadOrigen;
        this.entidadOrigenId = entidadOrigenId;
        this.motivo = motivo;
        this.cambiadoPor = cambiadoPor;
    }

    public Long getId() {
        return id;
    }

    public EstadoLote getEstadoAnterior() {
        return estadoAnterior;
    }

    public EstadoLote getEstadoNuevo() {
        return estadoNuevo;
    }

    public String getEntidadOrigen() {
        return entidadOrigen;
    }

    public Long getEntidadOrigenId() {
        return entidadOrigenId;
    }

    public String getMotivo() {
        return motivo;
    }

    public Long getCambiadoPor() {
        return cambiadoPor;
    }
}
