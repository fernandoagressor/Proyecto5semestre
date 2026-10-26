package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Entrega cada cambio de MongoDB al proyector de su colección.
 */
public class DespachadorCambios {

    public static final String FLUJO = "nexora-principal";

    private final Map<String, ProyectorLectura> proyectoresPorColeccion;
    private final AlmacenTokenReanudacion tokens;

    public DespachadorCambios(List<ProyectorLectura> proyectores, AlmacenTokenReanudacion tokens) {
        this.proyectoresPorColeccion = proyectores.stream()
                .collect(Collectors.toMap(
                        ProyectorLectura::coleccion,
                        Function.identity(),
                        (primero, segundo) -> {
                            throw new IllegalStateException(
                                    "Hay más de un proyector para la colección "
                                            + primero.coleccion());
                        }));
        this.tokens = tokens;
    }

    public void procesar(CambioDocumento cambio) {
        ProyectorLectura proyector = proyectoresPorColeccion.get(cambio.coleccion());
        if (proyector != null) {
            proyector.aplicar(cambio);
        }
        tokens.guardar(FLUJO, cambio.tokenReanudacion());
    }


}