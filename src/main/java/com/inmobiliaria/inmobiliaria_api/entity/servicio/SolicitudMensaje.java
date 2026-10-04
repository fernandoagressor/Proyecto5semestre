package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "solicitud_mensajes")
public class SolicitudMensaje extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "solicitud_id", nullable = false)
    private Long solicitudId;
    @Column(name = "usuario_id")
    private Long usuarioId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensaje;
    @Column(name = "es_interno", nullable = false)
    private boolean interno;
    @Column(nullable = false, length = 20)
    private String canal;

    protected SolicitudMensaje() { }

    public SolicitudMensaje(Long solicitudId, Long usuarioId, String mensaje, boolean interno, String canal) {
        this.solicitudId = solicitudId;
        this.usuarioId = usuarioId;
        this.mensaje = mensaje;
        this.interno = interno;
        this.canal = canal;
    }

    public Long getId() { return id; }
    public Long getSolicitudId() { return solicitudId; }
    public Long getUsuarioId() { return usuarioId; }
    public String getMensaje() { return mensaje; }
    public boolean isInterno() { return interno; }
    public String getCanal() { return canal; }
}
