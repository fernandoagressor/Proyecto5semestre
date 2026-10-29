package com.inmobiliaria.inmobiliaria_api.service.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.FuenteProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Oportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Prospecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.TemperaturaProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.EstadoSimulacion;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Simulacion;
import com.inmobiliaria.inmobiliaria_api.repository.crm.OportunidadRepository;
import com.inmobiliaria.inmobiliaria_api.repository.crm.ProspectoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.simulacion.SimulacionRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Cola {@code crm_pendientes} (rama 8): la venta escribe en MySQL y el cambio llega al CRM en
 * MongoDB. Sin transacción de prueba, porque lo que se comprueba es justamente el commit.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true, parallel = true)
class PendientesCrmTest {

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

    private static final long EMPRESA = 8L;
    private static final LocalDateTime FIRMA = LocalDateTime.parse("2026-10-29T11:00:00");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private MongoClient cliente;
    private MongoTemplate mongo;
    private OportunidadRepository oportunidades;
    private ProspectoRepository prospectos;
    private SimulacionRepository simulaciones;
    private ProcesadorPendientesCrm procesador;
    private PendientesCrmService pendientes;

    @BeforeEach
    void preparar() {
        cliente = MongoClients.create(MONGODB.getReplicaSetUrl());
        mongo = new MongoTemplate(cliente, "pendientes_" + System.nanoTime());
        SecuenciaService secuencias = new SecuenciaService(mongo);
        oportunidades = new OportunidadRepository(mongo);
        prospectos = new ProspectoRepository(mongo);
        simulaciones = new SimulacionRepository(mongo);
        procesador = new ProcesadorPendientesCrm(jdbc, transactionManager, oportunidades, prospectos, simulaciones, secuencias);
        pendientes = new PendientesCrmService(jdbc, procesador);
        jdbc.update("DELETE FROM crm_pendientes");
    }

    @AfterEach
    void cerrar() {
        jdbc.update("DELETE FROM crm_pendientes");
        mongo.getDb().drop();
        cliente.close();
    }

    @Test
    void laReservaYLaFirmaLleganAlCrmAlConfirmarse() {
        crearOportunidad(20L, 5L);
        TransactionTemplate venta = new TransactionTemplate(transactionManager);

        venta.executeWithoutResult(estado -> {
            pendientes.clienteDeReserva(EMPRESA, 20L, 40L, 9L);
            assertThat(oportunidad(20L).getClienteId()).as("antes del commit").isNull();
        });
        venta.executeWithoutResult(estado -> pendientes.ventaGanada(EMPRESA, 20L, 300L, 40L, FIRMA, 9L));

        Oportunidad ganada = oportunidad(20L);
        assertThat(ganada.getClienteId()).isEqualTo(40L);
        assertThat(ganada.getEstado()).isEqualTo(EstadoOportunidad.GANADA);
        assertThat(ganada.getFechaCierre()).isEqualTo(FIRMA);
        assertThat(ganada.getHistorialEtapas()).last()
                .satisfies(cambio -> assertThat(cambio.getObservacion()).contains("contrato 300"));
        Prospecto prospecto = prospectos.findByIdAndEmpresaId(5L, EMPRESA).orElseThrow();
        assertThat(prospecto.getEstado()).isEqualTo(EstadoProspecto.CONVERTIDO);
        assertThat(prospecto.getClienteId()).isEqualTo(40L);
        assertThat(sinProcesar()).isZero();
    }

