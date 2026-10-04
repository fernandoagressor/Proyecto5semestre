package com.inmobiliaria.inmobiliaria_api.entity.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PagoReversionTest {
    @Test
    void exigeAprobadorDistinto() {
        PagoReversion reversion = new PagoReversion(1L, new BigDecimal("100"), "Error de referencia", null, 8L);
        assertThrows(ReglaNegocioException.class, () -> reversion.aprobarYEjecutar(8L, LocalDateTime.now()));
        reversion.aprobarYEjecutar(9L, LocalDateTime.now());
        assertEquals(EstadoReversion.EJECUTADA, reversion.getEstado());
    }
}
