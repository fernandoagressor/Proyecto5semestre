package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura.ProyectorSolicitudesPqrs;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura.ProyectorTareas;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.bson.BsonDocument;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Reconstrucción de las tablas espejo desde MongoDB (rama 11). Sin transacción de prueba: el
 * observador escribe desde su propio hilo.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProyectorTareas.class, ProyectorSolicitudesPqrs.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class ReconstruccionEspejosTest {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.11"))
                    .withDatabaseName("nexora_test")
                    .withUsername("nexora_test")
                    .withPassword("nexora_test_password")
                    .withCommand("--log-bin-trust-function-creators=1");

    // MongoDB 8.0 no arranca en kernels Linux 6.19+ (SERVER-121912), como los de GitHub Actions.
    @Container
    static final MongoDBContainer MONGODB =
            new MongoDBContainer(DockerImageName.parse("mongo:8.0.32"))
                    .withReplicaSet()
                    .withEnv("GLIBC_TUNABLES", "glibc.pthread.rseq=1");

    @DynamicPropertySource
    static void conectarMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProyectorTareas proyectorTareas;
    @Autowired
    private ProyectorSolicitudesPqrs proyectorPqrs;

    private MongoClient cliente;
    private MongoTemplate mongo;
    private AlmacenTokenMongo tokens;
    private DespachadorCambios despachador;
    private ReconstructorEspejos reconstructor;
    private ObservadorCambios observador;

    @BeforeEach
    void preparar() {
        cliente = MongoClients.create(MONGODB.getReplicaSetUrl());
        mongo = new MongoTemplate(cliente, "espejos_" + System.nanoTime());
        tokens = new AlmacenTokenMongo(mongo);
        despachador = new DespachadorCambios(List.of(proyectorTareas, proyectorPqrs), tokens);
        @SuppressWarnings("unchecked")
        ObjectProvider<InvalidadorCacheDashboards> sinCache = mock(ObjectProvider.class);
        reconstructor = new ReconstructorEspejos(mongo, jdbc, despachador, sinCache);
        limpiarEspejos();

        mongo.getCollection("tareas").insertMany(List.of(
                tarea(1L, "PENDIENTE"),
                tarea(2L, "VENCIDA"),
                // Sin estado: el proyector la rechaza y la reconstrucción sigue con las demás.
                new Document("_id", 4L).append("empresaId", 8L)));
        jdbc.update("""
                INSERT INTO lectura_tareas (id, empresa_id, proyecto_id, estado)
                VALUES (2, 8, 1, 'PENDIENTE'), (3, 8, 1, 'PENDIENTE')
                """);
    }

    @AfterEach
    void cerrar() {
        if (observador != null) {
            observador.stop();
        }
        limpiarEspejos();
        mongo.getDb().drop();
        cliente.close();
    }

    @Test
    void dejaElEspejoIgualAMongoSinVaciarloAntes() {
        List<ReconstructorEspejos.Resultado> resultados = reconstructor.reconstruirTodo();

        assertThat(resultados).extracting(ReconstructorEspejos.Resultado::tabla)
                .containsExactly("lectura_tareas", "lectura_solicitudes_pqrs");
        assertThat(resultados.get(0)).satisfies(r -> {
            assertThat(r.proyectados()).isEqualTo(2);
            assertThat(r.eliminados()).as("la tarea 3 ya no existe en MongoDB").isEqualTo(1);
            assertThat(r.errores()).isEqualTo(1);
        });
        assertThat(estados()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "PENDIENTE", 2L, "VENCIDA"));
    }

    @Test
    void lasPqrsBorradasTambienSacanSuEncuestaDelEspejo() {
        jdbc.update("""
                INSERT INTO lectura_solicitudes_pqrs (id, empresa_id, proyecto_id, tipo, estado, creado_en)
                VALUES (50, 8, 1, 'PETICION', 'CERRADA', '2026-10-01 10:00:00')
                """);
        jdbc.update("""
                INSERT INTO lectura_encuestas_satisfaccion (solicitud_id, empresa_id, proyecto_id, puntuacion, respondida_en)
                VALUES (50, 8, 1, 5, '2026-10-02 10:00:00')
                """);

        reconstructor.reconstruirTodo();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lectura_encuestas_satisfaccion", Integer.class)).isZero();
    }

    @Test
    void sinTokenElObservadorReconstruyeAlAbrirElFlujoYLuegoSigueLosCambios() throws InterruptedException {
        observador = new ObservadorCambios(mongo, despachador, tokens, Duration.ofMillis(200), reconstructor, false);
        observador.start();
        esperarHasta(observador::escuchando);
        assertThat(estados()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "PENDIENTE", 2L, "VENCIDA"));

        mongo.getCollection("tareas").insertOne(tarea(5L, "EN_PROCESO"));

        esperarHasta(() -> estados().containsKey(5L));
        esperarHasta(() -> tokens.leer(DespachadorCambios.FLUJO).isPresent());
    }

    @Test
    void reconoceElHistorialPerdidoDelChangeStream() {
        MongoCommandException perdido = new MongoCommandException(
                BsonDocument.parse("{ok: 0, code: 286, errmsg: 'ChangeStreamHistoryLost'}"), new ServerAddress());
        MongoCommandException otro = new MongoCommandException(
                BsonDocument.parse("{ok: 0, code: 13, errmsg: 'Unauthorized'}"), new ServerAddress());

        assertThat(ObservadorCambios.historialPerdido(perdido)).isTrue();
        assertThat(ObservadorCambios.historialPerdido(new MongoException("envuelto", perdido))).isTrue();
        assertThat(ObservadorCambios.historialPerdido(otro)).isFalse();
        assertThat(ObservadorCambios.historialPerdido(new IllegalStateException("otra cosa"))).isFalse();
    }

    private Map<Long, String> estados() {
        Map<Long, String> estados = new java.util.HashMap<>();
        jdbc.query("SELECT id, estado FROM lectura_tareas", rs -> {
            estados.put(rs.getLong("id"), rs.getString("estado"));
        });
        return estados;
    }

    private void limpiarEspejos() {
        jdbc.update("DELETE FROM lectura_tareas");
        jdbc.update("DELETE FROM lectura_encuestas_satisfaccion");
        jdbc.update("DELETE FROM lectura_solicitudes_pqrs");
    }

    private static Document tarea(long id, String estado) {
        return new Document("_id", id).append("empresaId", 8L).append("proyectoId", 1L).append("estado", estado)
                .append("creadoEn", new Date());
    }

    private static void esperarHasta(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!condicion.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("La condición no se cumplió en 20 segundos");
            }
            Thread.sleep(100);
        }
    }
}
