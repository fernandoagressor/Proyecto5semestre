package com.inmobiliaria.inmobiliaria_api.service.simulacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.EstadoSimulacion;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Simulacion;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.SimulacionCuota;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.simulacion.SimulacionRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/** Simulaciones en MongoDB (rama 8b): migración desde MySQL, consultas y concurrencia. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true, parallel = true)
class SimulacionMongoTest {

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

    private MongoClient cliente;
    private MongoTemplate mongo;
    private SecuenciaService secuencias;
    private SimulacionRepository simulaciones;
    private MigradorSimulaciones migrador;

    @BeforeEach
    void preparar() {
        cliente = MongoClients.create(MONGODB.getReplicaSetUrl());
        mongo = new MongoTemplate(cliente, "simulaciones_" + System.nanoTime());
        secuencias = new SecuenciaService(mongo);
        simulaciones = new SimulacionRepository(mongo);
        migrador = new MigradorSimulaciones(jdbc, new DocumentosMigradosRepository(mongo), simulaciones, secuencias);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        sembrarMySql();
    }

    @AfterEach
    void cerrar() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        mongo.getDb().drop();
        cliente.close();
    }

    @Test
    void copiaLasSimulacionesConSuCronogramaUnaSolaVez() {
        assertThat(migrador.migrar()).isEqualTo(2);
        assertThat(migrador.migrar()).isZero();

        Simulacion simulacion = simulaciones.findByIdAndEmpresaId(40L, 8L).orElseThrow();
        assertThat(simulacion.getAsesorId()).as("copiado de la oportunidad").isEqualTo(34L);
        assertThat(simulacion.getEstado()).isEqualTo(EstadoSimulacion.ENVIADA);
        assertThat(simulacion.getPeriodicidad()).isEqualTo(Periodicidad.MENSUAL);
        assertThat(simulacion.getValorLote()).isEqualByComparingTo("100000000.00");
        assertThat(simulacion.getTasaEfectivaAnual()).isEqualByComparingTo("0.120000");
        assertThat(simulacion.getVigenteHasta()).isEqualTo(LocalDateTime.parse("2026-11-30T18:00:00"));
        assertThat(simulacion.getVersion()).isEqualTo(3L);
        assertThat(simulacion.getCuotas()).extracting(SimulacionCuota::getNumero).containsExactly(1, 2);
        SimulacionCuota primera = simulacion.getCuotas().get(0);
        assertThat(primera.getFechaVencimiento()).isEqualTo(LocalDate.parse("2026-12-01"));
        assertThat(primera.getValorTotal()).isEqualByComparingTo("38000000.00");
        assertThat(primera.getSimulacionId()).isEqualTo(40L);

        assertThat(simulaciones.findByIdAndEmpresaId(41L, 8L).orElseThrow().getCuotas()).isEmpty();

        Document crudo = mongo.getCollection(Simulacion.COLECCION).find(Filters.eq("_id", 40L)).first();
        assertThat(crudo.get("valorLote")).isInstanceOf(Decimal128.class);
        assertThat(crudo.getList("cuotas", Document.class).get(0).get("capital")).isInstanceOf(Decimal128.class);
    }

    @Test
    void laSecuenciaQuedaPorEncimaDeLosIdsDeMySql() {
        migrador.migrar();

        assertThat(secuencias.siguiente(SimulacionService.SECUENCIA)).isEqualTo(42L);
    }

    @Test
    void elResumenFiltraPorAsesorYNoTraeElCronograma() {
        migrador.migrar();

        var pagina = simulaciones.buscarResumen(Criteria.where("empresaId").is(8L).and("asesorId").is(34L),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "creadoEn")));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent()).singleElement().satisfies(s -> {
            assertThat(s.getId()).isEqualTo(40L);
            assertThat(s.getCuotas()).isEmpty();
        });
    }

    @Test
    void unaCopiaVencidaNoPisaLosCambiosYElCodigoEsUnicoPorEmpresa() {
        migrador.migrar();
        Simulacion primera = simulaciones.findByIdAndEmpresaId(40L, 8L).orElseThrow();
        Simulacion vieja = simulaciones.findByIdAndEmpresaId(40L, 8L).orElseThrow();

        primera.aplicarAceptacion();
        simulaciones.guardar(primera);
        vieja.cancelar();

        assertThatThrownBy(() -> simulaciones.guardar(vieja)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(simulaciones.findByIdAndEmpresaId(40L, 8L).orElseThrow().getEstado())
                .isEqualTo(EstadoSimulacion.ACEPTADA);

        Simulacion repetida = new Simulacion(8L, 1L, "SIM-A", 20L, 301L, 34L, new BigDecimal("1.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1.00"), 1, BigDecimal.ZERO, Periodicidad.MENSUAL,
                LocalDateTime.parse("2026-12-31T00:00:00"));
        repetida.identificar(secuencias.siguiente(SimulacionService.SECUENCIA));
        assertThatThrownBy(() -> simulaciones.insertar(repetida)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void sembrarMySql() {
        jdbc.update("""
                INSERT INTO oportunidades (id, empresa_id, proyecto_id, codigo, prospecto_id, asesor_id, etapa_actual,
                  estado, probabilidad_cierre, valor_estimado, version)
                VALUES (20, 8, 1, 'OP-ABC', 5, 34, 'PROPUESTA', 'ABIERTA', 70.00, 100000000.00, 0),
                       (21, 8, 1, 'OP-DEF', 6, 35, 'PROPUESTA', 'ABIERTA', 50.00, 90000000.00, 0)
                """);
        jdbc.update("""
                INSERT INTO simulaciones (id, empresa_id, proyecto_id, codigo, oportunidad_id, lote_id, creado_por,
                  valor_lote, descuento, cuota_inicial, saldo_financiar, numero_cuotas, tasa_efectiva_anual,
                  periodicidad, vigente_hasta, estado, version, creado_en)
                VALUES (40, 8, 1, 'SIM-A', 20, 301, 34, 100000000.00, 5000000.00, 20000000.00, 75000000.00, 2,
                        0.120000, 'MENSUAL', '2026-11-30 18:00:00', 'ENVIADA', 3, '2026-10-20 09:00:00'),
                       (41, 8, 1, 'SIM-B', 21, 302, 35, 90000000.00, 0.00, 10000000.00, 80000000.00, 12,
                        0.000000, 'MENSUAL', '2026-11-15 18:00:00', 'BORRADOR', 0, '2026-10-21 09:00:00')
                """);
        jdbc.update("""
                INSERT INTO simulacion_cuotas (id, simulacion_id, numero, fecha_vencimiento, capital, interes, otros,
                  valor_total, saldo_proyectado)
                VALUES (501, 40, 2, '2027-01-01', 37500000.00, 400000.00, 0.00, 37900000.00, 0.00),
                       (500, 40, 1, '2026-12-01', 37500000.00, 500000.00, 0.00, 38000000.00, 37500000.00)
                """);
    }
}
