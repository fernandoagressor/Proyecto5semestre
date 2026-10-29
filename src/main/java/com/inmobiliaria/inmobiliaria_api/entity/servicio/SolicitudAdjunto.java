package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import java.time.LocalDateTime;

/**
 * Archivo adjunto a una PQRS o a uno de sus mensajes; vive embebido en {@link SolicitudPqrs}.
 * El archivo en sí sigue en la tabla {@code archivos} de MySQL y aquí solo se guarda su id.
 */
public class SolicitudAdjunto {
    private Long id;
    private Long mensajeId;
    private Long archivoId;
    private LocalDateTime creadoEn;

    protected SolicitudAdjunto() { }

    public SolicitudAdjunto(Long id, Long mensajeId, Long archivoId, LocalDateTime creadoEn) {
        this.id = id;
        this.mensajeId = mensajeId;
        this.archivoId = archivoId;
        this.creadoEn = creadoEn;
    }

    public Long getId() { return id; }
    public Long getMensajeId() { return mensajeId; }
    public Long getArchivoId() { return archivoId; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
