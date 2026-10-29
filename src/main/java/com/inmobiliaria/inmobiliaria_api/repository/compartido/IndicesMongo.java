package com.inmobiliaria.inmobiliaria_api.repository.compartido;

/**
 * Repositorio de MongoDB que declara los índices de su colección. {@code InicializadorIndicesMongo}
 * los crea al arrancar, aunque el migrador de esa colección esté apagado.
 */
public interface IndicesMongo {

    /** Crea los índices de la colección; debe ser idempotente. */
    void asegurarIndices();
}
