package com.inmobiliaria.inmobiliaria_api.entity.soporte;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.PrioridadServicio;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketSoporteTest {
    @Test
    void soloUnTicketResueltoPuedeCerrarse() {
        TicketSoporte ticket = nuevo();
        assertThrows(ReglaNegocioException.class,
                () -> ticket.cambiarEstado(EstadoTicket.CERRADO, LocalDateTime.now()));
        ticket.cambiarEstado(EstadoTicket.RESUELTO, LocalDateTime.now());
        ticket.cambiarEstado(EstadoTicket.CERRADO, LocalDateTime.now());
        assertEquals(EstadoTicket.CERRADO, ticket.getEstado());
    }

    private static TicketSoporte nuevo() {
        return new TicketSoporte(1L, null, "SUP-1", 7L, "Error",
                "ACCESO", PrioridadServicio.ALTA, "No puedo ingresar");
    }
}
