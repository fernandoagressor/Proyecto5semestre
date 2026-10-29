package com.inmobiliaria.inmobiliaria_api.service.gerencia;

/**
 * Nombres de las secuencias (colección {@code contadores}) del módulo de auditoría y reportes.
 * Coinciden con las tablas de MySQL de donde vienen los ids migrados.
 */
final class SecuenciasAuditoria {
    static final String EVENTOS = "auditoria_eventos";
    static final String ALERTAS = "alertas_anomalia";
    static final String EXPORTACIONES = "exportaciones_auditoria";
    static final String REPORTES = "reportes_generados";

    private SecuenciasAuditoria() {
    }
}