    @Test
    void siLaVentaHaceRollbackNoQuedaPendienteNiCambio() {
        crearOportunidad(20L, 5L);
        TransactionTemplate venta = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> venta.executeWithoutResult(estado -> {
            pendientes.ventaGanada(EMPRESA, 20L, 300L, 40L, FIRMA, 9L);
            throw new IllegalStateException("La firma falló");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_pendientes", Integer.class)).isZero();
        assertThat(oportunidad(20L).getEstado()).isEqualTo(EstadoOportunidad.ABIERTA);
    }

    @Test
    void laAnulacionReabreLaOportunidadYElProspecto() {
        crearOportunidad(20L, 5L);
        pendientes.ventaGanada(EMPRESA, 20L, 300L, 40L, FIRMA, 9L);

        pendientes.ventaAnulada(EMPRESA, 20L, 300L, 9L);

        assertThat(oportunidad(20L).getEstado()).isEqualTo(EstadoOportunidad.ABIERTA);
        assertThat(prospectos.findByIdAndEmpresaId(5L, EMPRESA).orElseThrow().getEstado())
                .isEqualTo(EstadoProspecto.CALIFICADO);
    }

    @Test
    void unFalloSeReintentaYBloqueaLosSiguientesDeLaMismaOportunidad() {
        // La oportunidad 21 todavía no existe en MongoDB: los dos pendientes fallan o esperan.
        pendientes.clienteDeReserva(EMPRESA, 21L, 40L, 9L);
        pendientes.ventaGanada(EMPRESA, 21L, 301L, 40L, FIRMA, 9L);

        Map<String, Object> primero = jdbc.queryForMap(
                "SELECT intentos, ultimo_error, procesado_en FROM crm_pendientes ORDER BY id LIMIT 1");
        assertThat(((Number) primero.get("intentos")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat((String) primero.get("ultimo_error")).contains("no existe");
        assertThat(primero.get("procesado_en")).isNull();
        assertThat(sinProcesar()).isEqualTo(2);

        crearOportunidad(21L, 6L);
        procesador.procesarPendientes();

        Oportunidad oportunidad = oportunidad(21L);
        assertThat(oportunidad.getClienteId()).isEqualTo(40L);
        assertThat(oportunidad.getEstado()).isEqualTo(EstadoOportunidad.GANADA);
        assertThat(sinProcesar()).isZero();
    }

    @Test
    void aplicarDosVecesNoDuplicaElHistorial() {
        crearOportunidad(20L, 5L);
        pendientes.ventaGanada(EMPRESA, 20L, 300L, 40L, FIRMA, 9L);
        int cambios = oportunidad(20L).getHistorialEtapas().size();

        jdbc.update("UPDATE crm_pendientes SET procesado_en = NULL");
        procesador.procesarPendientes();

        assertThat(oportunidad(20L).getHistorialEtapas()).hasSize(cambios);
    }

    @Test
    void laReservaAceptaLaSimulacionAlConfirmarseYSoloUnaVez() {
        Simulacion simulacion = new Simulacion(EMPRESA, 1L, "SIM-1", 20L, 301L, 34L,
                new BigDecimal("74000000.00"), BigDecimal.ZERO, new BigDecimal("14000000.00"),
                new BigDecimal("60000000.00"), 12, new BigDecimal("0.120000"), Periodicidad.MENSUAL,
                FIRMA.plusDays(30));
        simulacion.identificar(40L);
        simulacion.enviar(FIRMA);
        simulaciones.insertar(simulacion);
        TransactionTemplate reserva = new TransactionTemplate(transactionManager);

        reserva.executeWithoutResult(estado -> {
            pendientes.simulacionAceptada(EMPRESA, 20L, 40L, 9L);
            assertThat(simulaciones.findByIdAndEmpresaId(40L, EMPRESA).orElseThrow().getEstado())
                    .as("antes del commit").isEqualTo(EstadoSimulacion.ENVIADA);
        });

        Simulacion aceptada = simulaciones.findByIdAndEmpresaId(40L, EMPRESA).orElseThrow();
        assertThat(aceptada.getEstado()).isEqualTo(EstadoSimulacion.ACEPTADA);
        assertThat(jdbc.queryForObject("SELECT simulacion_id FROM crm_pendientes", Long.class)).isEqualTo(40L);
        assertThat(sinProcesar()).isZero();

        jdbc.update("UPDATE crm_pendientes SET procesado_en = NULL");
        procesador.procesarPendientes();
        assertThat(simulaciones.findByIdAndEmpresaId(40L, EMPRESA).orElseThrow().getVersion())
                .as("reaplicar no vuelve a guardar").isEqualTo(aceptada.getVersion());
    }

    private void crearOportunidad(long id, long prospectoId) {
        Prospecto prospecto = new Prospecto(EMPRESA, 1L, 34L, "Ana Gómez", "3001234567", null, FuenteProspecto.WEB,
                null, TemperaturaProspecto.TIBIO, null);
        prospecto.identificar(prospectoId);
        prospecto.cambiarCalificacion(EstadoProspecto.CALIFICADO, TemperaturaProspecto.CALIENTE, null);
        prospectos.insertar(prospecto);
        Oportunidad oportunidad = new Oportunidad(EMPRESA, 1L, "OP-" + id, prospectoId, 34L,
                new BigDecimal("74000000.00"), null);
        oportunidad.identificar(id);
        oportunidades.insertar(oportunidad);
    }

    private Oportunidad oportunidad(long id) {
        return oportunidades.findByIdAndEmpresaId(id, EMPRESA).orElseThrow();
    }

    private int sinProcesar() {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM crm_pendientes WHERE procesado_en IS NULL", Integer.class);
        return total == null ? 0 : total;
    }
}
