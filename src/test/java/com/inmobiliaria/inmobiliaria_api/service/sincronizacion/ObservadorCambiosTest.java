package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class ObservadorCambiosTest {

    // MongoDB 8.0 no arranca en kernels Linux 6.19+ (SERVER-121912), como los de GitHub Actions.
    @Container
    static final MongoDBContainer MONGODB =
            new MongoDBContainer(DockerImageName.parse("mongo:8.0.32"))
                    .withReplicaSet()
                    .withEnv("GLIBC_TUNABLES", "glibc.pthread.rseq=1");

    private final List<CambioDocumento> recibidos = new CopyOnWriteArrayList<>();
    private MongoClient cliente;
    private MongoTemplate mongoTemplate;
    private AlmacenTokenMongo tokens;
    private ObservadorCambios observador;

    @BeforeEach
    void abrirUnaBaseNueva() {
        cliente = MongoClients.create(MONGODB.getReplicaSetUrl());
        mongoTemplate = new MongoTemplate(cliente, "sync_" + System.nanoTime());
        tokens = new AlmacenTokenMongo(mongoTemplate);
    }

    @AfterEach
    void cerrar() {
        if (observador != null) {
            observador.stop();
        }
        mongoTemplate.getDb().drop();
        cliente.close();
    }

    @Test
    void entregaLaInsercionAlProyectorYGuardaElToken() throws InterruptedException {
        observador = iniciarObservador();

        mongoTemplate.getCollection("tareas").insertOne(new Document("_id", 7L).append("estado", "PENDIENTE"));

        esperarHasta(() -> recibidos.size() == 1);
        CambioDocumento cambio = recibidos.get(0);
        assertThat(cambio.coleccion()).isEqualTo("tareas");
        assertThat(cambio.tipo()).isEqualTo(TipoCambio.INSERCION);
        assertThat(cambio.id()).isEqualTo(7L);
        assertThat(cambio.datos()).containsEntry("estado", "PENDIENTE");
        esperarHasta(() -> tokens.leer(DespachadorCambios.FLUJO).isPresent());
    }

    @Test
    void lasActualizacionesTraenElDocumentoCompleto() throws InterruptedException {
        mongoTemplate.getCollection("tareas").insertOne(
                new Document("_id", 8L).append("estado", "PENDIENTE").append("empresaId", 3L));
        observador = iniciarObservador();

        mongoTemplate.getCollection("tareas").updateOne(
                new Document("_id", 8L), new Document("$set", new Document("estado", "VENCIDA")));

        esperarHasta(() -> recibidos.size() == 1);
        assertThat(recibidos.get(0).tipo()).isEqualTo(TipoCambio.ACTUALIZACION);
        assertThat(recibidos.get(0).datos())
                .containsEntry("estado", "VENCIDA")
                .containsEntry("empresaId", 3L);
    }

    @Test
    void noRecibeColeccionesSinProyector() throws InterruptedException {
        observador = iniciarObservador();

        mongoTemplate.getCollection("contadores").insertOne(new Document("_id", "tareas").append("valor", 1L));
        mongoTemplate.getCollection("tareas").insertOne(new Document("_id", 9L));

        esperarHasta(() -> recibidos.size() == 1);
        Thread.sleep(500);
        assertThat(recibidos).extracting(CambioDocumento::coleccion).containsExactly("tareas");
    }

    @Test
    void alReiniciarseRecibeLoQueCambioMientrasEstabaDetenido() throws InterruptedException {
        observador = iniciarObservador();
        mongoTemplate.getCollection("tareas").insertOne(new Document("_id", 1L));
        esperarHasta(() -> recibidos.size() == 1);
        esperarHasta(() -> tokens.leer(DespachadorCambios.FLUJO).isPresent());
        observador.stop();

        mongoTemplate.getCollection("tareas").insertOne(new Document("_id", 2L));
        observador = nuevoObservador();
        observador.start();

        esperarHasta(() -> recibidos.size() == 2);
        Thread.sleep(500);
        assertThat(recibidos).extracting(CambioDocumento::id).containsExactly(1L, 2L);
    }

    private ObservadorCambios iniciarObservador() throws InterruptedException {
        ObservadorCambios nuevo = nuevoObservador();
        nuevo.start();
        esperarHasta(nuevo::escuchando);
        return nuevo;
    }

    private ObservadorCambios nuevoObservador() {
        ProyectorLectura proyectorTareas = new ProyectorLectura() {
            @Override
            public String coleccion() {
                return "tareas";
            }

            @Override
            public void aplicar(CambioDocumento cambio) {
                recibidos.add(cambio);
            }
        };
        DespachadorCambios despachador = new DespachadorCambios(List.of(proyectorTareas), tokens);
        return new ObservadorCambios(mongoTemplate, despachador, tokens, Duration.ofMillis(200));
    }

    private static void esperarHasta(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!condicion.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("La condición no se cumplió en 20 segundos");
            }
            Thread.sleep(50);
        }
    }
}
