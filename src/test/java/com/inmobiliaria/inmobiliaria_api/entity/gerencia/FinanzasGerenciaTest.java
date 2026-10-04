package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinanzasGerenciaTest {
    @Test
    void registradorNoPuedeAprobarCosto() {
        CostoProyecto costo = new CostoProyecto(1L, 2L, 3L, LocalDate.of(2026, 7, 1),
                "Urbanismo", "Proveedor", "FAC-1", new BigDecimal("12000000"), null, 8L);
        assertThrows(ReglaNegocioException.class, () -> costo.aprobar(8L));
        costo.aprobar(9L);
        costo.pagar();
        assertEquals(EstadoCosto.PAGADO, costo.getEstado());
    }

    @Test
    void presupuestoCerradoNoPuedeAnularse() {
        PresupuestoProyecto presupuesto = new PresupuestoProyecto(1L, 2L, 3L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30),
                new BigDecimal("200000000"), 8L);
        assertThrows(ReglaNegocioException.class, () -> presupuesto.aprobar(8L));
        presupuesto.aprobar(9L);
        presupuesto.cerrar();
        assertThrows(ReglaNegocioException.class, presupuesto::anular);
    }
}
