package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

/**
 * Se entera de cada cambio que el {@link DespachadorCambios} ya proyectó a las tablas espejo
 * (por ejemplo, para invalidar la caché de dashboards). Un oyente que falla no detiene la
 * sincronización: el error queda en el log.
 */
public interface OyenteProyeccion {

    void proyectado(CambioDocumento cambio);
}
