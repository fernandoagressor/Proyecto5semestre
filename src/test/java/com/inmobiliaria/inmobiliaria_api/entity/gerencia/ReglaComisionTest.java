package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReglaComisionTest {
    @Test
    void calculaPorcentajeSobreLaBase() {
        ReglaComision regla = new ReglaComision(1L, 2L, "Venta", TipoReglaComision.PORCENTAJE,
                new BigDecimal("2.5"), "CONTRATO_VIGENTE", LocalDate.of(2026, 1, 1), null);
        assertEquals(new BigDecimal("2500000.00"), regla.calcular(new BigDecimal("100000000")));
    }

    @Test
    void reglaCerradaNoAdmiteCambios() {
        ReglaComision regla = new ReglaComision(1L, null, "Venta", TipoReglaComision.VALOR_FIJO,
                new BigDecimal("500000"), "CONTRATO_VIGENTE", LocalDate.of(2026, 1, 1), null);
        regla.cerrar(LocalDate.of(2026, 6, 30));
        assertThrows(ReglaNegocioException.class, () -> regla.actualizar("Otra",
                TipoReglaComision.VALOR_FIJO, BigDecimal.ONE, "CONTRATO_VIGENTE",
                LocalDate.of(2026, 1, 1), null));
    }
}
