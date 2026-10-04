package com.inmobiliaria.inmobiliaria_api.entity.compartido;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@MappedSuperclass
public abstract class EntidadAuditable {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @PrePersist
    protected void antesDeCrear() {
        LocalDateTime ahora = LocalDateTime.now(BOGOTA);
        creadoEn = ahora;
        actualizadoEn = ahora;
    }

    @PreUpdate
    protected void antesDeActualizar() {
        actualizadoEn = LocalDateTime.now(BOGOTA);
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }
}
