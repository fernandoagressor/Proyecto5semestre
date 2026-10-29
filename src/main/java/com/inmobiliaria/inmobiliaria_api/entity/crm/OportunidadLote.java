package com.inmobiliaria.inmobiliaria_api.entity.crm;

import java.time.LocalDateTime;

/** Lote de interés de una oportunidad; vive embebido en {@link Oportunidad#getLotesInteres()}. */
public class OportunidadLote {
    private Long id;
    private Long oportunidadId;
    private Long loteId;
    private int prioridad;
    private boolean seleccionado;
    private LocalDateTime creadoEn;

    protected OportunidadLote() {
    }

    public OportunidadLote(Long id, Long oportunidadId, Long loteId, int prioridad, LocalDateTime creadoEn) {
        this.id = id;
        this.oportunidadId = oportunidadId;
        this.loteId = loteId;
        this.prioridad = prioridad;
        this.seleccionado = false;
        this.creadoEn = creadoEn;
    }

    public void seleccionar(boolean seleccionado) {
        this.seleccionado = seleccionado;
    }

    public Long getId() { return id; }
    public Long getOportunidadId() { return oportunidadId; }
    public Long getLoteId() { return loteId; }
    public int getPrioridad() { return prioridad; }
    public boolean isSeleccionado() { return seleccionado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
