package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "solicitud_historial")
public class SolicitudHistorial extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "solicitud_id", nullable = false)
    private Long solicitudId;
    @Column(nullable = false, length = 40)
    private String campo;
    @Column(name = "valor_anterior", length = 500)
    private String valorAnterior;
    @Column(name = "valor_nuevo", nullable = false, length = 500)
    private String valorNuevo;
    @Column(name = "usuario_id")
    private Long usuarioId;
    @Column(length = 500)
    private String motivo;

    protected SolicitudHistorial() { }

    public SolicitudHistorial(Long solicitudId, String campo, String valorAnterior,
                              String valorNuevo, Long usuarioId, String motivo) {
        this.solicitudId = solicitudId;
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
        this.usuarioId = usuarioId;
        this.motivo = motivo;
    }

    public Long getId() { return id; }
    public Long getSolicitudId() { return solicitudId; }
    public String getCampo() { return campo; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNuevo() { return valorNuevo; }
    public Long getUsuarioId() { return usuarioId; }
    public String getMotivo() { return motivo; }
}
