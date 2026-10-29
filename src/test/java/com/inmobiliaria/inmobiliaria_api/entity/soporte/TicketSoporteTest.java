package com.inmobiliaria.inmobiliaria_api.entity.soporte;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.PrioridadServicio;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void elSolicitanteNoVeLasNotasInternas() {
        TicketSoporte ticket = nuevo();
        ticket.agregarMensaje(1L, 7L, "No puedo ingresar", false);
        ticket.agregarMensaje(2L, 3L, "Revisar bloqueo de la cuenta", true);

        assertEquals(1, ticket.mensajes(false).size());
        assertEquals(2, ticket.mensajes(true).size());
    }

    @Test
    void unAdjuntoDebeSerDeUnMensajeDelTicketYNoRepetirse() {
        TicketSoporte ticket = nuevo();
        ticket.agregarMensaje(1L, 7L, "Captura del error", false);

        assertThrows(ReglaNegocioException.class, () -> ticket.adjuntar(10L, 99L, 500L));
        ticket.adjuntar(10L, 1L, 500L);
        assertThrows(ReglaNegocioException.class, () -> ticket.adjuntar(11L, null, 500L));
        assertEquals(1, ticket.getAdjuntos().size());
    }

    @Test
    void asignarPoneElTicketEnProcesoYQuedaEnElHistorial() {
        TicketSoporte ticket = nuevo();
        Long anterior = ticket.asignar(3L);
        ticket.registrarCambio(1L, "asignado_a", null, "3", 9L, null);

        assertNull(anterior);
        assertEquals(EstadoTicket.EN_PROCESO, ticket.getEstado());
        assertEquals("asignado_a", ticket.getHistorial().get(0).getCampo());
        assertThrows(IllegalStateException.class, () -> {
            ticket.identificar(5L);
            ticket.identificar(6L);
        });
    }

    private static TicketSoporte nuevo() {
        return new TicketSoporte(1L, null, "SUP-1", 7L, "Error",
                "ACCESO", PrioridadServicio.ALTA, "No puedo ingresar");
    }
}
