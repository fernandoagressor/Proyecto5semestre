package com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReservaReversionPagoTest {
    @Test
    void reversionDevuelveLaReservaAlPagoPendiente() {
        LocalDateTime ahora = LocalDateTime.of(2026, 8, 1, 9, 0);
        Reserva reserva = new Reserva(1L, 1L, "RSV-1", 1L, null, 1L, 1L, 1L, ahora, ahora.plusDays(2), new BigDecimal("500000"), "TRANSFERENCIA");
        reserva.confirmarPago(ahora.plusMinutes(5), 9L, 10L);
        reserva.reversarPagoConfirmado();
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reserva.getEstado());
        assertEquals(EstadoPagoReserva.PENDIENTE, reserva.getEstadoPago());
    }
}
