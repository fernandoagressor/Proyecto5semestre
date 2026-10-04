package com.inmobiliaria.inmobiliaria_api.entity.soporte;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket_mensajes")
public class TicketMensaje extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;
    @Column(name = "usuario_id")
    private Long usuarioId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensaje;
    @Column(name = "es_respuesta_interna", nullable = false)
    private boolean interno;

    protected TicketMensaje() { }

    public TicketMensaje(Long ticketId, Long usuarioId, String mensaje, boolean interno) {
        this.ticketId = ticketId;
        this.usuarioId = usuarioId;
        this.mensaje = mensaje;
        this.interno = interno;
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Long getUsuarioId() { return usuarioId; }
    public String getMensaje() { return mensaje; }
    public boolean isInterno() { return interno; }
}
