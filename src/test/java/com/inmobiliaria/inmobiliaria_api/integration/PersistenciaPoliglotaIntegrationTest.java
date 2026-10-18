package com.inmobiliaria.inmobiliaria_api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.inmobiliaria.inmobiliaria_api.config.PersistenceRolesProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "app.bootstrap-admin.enabled=false",
        "app.security.jwt-secret=0123456789abcdef0123456789abcdef",
        "spring.task.scheduling.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true, parallel = true)
class PersistenciaPoliglotaIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.11"))
                    .withDatabaseName("nexora_test")
                    .withUsername("nexora_test")
                    .withPassword("nexora_test_password")
                    .withCommand("--log-bin-trust-function-creators=1");

    @Container
    static final MongoDBContainer MONGODB =
            new MongoDBContainer(DockerImageName.parse("mongo:8.0.32"))
                    .withReplicaSet();

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.2.10-alpine3.22"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureConnections(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PersistenceRolesProperties persistenceRoles;

    @Autowired
    private SecuenciaService secuencias;

    @Test
    void lasTresPersistenciasRespondenDesdeLaAplicacion() {
        Integer mysqlResult = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        double mongoResult = mongoTemplate.executeCommand("{ ping: 1 }")
                .getDouble("ok");

        String redisKey = "nexora:test:infraestructura";
        redisTemplate.opsForValue().set(redisKey, "ok", Duration.ofSeconds(30));
        String redisResult = redisTemplate.opsForValue().get(redisKey);
        redisTemplate.delete(redisKey);

        assertThat(mysqlResult).isEqualTo(1);
        assertThat(mongoResult).isEqualTo(1.0d);
        assertThat(redisResult).isEqualTo("ok");
    }

    @Test
    void conservaLasResponsabilidadesDeCadaTecnologia() {
        assertThat(persistenceRoles.primaryStore())
                .isEqualTo(PersistenceRolesProperties.Store.MONGODB);
        assertThat(persistenceRoles.transactionalStore())
                .isEqualTo(PersistenceRolesProperties.Store.MYSQL);
        assertThat(persistenceRoles.ephemeralStore())
                .isEqualTo(PersistenceRolesProperties.Store.REDIS);
    }
    @Test
    void lasSecuenciasEntreganNumerosConsecutivosSinRepetir() throws Exception {
        String nombre = "prueba-" + System.nanoTime();

        // 1. Empieza en 1 y avanza de uno en uno
        assertThat(secuencias.siguiente(nombre)).isEqualTo(1L);
        assertThat(secuencias.siguiente(nombre)).isEqualTo(2L);

        // 2. asegurarMinimo salta adelante (migración de datos existentes)
        secuencias.asegurarMinimo(nombre, 100L);
        assertThat(secuencias.siguiente(nombre)).isEqualTo(101L);

        // 3. asegurarMinimo nunca retrocede
        secuencias.asegurarMinimo(nombre, 50L);
        assertThat(secuencias.siguiente(nombre)).isEqualTo(102L);

        // 4. 200 peticiones simultáneas → 200 números distintos
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Long>> resultados = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                resultados.add(pool.submit(() -> secuencias.siguiente(nombre)));
            }
            Set<Long> numeros = new HashSet<>();
            for (Future<Long> resultado : resultados) {
                numeros.add(resultado.get());
            }
            assertThat(numeros).hasSize(200);
        } finally {
            pool.shutdown();
        }
    }
}
