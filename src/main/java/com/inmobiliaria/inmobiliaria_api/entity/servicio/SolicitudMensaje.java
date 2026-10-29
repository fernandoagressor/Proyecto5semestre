package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import java.time.LocalDateTime;

/** Mensaje de la conversación de una PQRS; vive embebido en {@link SolicitudPqrs}. */
public class SolicitudMensaje {
    private Long id;
    private Long usuarioId;
    private String mensaje;
    private boolean interno;
    private String canal;
    private LocalDateTime creadoEn;

    protected SolicitudMensaje() { }

    public SolicitudMensaje(Long id, Long usuarioId, String mensaje, boolean interno,
                            String canal, LocalDateTime creadoEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.mensaje = mensaje;
        this.interno = interno;
        this.canal = canal;
        this.creadoEn = creadoEn;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getMensaje() { return mensaje; }
    public boolean isInterno() { return interno; }
    public String getCanal() { return canal; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
