package com.inmobiliaria.inmobiliaria_api.entity.soporte;

import java.time.LocalDateTime;

/**
 * Archivo adjunto a un ticket o a uno de sus mensajes; vive embebido en {@link TicketSoporte}.
 * El archivo sigue en la tabla {@code archivos} de MySQL y aquí solo se guarda su id.
 */
public class TicketAdjunto {
    private Long id;
    private Long mensajeId;
    private Long archivoId;
    private LocalDateTime creadoEn;

    protected TicketAdjunto() { }

    public TicketAdjunto(Long id, Long mensajeId, Long archivoId, LocalDateTime creadoEn) {
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
