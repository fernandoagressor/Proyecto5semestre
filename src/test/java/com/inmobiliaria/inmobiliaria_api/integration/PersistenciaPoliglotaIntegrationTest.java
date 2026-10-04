package com.inmobiliaria.inmobiliaria_api.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.testcontainers.containers.MongoDBContainer;
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
                    .withPassword("nexora_test_password");

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
}
