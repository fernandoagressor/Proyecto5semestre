package com.inmobiliaria.inmobiliaria_api.entity.crm;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "oportunidad_lotes")
public class OportunidadLote extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oportunidad_id", nullable = false)
    private Long oportunidadId;

    @Column(name = "lote_id", nullable = false)
    private Long loteId;

    @Column(nullable = false)
    private int prioridad;

    @Column(nullable = false)

    private boolean seleccionado;

    protected OportunidadLote() {
    }

    public OportunidadLote(
            Long oportunidadId,
            Long loteId,
            int prioridad
    ) {
        this.oportunidadId = oportunidadId;
        this.loteId = loteId;
        this.prioridad = prioridad;
        this.seleccionado = false;
    }

    public void seleccionar(boolean seleccionado) {
        this.seleccionado = seleccionado;
    }

    public Long getId() {
        return id;
    }

    public Long getOportunidadId() {
        return oportunidadId;
    }

    public Long getLoteId() {
        return loteId;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public boolean isSeleccionado() {
        return seleccionado;
    }
}
