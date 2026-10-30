package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Las consultas de alcance contra el esquema real de MySQL. La de usuarios filtraba por una columna
 * {@code activo} que no existe y crear o reasignar tareas respondía 500 (lo encontró la prueba de humo).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class GerenciaAlcanceServiceMySqlTest {

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

    private static final ContextoUsuario CONTEXTO = new ContextoUsuario(1L, 8L);

    @Autowired
    private JdbcTemplate jdbc;

    private GerenciaAlcanceService service;

    @BeforeEach
    void preparar() {
        service = new GerenciaAlcanceService(mock(AlcanceProyectoService.class), jdbc);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbc.update("""
                INSERT INTO usuarios (id, empresa_id, persona_id, nombre_usuario, correo, password_hash, estado)
                VALUES (34, 8, 1, 'asesora', 'asesora@ejemplo.com', 'x', 'ACTIVO'),
                       (35, 8, 2, 'retirado', 'retirado@ejemplo.com', 'x', 'INACTIVO'),
                       (36, 9, 3, 'otra', 'otra@ejemplo.com', 'x', 'ACTIVO')
                """);
    }

    @AfterEach
    void cerrar() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    @Test
    void aceptaAlUsuarioActivoDeLaEmpresa() {
        assertThatCode(() -> service.verificarUsuarioEmpresa(CONTEXTO, 34L)).doesNotThrowAnyException();
    }

    @Test
    void rechazaAlInactivoAlDeOtraEmpresaYAlQueNoExiste() {
        for (long usuario : new long[] {35L, 36L, 99L}) {
            assertThatThrownBy(() -> service.verificarUsuarioEmpresa(CONTEXTO, usuario))
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }
}
