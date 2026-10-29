package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import com.mongodb.client.MongoCursor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Rehace las tablas espejo {@code lectura_*} completas a partir de MongoDB.
 *
 * <p>Para cada proyector con tabla espejo: proyecta todos los documentos de su colección (upsert,
 * como un cambio normal) y luego elimina las filas cuyo documento ya no existe. No vacía la tabla
 * antes, así los dashboards nunca ven el espejo a medio llenar.</p>
 *
 * <p>Lo usa el {@link ObservadorCambios} cuando no hay token guardado, cuando MongoDB ya no tiene
 * el historial desde el último token (error 286) o a pedido con
 * {@code app.sync.reconstruir-al-arrancar=true}. Un documento que no se puede proyectar se
 * registra y la reconstrucción sigue con los demás.</p>
 */
@Component
public class ReconstructorEspejos {

    private static final Logger log = LoggerFactory.getLogger(ReconstructorEspejos.class);
    private static final int LOTE = 500;

    private final MongoTemplate mongoTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final DespachadorCambios despachador;
    private final ObjectProvider<InvalidadorCacheDashboards> invalidador;

    public ReconstructorEspejos(MongoTemplate mongoTemplate, JdbcTemplate jdbcTemplate,
                                DespachadorCambios despachador,
                                ObjectProvider<InvalidadorCacheDashboards> invalidador) {
        this.mongoTemplate = mongoTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.despachador = despachador;
        this.invalidador = invalidador;
    }

    /** Resultado de reconstruir una tabla espejo. */
    public record Resultado(String coleccion, String tabla, long proyectados, long eliminados, long errores) { }

    /** Reconstruye todas las tablas espejo y deja marcada la caché de dashboards para borrarse. */
    public List<Resultado> reconstruirTodo() {
        List<Resultado> resultados = new ArrayList<>();
        for (ProyectorLectura proyector : despachador.proyectores()) {
            if (proyector.tablaEspejo().isPresent()) {
                resultados.add(reconstruir(proyector));
            }
        }
        invalidador.ifAvailable(InvalidadorCacheDashboards::marcarTodas);
        log.info("Tablas espejo reconstruidas desde MongoDB: {}", resultados);
        return resultados;
    }

    Resultado reconstruir(ProyectorLectura proyector) {
        String coleccion = proyector.coleccion();
        String tabla = proyector.tablaEspejo().orElseThrow();
        Set<Long> vigentes = new HashSet<>();
        long proyectados = 0;
        long errores = 0;
        try (MongoCursor<Document> cursor = mongoTemplate.getCollection(coleccion).find()
                .batchSize(LOTE).iterator()) {
            while (cursor.hasNext()) {
                Document documento = cursor.next();
                if (!(documento.get("_id") instanceof Number numero)) {
                    continue;
                }
                long id = numero.longValue();
                vigentes.add(id);
                Map<String, Object> datos = new LinkedHashMap<>(documento);
                datos.remove("_id");
                if (aplicar(proyector, new CambioDocumento(coleccion, TipoCambio.ACTUALIZACION, id, datos, null))) {
                    proyectados++;
                } else {
                    errores++;
                }
            }
        }
        long eliminados = 0;
        // La tabla viene del proyector (código), no de datos externos.
        for (Long id : jdbcTemplate.queryForList("SELECT id FROM " + tabla, Long.class)) {
            if (!vigentes.contains(id)
                    && aplicar(proyector, new CambioDocumento(coleccion, TipoCambio.ELIMINACION, id, Map.of(), null))) {
                eliminados++;
            }
        }
        return new Resultado(coleccion, tabla, proyectados, eliminados, errores);
    }

    private static boolean aplicar(ProyectorLectura proyector, CambioDocumento cambio) {
        try {
            proyector.aplicar(cambio);
            return true;
        } catch (RuntimeException e) {
            log.error("No se pudo proyectar el documento {} de {} al reconstruir su espejo",
                    cambio.id(), cambio.coleccion(), e);
            return false;
        }
    }
}
