package com.inmobiliaria.inmobiliaria_api.service.crm;

/**
 * Nombres de las secuencias (colección {@code contadores}) del CRM. Coinciden con las tablas de
 * MySQL de donde vienen los ids migrados.
 */
final class SecuenciasCrm {
    static final String PROSPECTOS = "prospectos";
    static final String OPORTUNIDADES = "oportunidades";
    static final String LOTES_INTERES = "oportunidad_lotes";
    static final String HISTORIAL_ETAPAS = "oportunidad_etapa_historial";
    static final String ACTIVIDADES = "actividades_comerciales";
    static final String CITAS = "citas_comerciales";

    private SecuenciasCrm() {
    }
}
