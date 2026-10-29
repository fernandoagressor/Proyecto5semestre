package com.inmobiliaria.inmobiliaria_api.entity.simulacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reglas de la simulación como documento de MongoDB (rama 8b). */
class SimulacionAgregadoTest {

    private static final LocalDateTime AHORA = LocalDateTime.parse("2026-10-29T10:00:00");

    @Test
    void validarLaAceptacionNoCambiaElEstado() {
        Simulacion simulacion = nueva();
        assertThatThrownBy(() -> simulacion.validarAceptacion(AHORA))
                .as("en borrador").isInstanceOf(ReglaNegocioException.class);

        simulacion.enviar(AHORA);
        simulacion.validarAceptacion(AHORA);

        assertThat(simulacion.getEstado()).isEqualTo(EstadoSimulacion.ENVIADA);
        assertThatThrownBy(() -> simulacion.validarAceptacion(AHORA.plusDays(31)))
                .as("vencida").isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void aplicarLaAceptacionEsIdempotenteYLaReservaManda() {
        Simulacion simulacion = nueva();
        simulacion.enviar(AHORA);
        simulacion.cancelar();

        assertThat(simulacion.aplicarAceptacion()).isTrue();
        assertThat(simulacion.getEstado()).isEqualTo(EstadoSimulacion.ACEPTADA);
        assertThat(simulacion.aplicarAceptacion()).isFalse();
        assertThatThrownBy(simulacion::cancelar).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void elIdYElCronogramaSoloSeAsignanUnaVezYLasCuotasSalenOrdenadas() {
        Simulacion simulacion = nueva();
        simulacion.identificar(7L);
        assertThatThrownBy(() -> simulacion.identificar(8L)).isInstanceOf(IllegalStateException.class);

        simulacion.asignarCronograma(List.of(cuota(2), cuota(1)));
        assertThatThrownBy(() -> simulacion.asignarCronograma(List.of(cuota(3))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(simulacion.getCuotas()).extracting(SimulacionCuota::getNumero).containsExactly(1, 2);
    }

    private static Simulacion nueva() {
        return new Simulacion(8L, 1L, "SIM-1", 20L, 301L, 34L, new BigDecimal("100000000.00"),
                new BigDecimal("5000000.00"), new BigDecimal("20000000.00"), new BigDecimal("75000000.00"),
                12, new BigDecimal("0.120000"), Periodicidad.MENSUAL, AHORA.plusDays(30));
    }

    private static SimulacionCuota cuota(int numero) {
        return new SimulacionCuota(7L, numero, LocalDate.parse("2026-12-01").plusMonths(numero - 1L),
                new BigDecimal("6000000.00"), new BigDecimal("700000.00"), BigDecimal.ZERO,
                new BigDecimal("6700000.00"), new BigDecimal("69000000.00"));
    }
}
