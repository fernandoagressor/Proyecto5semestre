package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AlertaYReporteTest {

    private static final LocalDateTime AHORA = LocalDateTime.parse("2026-10-29T10:00:00");

    @Test
    void asignarPoneLaAlertaEnInvestigacion() {
        AlertaAnomalia alerta = new AlertaAnomalia(1L, 8L, "ACCESO_INUSUAL", "ALTA", 34L, null, "Tres países en una hora");

        assertThat(alerta.asignar(35L)).isNull();
        assertThat(alerta.getEstado()).isEqualTo(AlertaAnomalia.EN_INVESTIGACION);
        assertThat(alerta.asignar(36L)).isEqualTo(35L);
    }

    @Test
    void cerrarSinAsignadoDejaComoResponsableAQuienCierra() {
        AlertaAnomalia alerta = new AlertaAnomalia(1L, 8L, "ACCESO_INUSUAL", "ALTA", 34L, null, "Tres países en una hora");

        assertThat(alerta.cerrar("Viaje confirmado", true, 9L, AHORA)).isEqualTo(AlertaAnomalia.ABIERTA);

        assertThat(alerta.getEstado()).isEqualTo(AlertaAnomalia.DESCARTADA);
        assertThat(alerta.getAsignadoA()).isEqualTo(9L);
        assertThat(alerta.getResueltaEn()).isEqualTo(AHORA);
        assertThatThrownBy(() -> alerta.asignar(5L)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> alerta.cerrar("Otra vez", false, 9L, AHORA)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void unReporteSoloSeCompletaUnaVezYElFalloExigeDetalle() {
        ReporteGenerado reporte = new ReporteGenerado(1L, 8L, null, 34L, "CARTERA", "{}", "PDF", AHORA.plusDays(7));

        assertThatThrownBy(() -> reporte.completar(null, " ", AHORA)).isInstanceOf(ReglaNegocioException.class);
        reporte.completar(900L, null, AHORA);

        assertThat(reporte.getEstado()).isEqualTo("GENERADO");
        assertThat(reporte.getArchivoId()).isEqualTo(900L);
        assertThatThrownBy(() -> reporte.completar(901L, null, AHORA)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void unReporteFallidoGuardaElDetalle() {
        ReporteGenerado reporte = new ReporteGenerado(2L, 8L, 1L, 34L, "VENTAS", "{}", "XLSX", AHORA.plusDays(7));

        reporte.completar(null, "  Sin datos en el periodo ", AHORA);

        assertThat(reporte.getEstado()).isEqualTo("FALLIDO");
        assertThat(reporte.getDetalleError()).isEqualTo("Sin datos en el periodo");
    }
}
