package com.inmobiliaria.inmobiliaria_api.entity.soporte;

import java.time.LocalDateTime;

/** Mensaje de la conversación de un ticket; vive embebido en {@link TicketSoporte}. */
public class TicketMensaje {
    private Long id;
    private Long usuarioId;
    private String mensaje;
    private boolean interno;
    private LocalDateTime creadoEn;

    protected TicketMensaje() { }

    public TicketMensaje(Long id, Long usuarioId, String mensaje, boolean interno, LocalDateTime creadoEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.mensaje = mensaje;
        this.interno = interno;
        this.creadoEn = creadoEn;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getMensaje() { return mensaje; }
    public boolean isInterno() { return interno; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
