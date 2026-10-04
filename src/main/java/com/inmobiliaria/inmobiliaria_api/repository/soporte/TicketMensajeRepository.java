package com.inmobiliaria.inmobiliaria_api.repository.soporte;

import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketMensaje;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketMensajeRepository extends JpaRepository<TicketMensaje, Long> {
    List<TicketMensaje> findByTicketIdOrderByCreadoEnAsc(Long ticketId);
    List<TicketMensaje> findByTicketIdAndInternoFalseOrderByCreadoEnAsc(Long ticketId);
    Optional<TicketMensaje> findByIdAndTicketId(Long id, Long ticketId);
}
