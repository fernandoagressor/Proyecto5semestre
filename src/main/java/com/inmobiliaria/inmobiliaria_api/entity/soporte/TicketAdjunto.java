package com.inmobiliaria.inmobiliaria_api.entity.soporte;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket_adjuntos")
public class TicketAdjunto extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;
    @Column(name = "mensaje_id")
    private Long mensajeId;
    @Column(name = "archivo_id", nullable = false)
    private Long archivoId;

    protected TicketAdjunto() { }
    public TicketAdjunto(Long ticketId, Long mensajeId, Long archivoId) {
        this.ticketId = ticketId;
        this.mensajeId = mensajeId;
        this.archivoId = archivoId;
    }
    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Long getMensajeId() { return mensajeId; }
    public Long getArchivoId() { return archivoId; }
}
