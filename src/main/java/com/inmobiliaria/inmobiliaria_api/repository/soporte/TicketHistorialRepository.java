package com.inmobiliaria.inmobiliaria_api.repository.soporte;

import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketHistorial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketHistorialRepository extends JpaRepository<TicketHistorial, Long> {
    List<TicketHistorial> findByTicketIdOrderByCreadoEnAsc(Long ticketId);
}
