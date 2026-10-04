package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodoDashboardTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 16);

    @Test
    void usaTodoElHistoricoCuandoNoSeEnviaUnRango() {
        PeriodoDashboard periodo = PeriodoDashboard.resolver(null, null, HOY);

        assertThat(periodo.desde()).isEqualTo(PeriodoDashboard.INICIO_HISTORICO);
        assertThat(periodo.hasta()).isEqualTo(HOY);
    }

    @Test
    void conservaElRangoSeleccionadoPorElUsuario() {
        PeriodoDashboard periodo = PeriodoDashboard.resolver(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 16),
                HOY
        );

        assertThat(periodo.desde()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(periodo.hasta()).isEqualTo(LocalDate.of(2026, 9, 16));
    }

    @Test
    void rechazaUnaFechaFinalAnteriorALaInicial() {
        assertThatThrownBy(() -> PeriodoDashboard.resolver(
                LocalDate.of(2026, 9, 17),
                LocalDate.of(2026, 9, 16),
                HOY
        )).isInstanceOf(ReglaNegocioException.class)
          .hasMessage("La fecha final no puede ser anterior a la inicial");
    }
}
