package com.inmobiliaria.inmobiliaria_api.entity.crm;

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
@Table(name = "oportunidad_etapa_historial")
public class OportunidadEtapaHistorial extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oportunidad_id", nullable = false)
    private Long oportunidadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa_anterior", length = 40)
    private EtapaOportunidad etapaAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa_nueva", nullable = false, length = 40)
    private EtapaOportunidad etapaNueva;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(length = 500)
    private String observacion;

    protected OportunidadEtapaHistorial() {
    }

    public OportunidadEtapaHistorial(
            Long oportunidadId,
            EtapaOportunidad etapaAnterior,
            EtapaOportunidad etapaNueva,
            Long usuarioId,
            String observacion
    ) {
        this.oportunidadId = oportunidadId;
        this.etapaAnterior = etapaAnterior;
        this.etapaNueva = etapaNueva;
        this.usuarioId = usuarioId;
        this.observacion = observacion;
    }

    public Long getId() {
        return id;
    }

    public Long getOportunidadId() {
        return oportunidadId;
    }

    public EtapaOportunidad getEtapaAnterior() {
        return etapaAnterior;
    }

    public EtapaOportunidad getEtapaNueva() {
        return etapaNueva;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getObservacion() {
        return observacion;
    }
}
