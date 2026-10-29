package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import com.mongodb.MongoServerException;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.OperationType;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Escucha el Change Stream de la base de MongoDB y entrega cada cambio al {@link DespachadorCambios}.
 *
 * <ul>
 *   <li>Solo recibe las colecciones que tienen proyector, así no ve sus propias escrituras
 *       en {@code sync_tokens}.</li>
 *   <li>Al arrancar reanuda desde el último token guardado; sin token empieza desde ahora.</li>
 *   <li>Pide el documento completo en las actualizaciones ({@code UPDATE_LOOKUP}) para que los
 *       proyectores reciban el estado actual y no solo los campos cambiados.</li>
 *   <li>Si el flujo falla (MongoDB caído, proyector con error), espera y vuelve a abrirlo desde el
 *       último token: ningún cambio se pierde, alguno puede repetirse.</li>
 *   <li>Si MongoDB ya no tiene el historial desde el último token (error 286, por ejemplo tras
 *       mucho tiempo apagado), olvida el token, abre el flujo desde ahora y reconstruye las
 *       tablas espejo completas con {@link ReconstructorEspejos}. Lo mismo hace cuando no hay
 *       token guardado o con {@code app.sync.reconstruir-al-arrancar=true}. La reconstrucción
 *       corre con el flujo ya abierto, así los cambios que ocurran mientras tanto se aplican
 *       después y nada queda fuera.</li>
 * </ul>
 *
 * <p>Se activa con {@code app.sync.enabled=true}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.sync", name = "enabled", havingValue = "true")
public class ObservadorCambios implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ObservadorCambios.class);
    private static final long ESPERA_POR_LOTE_MS = 500;
    /** Solo estos eventos son de datos; drop, rename o invalidate únicamente avanzan el token. */
    private static final Set<OperationType> OPERACIONES_DE_DATOS = EnumSet.of(
            OperationType.INSERT, OperationType.UPDATE, OperationType.REPLACE, OperationType.DELETE);

    private final TraductorCambios traductor = new TraductorCambios();

    private final MongoTemplate mongoTemplate;
    private final DespachadorCambios despachador;
    private final AlmacenTokenReanudacion tokens;
    private final Duration esperaReintento;
    private final ReconstructorEspejos reconstructor;

    private volatile boolean reconstruir;
    private volatile boolean activo;
    private volatile boolean escuchando;
    private Thread hilo;

    /** Sin reconstrucción de espejos (pruebas del flujo). */
    public ObservadorCambios(MongoTemplate mongoTemplate,
                             DespachadorCambios despachador,
                             AlmacenTokenReanudacion tokens,
                             Duration esperaReintento) {
        this(mongoTemplate, despachador, tokens, esperaReintento, null, false);
    }

    @Autowired
    public ObservadorCambios(MongoTemplate mongoTemplate,
                             DespachadorCambios despachador,
                             AlmacenTokenReanudacion tokens,
                             @Value("${app.sync.espera-reintento:PT5S}") Duration esperaReintento,
                             ReconstructorEspejos reconstructor,
                             @Value("${app.sync.reconstruir-al-arrancar:false}") boolean reconstruirAlArrancar) {
        this.mongoTemplate = mongoTemplate;
        this.despachador = despachador;
        this.tokens = tokens;
        this.esperaReintento = esperaReintento;
        this.reconstructor = reconstructor;
        this.reconstruir = reconstruirAlArrancar;
    }

    @Override
    public synchronized void start() {
        if (activo) {
            return;
        }
        activo = true;
        hilo = Thread.ofPlatform().name("nexora-sync").daemon(true).start(this::ejecutar);
    }

    @Override
    public void stop() {
        activo = false;
        Thread actual = hilo;
        if (actual == null) {
            return;
        }
        try {
            actual.join(esperaReintento.toMillis() + 5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return activo;
    }

    /** Verdadero cuando el Change Stream ya está abierto y recibiendo cambios. */
    boolean escuchando() {
        return escuchando;
    }

    private void ejecutar() {
        while (activo) {
            try {
                escuchar();
            } catch (RuntimeException e) {
                escuchando = false;
                if (!activo) {
                    break;
                }
                if (historialPerdido(e)) {
                    log.error("MongoDB ya no tiene el historial desde el último token; se olvida el token y se "
                            + "reconstruyen las tablas espejo", e);
                    tokens.borrar(DespachadorCambios.FLUJO);
                    reconstruir = true;
                    continue;
                }
                log.warn("El Change Stream se detuvo; se reabre en {} desde el último token", esperaReintento, e);
                dormir();
            }
        }
        escuchando = false;
    }

    private void escuchar() {
        List<String> colecciones = List.copyOf(despachador.colecciones());
        if (colecciones.isEmpty()) {
            log.info("No hay proyectores de lectura registrados; el observador de cambios no se inicia");
            activo = false;
            return;
        }
        ChangeStreamIterable<Document> flujo = mongoTemplate.getDb()
                .watch(List.of(Aggregates.match(Filters.in("ns.coll", colecciones))))
                .fullDocument(FullDocument.UPDATE_LOOKUP)
                .maxAwaitTime(ESPERA_POR_LOTE_MS, TimeUnit.MILLISECONDS);
        Optional<String> token = tokens.leer(DespachadorCambios.FLUJO);
        token.ifPresent(valor -> flujo.resumeAfter(BsonDocument.parse(valor)));
        if (token.isEmpty() && reconstructor != null) {
            reconstruir = true;
        }

        try (MongoCursor<ChangeStreamDocument<Document>> cursor = flujo.iterator()) {
            if (reconstruir && reconstructor != null) {
                // El flujo ya está abierto: lo que cambie durante la reconstrucción llega después.
                reconstructor.reconstruirTodo();
                reconstruir = false;
            }
            escuchando = true;
            log.info("Observando cambios de {}", colecciones);
            while (activo) {
                ChangeStreamDocument<Document> evento = cursor.tryNext();
                if (evento != null) {
                    procesar(evento);
                }
            }
        } finally {
            escuchando = false;
        }
    }

    private void procesar(ChangeStreamDocument<Document> evento) {
        if (!OPERACIONES_DE_DATOS.contains(evento.getOperationType())
                || evento.getNamespace() == null
                || evento.getDocumentKey() == null) {
            tokens.guardar(DespachadorCambios.FLUJO, evento.getResumeToken().toJson());
            return;
        }
        despachador.procesar(traductor.traducir(
                evento.getNamespace().getCollectionName(),
                evento.getOperationType(),
                evento.getDocumentKey(),
                evento.getFullDocument(),
                evento.getResumeToken()));
    }

    /**
     * Error 286 (ChangeStreamHistoryLost): el oplog ya no tiene la posición del token. Las
     * versiones anteriores a 4.2 respondían 280 (ChangeStreamFatalError) en el mismo caso.
     */
    static boolean historialPerdido(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (actual instanceof MongoServerException servidor
                    && (servidor.getCode() == 286 || servidor.getCode() == 280)) {
                return true;
            }
        }
        return false;
    }

    private void dormir() {
        try {
            Thread.sleep(esperaReintento.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            activo = false;
        }
    }
}
