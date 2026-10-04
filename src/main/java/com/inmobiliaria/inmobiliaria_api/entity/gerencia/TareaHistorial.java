package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tarea_historial")
public class TareaHistorial extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tarea_id", nullable = false)
    private Long tareaId;
    @Column(name = "estado_anterior", length = 20)
    private String estadoAnterior;
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private String estadoNuevo;
    @Column(name = "usuario_id")
    private Long usuarioId;
    @Column(length = 500)
    private String comentario;

    protected TareaHistorial() { }

    public TareaHistorial(Long tareaId, String estadoAnterior, String estadoNuevo,
                          Long usuarioId, String comentario) {
        this.tareaId = tareaId;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.usuarioId = usuarioId;
        this.comentario = comentario == null || comentario.isBlank() ? null : comentario.trim();
    }

    public Long getId() { return id; }
    public Long getTareaId() { return tareaId; }
    public String getEstadoAnterior() { return estadoAnterior; }
    public String getEstadoNuevo() { return estadoNuevo; }
    public Long getUsuarioId() { return usuarioId; }
    public String getComentario() { return comentario; }
}
