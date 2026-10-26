package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Entrega cada cambio de MongoDB al proyector de su colección.
 */
public class DespachadorCambios {

    private final Map<String, ProyectorLectura> proyectoresPorColeccion;

    public DespachadorCambios(List<ProyectorLectura> proyectores, AlmacenTokenReanudacion tokens) {
        this.proyectoresPorColeccion = proyectores.stream()
                .collect(Collectors.toMap(ProyectorLectura::coleccion, Function.identity()));
    }

    public void procesar(CambioDocumento cambio) {
        proyectoresPorColeccion.get(cambio.coleccion()).aplicar(cambio);
    }
}