package com.inmobiliaria.inmobiliaria_api.service.crm;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica en Prometheus el estado de la cola {@code crm_pendientes}:
 * {@code nexora_crm_pendientes} (por aplicar, todavía con reintentos) y
 * {@code nexora_crm_pendientes_agotados} (agotaron los {@value ProcesadorPendientesCrm#MAX_INTENTOS}
 * intentos y necesitan revisión manual; debería ser siempre 0).
 */
@Component
public class MetricasPendientesCrm {

    private final JdbcTemplate jdbcTemplate;

    public MetricasPendientesCrm(MeterRegistry registry, JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("nexora.crm.pendientes", this, MetricasPendientesCrm::porAplicar)
                .description("Cambios de la venta pendientes de aplicar al CRM en MongoDB")
                .register(registry);
        Gauge.builder("nexora.crm.pendientes.agotados", this, MetricasPendientesCrm::agotados)
                .description("Cambios que agotaron sus reintentos y esperan revisión manual")
                .register(registry);
    }

    double porAplicar() {
        return contar("procesado_en IS NULL AND intentos < ?");
    }

    double agotados() {
        return contar("procesado_en IS NULL AND intentos >= ?");
    }

    private double contar(String condicion) {
        try {
            Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_pendientes WHERE " + condicion,
                    Long.class, ProcesadorPendientesCrm.MAX_INTENTOS);
            return total == null ? 0 : total;
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
