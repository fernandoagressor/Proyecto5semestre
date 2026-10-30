package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Atraso de la sincronización a las tablas espejo (rama P2, monitoreo). */
class MedidorRetrasoSincronizacionTest {

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final MedidorRetrasoSincronizacion medidor = new MedidorRetrasoSincronizacion(registro);

    @Test
    void midePrimeroDesdeLaUltimaActualizacionYSiNoDesdeLaCreacion() {
        Date hace3s = Date.from(Instant.now().minusSeconds(3));
        Date hace1h = Date.from(Instant.now().minus(Duration.ofHours(1)));

        medidor.proyectado(cambio(Map.of("actualizadoEn", hace3s, "creadoEn", hace1h)));
        medidor.proyectado(cambio(Map.of("creadoEn", hace3s)));

        Timer timer = registro.get("nexora.sync.retraso").timer();
        assertThat(timer.count()).isEqualTo(2);
        assertThat(timer.max(TimeUnit.SECONDS)).isBetween(2.5, 10.0);
    }

    @Test
    void ignoraDocumentosSinFechaOViejosReproyectados() {
        medidor.proyectado(cambio(Map.of("estado", "ABIERTA")));
        medidor.proyectado(cambio(Map.of()));
        medidor.proyectado(cambio(Map.of("creadoEn", Date.from(Instant.now().minus(Duration.ofDays(3))))));

        assertThat(registro.get("nexora.sync.retraso").timer().count()).isZero();
    }

    private static CambioDocumento cambio(Map<String, Object> datos) {
        return new CambioDocumento("tareas", TipoCambio.ACTUALIZACION, 1L, datos, "token");
    }
}
