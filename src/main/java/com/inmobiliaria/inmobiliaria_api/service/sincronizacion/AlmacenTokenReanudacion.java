package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import java.util.Optional;

/** Guarda la última posición procesada del Change Stream para poder reanudarlo. */
public interface AlmacenTokenReanudacion {

    Optional<String> leer(String flujo);

    void guardar(String flujo, String token);

    /** Olvida la posición guardada: el flujo vuelve a empezar desde ahora. */
    void borrar(String flujo);
}