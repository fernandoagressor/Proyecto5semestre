package com.inmobiliaria.inmobiliaria_api.entity.crm;

import java.time.LocalDateTime;

/** Cambio de etapa de una oportunidad; vive embebido en {@link Oportunidad#getHistorialEtapas()}. */
public class OportunidadEtapaHistorial {
    private Long id;
    private Long oportunidadId;
    private EtapaOportunidad etapaAnterior;
    private EtapaOportunidad etapaNueva;
    private Long usuarioId;
    private String observacion;
    private LocalDateTime creadoEn;

    protected OportunidadEtapaHistorial() {
    }

    public OportunidadEtapaHistorial(Long id, Long oportunidadId, EtapaOportunidad etapaAnterior,
                                     EtapaOportunidad etapaNueva, Long usuarioId, String observacion,
                                     LocalDateTime creadoEn) {
        this.id = id;
        this.oportunidadId = oportunidadId;
        this.etapaAnterior = etapaAnterior;
        this.etapaNueva = etapaNueva;
        this.usuarioId = usuarioId;
        this.observacion = observacion;
        this.creadoEn = creadoEn;
    }

    public Long getId() { return id; }
    public Long getOportunidadId() { return oportunidadId; }
    public EtapaOportunidad getEtapaAnterior() { return etapaAnterior; }
    public EtapaOportunidad getEtapaNueva() { return etapaNueva; }
    public Long getUsuarioId() { return usuarioId; }
    public String getObservacion() { return observacion; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
