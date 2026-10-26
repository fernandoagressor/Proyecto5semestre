package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

/**
 * Copia los cambios de una colección de MongoDB a su tabla espejo en MySQL.
 * Debe ser idempotente: aplicar dos veces el mismo cambio deja el mismo resultado.
 */
public interface ProyectorLectura {

    /** Colección de MongoDB que este proyector atiende. */
    String coleccion();

    void aplicar(CambioDocumento cambio);
}