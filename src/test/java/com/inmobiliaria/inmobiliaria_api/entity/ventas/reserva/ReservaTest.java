package com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservaTest {

    @Test
    void confirmaPagoYConvierteSoloDuranteLaVigencia() {
        LocalDateTime ahora = LocalDateTime.of(2026, 7, 31, 10, 0);
        Reserva reserva = reserva(ahora, ahora.plusDays(2));

        EstadoReserva anterior = reserva.confirmarPago(
                ahora.plusHours(1),
                8L,
                20L
        );
        assertEquals(EstadoReserva.PENDIENTE_PAGO, anterior);
        assertEquals(EstadoReserva.ACTIVA, reserva.getEstado());
        assertEquals(EstadoPagoReserva.CONFIRMADO, reserva.getEstadoPago());

        reserva.convertir(ahora.plusDays(1));
        assertEquals(EstadoReserva.CONVERTIDA, reserva.getEstado());
    }

    @Test
    void venceYRechazaOperacionesPosteriores() {
        LocalDateTime ahora = LocalDateTime.of(2026, 7, 31, 10, 0);
        Reserva reserva = reserva(ahora, ahora.plusHours(2));

        assertNull(reserva.vencer(ahora.plusHours(1)));
        assertEquals(
                EstadoReserva.PENDIENTE_PAGO,
                reserva.vencer(ahora.plusHours(2))
        );
        assertEquals(EstadoReserva.VENCIDA, reserva.getEstado());
        assertThrows(
                ReglaNegocioException.class,
                () -> reserva.confirmarPago(ahora.plusHours(3), 8L, null)
        );
    }

    @Test
    void rechazoDePagoCancelaConMotivo() {
        LocalDateTime ahora = LocalDateTime.of(2026, 7, 31, 10, 0);
        Reserva reserva = reserva(ahora, ahora.plusDays(1));

        reserva.rechazarPago("Comprobante inválido");

        assertEquals(EstadoReserva.CANCELADA, reserva.getEstado());
        assertEquals(EstadoPagoReserva.RECHAZADO, reserva.getEstadoPago());
        assertEquals("Comprobante inválido", reserva.getMotivoCancelacion());
    }

    private static Reserva reserva(
            LocalDateTime inicio,
            LocalDateTime vencimiento
    ) {
        return new Reserva(
                1L,
                2L,
                "RSV-TEST",
                3L,
                4L,
                5L,
                6L,
                7L,
                inicio,
                vencimiento,
                new BigDecimal("1000000.00"),
                "TRANSFERENCIA"
        );
    }
}
