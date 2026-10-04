package com.inmobiliaria.inmobiliaria_api.entity.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CuotaTest {

    @Test
    void soloPermiteAplicarHastaElSaldo() {
        Cuota cuota = cuota();
        cuota.aplicar(new BigDecimal("250.00"), ConceptoPago.CAPITAL, LocalDate.of(2026, 8, 15));
        assertEquals(new BigDecimal("750.00"), cuota.getSaldo());
        assertEquals(EstadoCuota.PARCIAL, cuota.getEstado());
        assertThrows(ReglaNegocioException.class, () ->
                cuota.aplicar(new BigDecimal("751.00"), ConceptoPago.CAPITAL, LocalDate.of(2026, 8, 15)));
    }

    @Test
    void unaReversionRestauraSaldoYEstado() {
        Cuota cuota = cuota();
        cuota.aplicar(new BigDecimal("1000.00"), ConceptoPago.CAPITAL, LocalDate.of(2026, 8, 15));
        assertEquals(EstadoCuota.PAGADA, cuota.getEstado());
        cuota.revertir(new BigDecimal("1000.00"), ConceptoPago.CAPITAL, LocalDate.of(2026, 8, 16));
        assertEquals(new BigDecimal("1000.00"), cuota.getSaldo());
        assertEquals(EstadoCuota.PENDIENTE, cuota.getEstado());
    }

    @Test
    void unaCuotaGeneradaEnPesosQuedaPagadaSinResiduoOculto() {
        Cuota cuota = new Cuota(
                1L,
                1L,
                1L,
                1,
                LocalDate.of(2026, 9, 10),
                new BigDecimal("4958333.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        cuota.aplicar(
                new BigDecimal("4958333.00"),
                ConceptoPago.CAPITAL,
                LocalDate.of(2026, 8, 11)
        );

        assertEquals(BigDecimal.ZERO.setScale(2), cuota.getSaldo());
        assertEquals(EstadoCuota.PAGADA, cuota.getEstado());
    }

    private Cuota cuota() {
        return new Cuota(1L, 1L, 1L, 1, LocalDate.of(2026, 9, 1), new BigDecimal("900"), new BigDecimal("100"), BigDecimal.ZERO);
    }
}
