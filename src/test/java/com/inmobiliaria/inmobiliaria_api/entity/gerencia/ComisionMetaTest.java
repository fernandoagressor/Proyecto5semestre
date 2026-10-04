package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComisionMetaTest {
    @Test
    void beneficiarioNoApruebaSuComision() {
        ReglaComision regla = new ReglaComision(1L, 2L, "Venta", TipoReglaComision.PORCENTAJE,
                BigDecimal.ONE, "CONTRATO_VIGENTE", LocalDate.of(2026, 1, 1), null);
        Comision comision = new Comision(1L, 2L, 9L, 10L, regla, new BigDecimal("100000000"));
        comision.causar(LocalDate.of(2026, 7, 1));
        assertThrows(ReglaNegocioException.class, () -> comision.aprobar(9L));
        comision.aprobar(8L);
        assertEquals(EstadoComision.APROBADA, comision.getEstado());
    }

    @Test
    void metaAprobadaSeCierraSoloDespuesDelPeriodo() {
        MetaComercial meta = new MetaComercial(1L, 2L, 9L, "2026-06",
                new BigDecimal("500000000"), 5);
        meta.aprobar(8L);
        meta.cerrar(YearMonth.of(2026, 7));
        assertEquals(EstadoMeta.CERRADA, meta.getEstado());
    }
}
