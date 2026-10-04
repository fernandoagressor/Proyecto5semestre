package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "solicitud_adjuntos")
public class SolicitudAdjunto extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "solicitud_id", nullable = false)
    private Long solicitudId;
    @Column(name = "mensaje_id")
    private Long mensajeId;
    @Column(name = "archivo_id", nullable = false)
    private Long archivoId;

    protected SolicitudAdjunto() { }

    public SolicitudAdjunto(Long solicitudId, Long mensajeId, Long archivoId) {
        this.solicitudId = solicitudId;
        this.mensajeId = mensajeId;
        this.archivoId = archivoId;
    }

    public Long getId() { return id; }
    public Long getSolicitudId() { return solicitudId; }
    public Long getMensajeId() { return mensajeId; }
    public Long getArchivoId() { return archivoId; }
}
