package com.inmobiliaria.inmobiliaria_api.service.servicio;

/**
 * Nombres de las secuencias (colección {@code contadores}) que numeran las PQRS y sus partes
 * embebidas. Coinciden con las tablas de MySQL de donde vienen los ids migrados.
 */
final class SecuenciasPqrs {
    static final String SOLICITUDES = "solicitudes_pqrs";
    static final String MENSAJES = "solicitud_mensajes";
    static final String ADJUNTOS = "solicitud_adjuntos";
    static final String HISTORIAL = "solicitud_historial";

    private SecuenciasPqrs() {
    }
}
