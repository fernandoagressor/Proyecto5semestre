package com.inmobiliaria.inmobiliaria_api.service.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuotaCalculadoraTest {
    @Test
    void distribuyeTodoElCapitalSinResiduo() {
        CuotaCalculadora calculadora = new CuotaCalculadora();
        var cuotas = calculadora.generar(new BigDecimal("1000000"), 12, new BigDecimal("0.12"), Periodicidad.MENSUAL, LocalDate.of(2026, 9, 1));
        assertEquals(12, cuotas.size());
        assertEquals(new BigDecimal("1000000.00"), cuotas.stream().map(CuotaCalculadora.CuotaCalculada::capital).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertTrue(cuotas.stream().allMatch(c -> c.capital().signum() >= 0));
    }

    @Test
    void generaCuotasEnPesosEnterosYAjustaLaUltima() {
        CuotaCalculadora calculadora = new CuotaCalculadora();

        var cuotas = calculadora.generar(
                new BigDecimal("59500000"),
                12,
                BigDecimal.ZERO,
                Periodicidad.MENSUAL,
                LocalDate.of(2026, 9, 10)
        );

        assertEquals(new BigDecimal("4958333.00"), cuotas.getFirst().capital());
        assertEquals(new BigDecimal("4958337.00"), cuotas.getLast().capital());
        assertEquals(
                new BigDecimal("59500000.00"),
                cuotas.stream()
                        .map(CuotaCalculadora.CuotaCalculada::capital)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
        assertTrue(cuotas.stream().allMatch(c -> c.capital().remainder(BigDecimal.ONE).signum() == 0));
    }

    @Test
    void calculaMoraSoloConDiasPositivos() {
        CuotaCalculadora calculadora = new CuotaCalculadora();
        assertEquals(new BigDecimal("0.00"), calculadora.mora(new BigDecimal("100000"), new BigDecimal("0.25"), 0));
        assertTrue(calculadora.mora(new BigDecimal("100000"), new BigDecimal("0.25"), 30).signum() > 0);
    }
}
