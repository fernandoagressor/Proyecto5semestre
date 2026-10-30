package com.inmobiliaria.inmobiliaria_api.repository.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Registrar un inicio de sesión no cambia la versión del usuario. Antes se guardaba la entidad
 * completa y dos inicios de sesión simultáneos del mismo usuario chocaban por bloqueo optimista
 * (el segundo respondía 409; lo encontró la prueba de carga).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UsuarioAccesoMySqlTest {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.11"))
                    .withDatabaseName("nexora_test")
                    .withUsername("nexora_test")
                    .withPassword("nexora_test_password")
                    .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void conectarMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbc.update("""
                INSERT INTO usuarios (id, empresa_id, persona_id, nombre_usuario, correo, password_hash, estado,
                                      intentos_fallidos, bloqueado_hasta, version)
                VALUES (71, 8, 1, 'concurrente', 'concurrente@ejemplo.com', 'x', 'ACTIVO',
                        3, '2030-01-01 00:00:00', 5)
                """);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    @Test
    void marcaElAccesoSinCambiarLaVersion() {
        LocalDateTime ahora = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        assertThat(usuarios.registrarAccesoExitoso(71L, ahora)).isEqualTo(1);
        assertThat(usuarios.registrarAccesoExitoso(71L, ahora.plusSeconds(1))).isEqualTo(1);

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT version, intentos_fallidos, bloqueado_hasta, ultimo_acceso_en FROM usuarios WHERE id = 71");
        assertThat(((Number) fila.get("version")).longValue()).isEqualTo(5L);
        assertThat(((Number) fila.get("intentos_fallidos")).intValue()).isZero();
        assertThat(fila.get("bloqueado_hasta")).isNull();
        assertThat(fila.get("ultimo_acceso_en")).isNotNull();
    }

    @Test
    void unaCopiaLeidaAntesDelAccesoSeSigueGuardandoSinConflicto() {
        Usuario leido = usuarios.findById(71L).orElseThrow();

        usuarios.registrarAccesoExitoso(71L, LocalDateTime.now());

        assertThatCode(() -> usuarios.saveAndFlush(leido)).doesNotThrowAnyException();
    }
}
