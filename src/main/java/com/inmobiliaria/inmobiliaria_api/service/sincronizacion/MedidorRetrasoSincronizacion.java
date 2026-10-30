package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * Mide cuánto tarda un cambio de MongoDB en llegar a su tabla espejo: desde la marca de tiempo
 * del documento ({@code actualizadoEn}, o {@code creadoEn} si es nuevo) hasta que el
 * {@link DespachadorCambios} termina de proyectarlo. Se publica como {@code nexora_sync_retraso}
 * (histograma); el plan de pruebas pide p95 por debajo de 5 s.
 */
@Component
public class MedidorRetrasoSincronizacion implements OyenteProyeccion {

    /** Más de un día no es retraso de la sincronización sino un documento viejo reproyectado. */
    private static final Duration MAXIMO_CREIBLE = Duration.ofDays(1);

    private final Timer retraso;

    public MedidorRetrasoSincronizacion(MeterRegistry registry) {
        this.retraso = Timer.builder("nexora.sync.retraso")
                .description("Tiempo entre la escritura en MongoDB y su proyección a la tabla espejo")
                .register(registry);
    }

    @Override
    public void proyectado(CambioDocumento cambio) {
        if (cambio.datos() == null) {
            return;
        }
        Object marca = cambio.datos().get("actualizadoEn");
        if (!(marca instanceof Date)) {
            marca = cambio.datos().get("creadoEn");
        }
        if (marca instanceof Date fecha) {
            Duration demora = Duration.between(fecha.toInstant(), Instant.now());
            if (!demora.isNegative() && demora.compareTo(MAXIMO_CREIBLE) < 0) {
                retraso.record(demora);
            }
        }
    }
}
