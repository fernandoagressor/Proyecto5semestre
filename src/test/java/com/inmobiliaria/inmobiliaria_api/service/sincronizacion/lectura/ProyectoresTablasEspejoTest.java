package com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.CambioDocumento;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.TipoCambio;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        ProyectorSolicitudesPqrs.class,
        ProyectorCitasServicio.class,
        ProyectorTicketsSoporte.class,
        ProyectorTareas.class,
        ProyectorOportunidades.class
})
@Testcontainers(disabledWithoutDocker = true)
class ProyectoresTablasEspejoTest {

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

    private static final Instant CREADA = Instant.parse("2026-10-22T14:30:00Z");
    private static final Instant LIMITE = Instant.parse("2026-10-28T13:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProyectorSolicitudesPqrs pqrs;
    @Autowired
    private ProyectorCitasServicio citas;
    @Autowired
    private ProyectorTicketsSoporte tickets;
    @Autowired
    private ProyectorTareas tareas;
    @Autowired
    private ProyectorOportunidades oportunidades;

    @Test
    void insertarYLuegoActualizarDejaUnaSolaFilaConElEstadoNuevo() {
        pqrs.aplicar(cambio("solicitudes_pqrs", TipoCambio.INSERCION, 312L, solicitud("ABIERTA")));
        pqrs.aplicar(cambio("solicitudes_pqrs", TipoCambio.ACTUALIZACION, 312L, solicitud("EN_PROCESO")));

        assertThat(contar("lectura_solicitudes_pqrs")).isEqualTo(1);
        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM lectura_solicitudes_pqrs WHERE id = 312");
        assertThat(fila)
                .containsEntry("estado", "EN_PROCESO")
                .containsEntry("tipo", "QUEJA");
        assertThat(((Number) fila.get("empresa_id")).longValue()).isEqualTo(8L);
        assertThat(((Timestamp) fila.get("creado_en")).toInstant()).isEqualTo(CREADA);
        assertThat(((Timestamp) fila.get("fecha_limite")).toInstant()).isEqualTo(LIMITE);
        assertThat(fila.get("fecha_primera_respuesta")).isNull();
    }

    @Test
    void aplicarDosVecesElMismoCambioDejaElMismoResultado() {
        CambioDocumento cambio = cambio("solicitudes_pqrs", TipoCambio.INSERCION, 313L, solicitud("ABIERTA"));

        pqrs.aplicar(cambio);
        pqrs.aplicar(cambio);

        assertThat(contar("lectura_solicitudes_pqrs")).isEqualTo(1);
    }

    @Test
    void laEncuestaEmbebidaVaASuTablaYSeBorraSiDesaparece() {
        Document conEncuesta = solicitud("CERRADA").append("encuesta",
                new Document("puntuacion", 5).append("respondidaEn", Date.from(LIMITE)));

        pqrs.aplicar(cambio("solicitudes_pqrs", TipoCambio.ACTUALIZACION, 314L, conEncuesta));

        Map<String, Object> encuesta = jdbc.queryForMap(
                "SELECT * FROM lectura_encuestas_satisfaccion WHERE solicitud_id = 314");
        assertThat(((Number) encuesta.get("puntuacion")).intValue()).isEqualTo(5);
        assertThat(((Number) encuesta.get("proyecto_id")).longValue()).isEqualTo(1L);

        pqrs.aplicar(cambio("solicitudes_pqrs", TipoCambio.ACTUALIZACION, 314L, solicitud("CERRADA")));

        assertThat(contar("lectura_encuestas_satisfaccion")).isZero();
        assertThat(contar("lectura_solicitudes_pqrs")).isEqualTo(1);
    }

    @Test
    void eliminarBorraLaFilaYSuEncuesta() {
        Document conEncuesta = solicitud("CERRADA").append("encuesta",
                new Document("puntuacion", 4).append("respondidaEn", Date.from(LIMITE)));
        pqrs.aplicar(cambio("solicitudes_pqrs", TipoCambio.INSERCION, 315L, conEncuesta));

        pqrs.aplicar(cambio("solicitudes_pqrs", TipoCambio.ELIMINACION, 315L, new Document()));

        assertThat(contar("lectura_solicitudes_pqrs")).isZero();
        assertThat(contar("lectura_encuestas_satisfaccion")).isZero();
    }

    @Test
    void unaActualizacionSinDatosSeTrataComoBorrado() {
        tareas.aplicar(cambio("tareas", TipoCambio.INSERCION, 50L,
                new Document("empresaId", 8L).append("estado", "PENDIENTE")));

        tareas.aplicar(cambio("tareas", TipoCambio.ACTUALIZACION, 50L, new Document()));

        assertThat(contar("lectura_tareas")).isZero();
    }

    @Test
    void unDocumentoSinIdNumericoSeIgnora() {
        tareas.aplicar(cambio("tareas", TipoCambio.INSERCION, null,
                new Document("empresaId", 8L).append("estado", "PENDIENTE")));

        assertThat(contar("lectura_tareas")).isZero();
    }

    @Test
    void lasOportunidadesGuardanLosPesosSinPerderPrecision() {
        Document oportunidad = new Document("empresaId", 8L)
                .append("proyectoId", 3L)
                .append("asesorId", 34L)
                .append("etapaActual", "NEGOCIACION")
                .append("estado", "ABIERTA")
                .append("probabilidadCierre", new Decimal128(new BigDecimal("70.00")))
                .append("valorEstimado", new Decimal128(new BigDecimal("74000000.00")))
                .append("creadoEn", Date.from(CREADA));

        oportunidades.aplicar(cambio("oportunidades", TipoCambio.INSERCION, 412L, oportunidad));

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM lectura_oportunidades WHERE id = 412");
        assertThat((BigDecimal) fila.get("valor_estimado")).isEqualByComparingTo("74000000.00");
        assertThat((BigDecimal) fila.get("probabilidad_cierre")).isEqualByComparingTo("70");
        assertThat(fila.get("fecha_cierre")).isNull();
    }

    @Test
    void citasYTicketsSeProyectanASusTablas() {
        citas.aplicar(cambio("citas_servicio", TipoCambio.INSERCION, 21L, new Document("empresaId", 8L)
                .append("proyectoId", 1L).append("estado", "PROGRAMADA").append("fechaInicio", Date.from(LIMITE))));
        tickets.aplicar(cambio("tickets_soporte", TipoCambio.INSERCION, 81L,
                new Document("estado", "ABIERTO")));

        assertThat(jdbc.queryForObject("SELECT estado FROM lectura_citas_servicio WHERE id = 21", String.class))
                .isEqualTo("PROGRAMADA");
        assertThat(jdbc.queryForMap("SELECT * FROM lectura_tickets_soporte WHERE id = 81").get("empresa_id"))
                .isNull();
    }

    @Test
    void unCampoObligatorioAusenteFallaNombrandoElCampoYElDocumento() {
        Document sinEmpresa = new Document("estado", "PENDIENTE");

        assertThatThrownBy(() -> tareas.aplicar(cambio("tareas", TipoCambio.INSERCION, 51L, sinEmpresa)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("51")
                .hasMessageContaining("tareas")
                .hasMessageContaining("empresaId");
        assertThat(contar("lectura_tareas")).isZero();
    }

    private static Document solicitud(String estado) {
        return new Document("empresaId", 8L)
                .append("proyectoId", 1L)
                .append("tipo", "QUEJA")
                .append("estado", estado)
                .append("fechaLimite", Date.from(LIMITE))
                .append("creadoEn", Date.from(CREADA));
    }

    private static CambioDocumento cambio(String coleccion, TipoCambio tipo, Long id, Document datos) {
        return new CambioDocumento(coleccion, tipo, id, datos, "{\"_data\":\"token\"}");
    }

    private int contar(String tabla) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
        return total == null ? 0 : total;
    }
}
