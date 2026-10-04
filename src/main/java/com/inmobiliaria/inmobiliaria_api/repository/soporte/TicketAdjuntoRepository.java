package com.inmobiliaria.inmobiliaria_api.repository.soporte;

import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketAdjunto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketAdjuntoRepository extends JpaRepository<TicketAdjunto, Long> {
    boolean existsByTicketIdAndArchivoId(Long ticketId, Long archivoId);
    List<TicketAdjunto> findByTicketIdOrderByCreadoEnAsc(Long ticketId);
}
