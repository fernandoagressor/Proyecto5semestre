package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import java.time.LocalDateTime;

/** Cambio de estado o reasignación de una tarea; vive embebido en {@link Tarea}. */
public class TareaHistorial {
    private Long id;
    private Long tareaId;
    private String estadoAnterior;
    private String estadoNuevo;
    private Long usuarioId;
    private String comentario;
    private LocalDateTime creadoEn;

    protected TareaHistorial() { }

    public TareaHistorial(Long id, Long tareaId, String estadoAnterior, String estadoNuevo,
                          Long usuarioId, String comentario, LocalDateTime creadoEn) {
        this.id = id;
        this.tareaId = tareaId;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.usuarioId = usuarioId;
        this.comentario = comentario == null || comentario.isBlank() ? null : comentario.trim();
        this.creadoEn = creadoEn;
    }

    public Long getId() { return id; }
    public Long getTareaId() { return tareaId; }
    public String getEstadoAnterior() { return estadoAnterior; }
    public String getEstadoNuevo() { return estadoNuevo; }
    public Long getUsuarioId() { return usuarioId; }
    public String getComentario() { return comentario; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
