package com.inmobiliaria.inmobiliaria_api.service.simulacion;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.SimulacionCuota;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanCuotasServiceTest {

    private final PlanCuotasService service = new PlanCuotasService();

    @Test
    void distribuyeSaldoSinInteresYCierraEnCero() {
        List<SimulacionCuota> cuotas = service.generar(
                9L,
                new BigDecimal("100000000.00"),
                12,
                BigDecimal.ZERO,
                Periodicidad.MENSUAL,
                LocalDate.of(2026, 9, 1)
        );

        assertThat(cuotas).hasSize(12);
        assertThat(cuotas.get(11).getSaldoProyectado())
                .isEqualByComparingTo("0.00");
        BigDecimal capitalTotal = cuotas.stream()
                .map(SimulacionCuota::getCapital)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(capitalTotal).isEqualByComparingTo("100000000.00");
    }

    @Test
    void generaInteresYFechasMensuales() {
        List<SimulacionCuota> cuotas = service.generar(
                9L,
                new BigDecimal("50000000.00"),
                6,
                new BigDecimal("0.120000"),
                Periodicidad.MENSUAL,
                LocalDate.of(2026, 9, 15)
        );

        assertThat(cuotas.get(0).getInteres()).isPositive();
        assertThat(cuotas.get(5).getFechaVencimiento())
                .isEqualTo(LocalDate.of(2027, 2, 15));
        assertThat(cuotas.get(5).getSaldoProyectado())
                .isEqualByComparingTo("0.00");
    }
}
