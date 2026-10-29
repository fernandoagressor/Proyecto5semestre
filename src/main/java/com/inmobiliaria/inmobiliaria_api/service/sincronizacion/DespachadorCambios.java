package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li>Después de proyectar avisa a los {@link OyenteProyeccion} (caché de dashboards); si un
 *       oyente falla, se registra y la sincronización sigue.</li>
 * </ul>
 */
public class DespachadorCambios {

    public static final String FLUJO = "nexora-principal";

    private final Map<String, ProyectorLectura> proyectoresPorColeccion;
    private static final Logger log = LoggerFactory.getLogger(DespachadorCambios.class);

    private final AlmacenTokenReanudacion tokens;
    private final List<OyenteProyeccion> oyentes;

    public DespachadorCambios(List<ProyectorLectura> proyectores, AlmacenTokenReanudacion tokens) {
        this(proyectores, tokens, List.of());
    }

    public DespachadorCambios(List<ProyectorLectura> proyectores, AlmacenTokenReanudacion tokens,
                              List<OyenteProyeccion> oyentes) {
        this.proyectoresPorColeccion = proyectores.stream()
                .collect(Collectors.toMap(
                        ProyectorLectura::coleccion,
                        Function.identity(),
                        (primero, segundo) -> {
                            throw new IllegalStateException(
                                    "Hay más de un proyector para la colección "
                                            + primero.coleccion());
                        },
                        LinkedHashMap::new));
        this.tokens = tokens;
        this.oyentes = List.copyOf(oyentes);
    }

    public void procesar(CambioDocumento cambio) {
        ProyectorLectura proyector = proyectoresPorColeccion.get(cambio.coleccion());
        if (proyector != null) {
            proyector.aplicar(cambio);
            avisar(cambio);
        }
        tokens.guardar(FLUJO, cambio.tokenReanudacion());
    }

    /** Avisa a los oyentes de un cambio ya proyectado (también lo usa la reconstrucción de espejos). */
    public void avisar(CambioDocumento cambio) {
        for (OyenteProyeccion oyente : oyentes) {
            try {
                oyente.proyectado(cambio);
            } catch (RuntimeException e) {
                log.warn("El oyente {} falló con el cambio {} de {}", oyente.getClass().getSimpleName(),
                        cambio.id(), cambio.coleccion(), e);
            }
        }
    }

    /** Proyectores registrados, en el orden en que se recibieron. */
    public List<ProyectorLectura> proyectores() {
        return List.copyOf(proyectoresPorColeccion.values());
    }

    /**
     * Colecciones que tienen proyector. El observador filtra el Change Stream con esta lista
     * para no recibir sus propias escrituras (por ejemplo, {@code sync_tokens}).
     */
    public Set<String> colecciones() {
        return Set.copyOf(proyectoresPorColeccion.keySet());
    }
}