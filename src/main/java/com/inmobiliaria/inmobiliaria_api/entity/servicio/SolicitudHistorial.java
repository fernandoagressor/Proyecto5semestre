package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import java.time.LocalDateTime;

/** Cambio de estado, prioridad o asignación de una PQRS; vive embebido en {@link SolicitudPqrs}. */
public class SolicitudHistorial {
    private Long id;
    private String campo;
    private String valorAnterior;
    private String valorNuevo;
    private Long usuarioId;
    private String motivo;
    private LocalDateTime creadoEn;

    protected SolicitudHistorial() { }

    public SolicitudHistorial(Long id, String campo, String valorAnterior, String valorNuevo,
                              Long usuarioId, String motivo, LocalDateTime creadoEn) {
        this.id = id;
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
        this.usuarioId = usuarioId;
        this.motivo = motivo;
        this.creadoEn = creadoEn;
    }

    public Long getId() { return id; }
    public String getCampo() { return campo; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNuevo() { return valorNuevo; }
    public Long getUsuarioId() { return usuarioId; }
    public String getMotivo() { return motivo; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
