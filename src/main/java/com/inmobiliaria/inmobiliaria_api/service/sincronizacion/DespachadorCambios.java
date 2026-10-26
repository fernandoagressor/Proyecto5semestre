package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Entrega cada cambio de MongoDB al proyector de su colección y avanza el token de reanudación.
 *
 * <p>Garantías (cada una cubierta por una prueba en {@code DespachadorCambiosTest}):</p>
 * <ul>
 *   <li>Cada cambio va solo al proyector de su colección.</li>
 *   <li>El token se guarda <b>después</b> de aplicar el cambio: si la aplicación se detiene
 *       en medio, el cambio se vuelve a entregar al reanudar (entrega "al menos una vez").
 *       Por eso los proyectores deben ser idempotentes.</li>
 *   <li>Si el proyector falla, el error se propaga y el token no avanza.</li>
 *   <li>Los cambios de colecciones sin proyector (por ejemplo, {@code contadores}) se ignoran,
 *       pero el token avanza.</li>
 *   <li>Dos proyectores para la misma colección impiden crear el despachador.</li>
 * </ul>
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