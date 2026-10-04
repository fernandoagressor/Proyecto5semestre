package com.inmobiliaria.inmobiliaria_api.entity.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PagoTest {
    @Test
    void confirmadoPuedeReversarsePeroNoConfirmarseDosVeces() {
        Pago pago = pago();
        pago.confirmar(9L, LocalDateTime.of(2026, 8, 1, 10, 0));
        assertEquals(EstadoPago.CONFIRMADO, pago.getEstado());
        assertThrows(ReglaNegocioException.class, () -> pago.confirmar(9L, LocalDateTime.now()));
        pago.reversar();
        assertEquals(EstadoPago.REVERSADO, pago.getEstado());
    }

    @Test
    void rechazoExigeMotivo() {
        assertThrows(ReglaNegocioException.class, () -> pago().rechazar(9L, LocalDateTime.now(), " "));
    }

    private Pago pago() {
        return new Pago(1L, "PAG-1", 1L, null, 1L, 1L, LocalDateTime.of(2026, 8, 1, 9, 0), new BigDecimal("100000"), "REF-1", 1L, OrigenPago.MANUAL, 8L, null);
    }
}
