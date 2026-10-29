package com.inmobiliaria.inmobiliaria_api.service.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.inmobiliaria.inmobiliaria_api.entity.crm.ActividadComercial;
import com.inmobiliaria.inmobiliaria_api.entity.crm.CitaComercial;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EtapaOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Oportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.OportunidadEtapaHistorial;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Prospecto;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.crm.CitaComercialRepository;
import com.inmobiliaria.inmobiliaria_api.repository.crm.OportunidadRepository;
import com.inmobiliaria.inmobiliaria_api.repository.crm.ProspectoRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.CambioDocumento;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.TipoCambio;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura.ProyectorOportunidades;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
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

/** CRM en MongoDB (rama 8): migración desde MySQL, consultas y contrato con el proyector. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProyectorOportunidades.class)
@Testcontainers(disabledWithoutDocker = true, parallel = true)
class CrmMongoTest {

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
    private ProyectorOportunidades proyector;

    private MongoClient cliente;
    private MongoTemplate mongo;
    private SecuenciaService secuencias;
    private ProspectoRepository prospectos;
    private OportunidadRepository oportunidades;
    private CitaComercialRepository citas;
    private MigradorProspectos migradorProspectos;
    private MigradorOportunidades migradorOportunidades;
    private MigradorCitasComerciales migradorCitas;

    @BeforeEach
    void preparar() {
        cliente = MongoClients.create(MONGODB.getReplicaSetUrl());
        mongo = new MongoTemplate(cliente, "crm_" + System.nanoTime());
        secuencias = new SecuenciaService(mongo);
        prospectos = new ProspectoRepository(mongo);
        oportunidades = new OportunidadRepository(mongo);
        citas = new CitaComercialRepository(mongo);
        DocumentosMigradosRepository documentos = new DocumentosMigradosRepository(mongo);
        migradorProspectos = new MigradorProspectos(jdbc, documentos, prospectos, secuencias);
        migradorOportunidades = new MigradorOportunidades(jdbc, documentos, oportunidades, secuencias);
        migradorCitas = new MigradorCitasComerciales(jdbc, documentos, citas, secuencias);
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
    void copiaElCrmConSusPartesEmbebidasUnaSolaVez() {
        assertThat(migradorProspectos.migrar()).isEqualTo(2);
        assertThat(migradorOportunidades.migrar()).isEqualTo(1);
        assertThat(migradorCitas.migrar()).isEqualTo(1);
        assertThat(List.of(migradorProspectos.migrar(), migradorOportunidades.migrar(), migradorCitas.migrar()))
                .containsOnly(0);

        Prospecto prospecto = prospectos.findByIdAndEmpresaId(5L, 8L).orElseThrow();
        assertThat(prospecto.getPresupuesto()).isEqualByComparingTo("150000000.00");
        assertThat(prospecto.getVersion()).isEqualTo(2L);

        Oportunidad oportunidad = oportunidades.findByIdAndEmpresaId(20L, 8L).orElseThrow();
        assertThat(oportunidad.getEtapaActual()).isEqualTo(EtapaOportunidad.PROPUESTA);
        assertThat(oportunidad.getValorEstimado()).isEqualByComparingTo("74000000.00");
        assertThat(oportunidad.getProbabilidadCierre()).isEqualByComparingTo("70");
        assertThat(oportunidad.getLotesInteres()).extracting(lote -> lote.getLoteId()).containsExactly(301L, 302L);
        assertThat(oportunidad.buscarLote(302L).orElseThrow().isSeleccionado()).isTrue();
        assertThat(oportunidad.getHistorialEtapas()).extracting(OportunidadEtapaHistorial::getEtapaNueva)
                .containsExactly(EtapaOportunidad.PROSPECTO_RECIBIDO, EtapaOportunidad.PROPUESTA);
        assertThat(oportunidad.getActividades()).extracting(ActividadComercial::getId).containsExactly(71L, 70L);

        Document crudo = mongo.getCollection(Oportunidad.COLECCION).find(Filters.eq("_id", 20L)).first();
        assertThat(crudo.get("valorEstimado")).isInstanceOf(Decimal128.class);
        assertThat(crudo.getList("presentaciones", Document.class)).singleElement()
                .satisfies(p -> assertThat(p.getString("nivelInteres")).isEqualTo("ALTO"));

        CitaComercial cita = citas.findByIdAndOportunidadId(90L, 20L).orElseThrow();
        assertThat(cita.getEmpresaId()).isEqualTo(8L);
        assertThat(cita.getProyectoId()).isEqualTo(1L);
    }

    @Test
    void lasSecuenciasQuedanPorEncimaDeLosIdsDeMySql() {
        migradorProspectos.migrar();
        migradorOportunidades.migrar();
        migradorCitas.migrar();

        assertThat(secuencias.siguiente(SecuenciasCrm.PROSPECTOS)).isEqualTo(7L);
        assertThat(secuencias.siguiente(SecuenciasCrm.OPORTUNIDADES)).isEqualTo(21L);
        assertThat(secuencias.siguiente(SecuenciasCrm.LOTES_INTERES)).isEqualTo(52L);
        assertThat(secuencias.siguiente(SecuenciasCrm.HISTORIAL_ETAPAS)).isEqualTo(62L);
        assertThat(secuencias.siguiente(SecuenciasCrm.ACTIVIDADES)).isEqualTo(72L);
        assertThat(secuencias.siguiente(SecuenciasCrm.CITAS)).isEqualTo(91L);
    }

    @Test
    void elDuplicadoDeContactoNoDistingueMayusculasEnElCorreo() {
        migradorProspectos.migrar();

        assertThat(prospectos.contarContactosActivos(8L, "ana@correo.co", null, null)).isEqualTo(1);
        assertThat(prospectos.contarContactosActivos(8L, null, "3001234567", null)).isEqualTo(1);
        assertThat(prospectos.contarContactosActivos(8L, "ana@correo.co", null, 5L)).isZero();
        // El prospecto 6 está descartado: no cuenta como duplicado.
        assertThat(prospectos.contarContactosActivos(8L, null, "3110000000", null)).isZero();
    }

    @Test
    void laAgendaFiltraPorRangoYAsesorYDetectaCruces() {
        migradorProspectos.migrar();
        migradorOportunidades.migrar();
        migradorCitas.migrar();
        LocalDateTime inicio = LocalDateTime.parse("2026-11-03T10:00:00");

        assertThat(citas.contarCrucesAgenda(34L, inicio.plusMinutes(30), inicio.plusHours(2), null)).isEqualTo(1);
        assertThat(citas.contarCrucesAgenda(34L, inicio.plusMinutes(30), inicio.plusHours(2), 90L)).isZero();
        assertThat(citas.contarCrucesAgenda(34L, inicio.plusHours(1), inicio.plusHours(2), null)).isZero();
        assertThat(citas.buscar(Criteria.where("empresaId").is(8L).and("fechaInicio").lt(inicio.plusDays(1))
                .and("fechaFin").gt(inicio.minusDays(1)), PageRequest.of(0, 10, Sort.by("fechaInicio")))
                .getContent()).extracting(CitaComercial::getId).containsExactly(90L);
    }

    @Test
    void lasOportunidadesCumplenElContratoDelProyectorDeLectura() {
        migradorOportunidades.migrar();
        Oportunidad nueva = new Oportunidad(8L, 1L, "OP-NUEVA", 5L, 34L, new BigDecimal("98500000.50"), null);
        nueva.identificar(21L);
        oportunidades.insertar(nueva);

        proyectar(20L);
        proyectar(21L);

        Map<String, Object> migrada = jdbc.queryForMap(
                "SELECT estado, etapa_actual, valor_estimado, probabilidad_cierre FROM lectura_oportunidades WHERE id = 20");
        assertThat(migrada).containsEntry("estado", EstadoOportunidad.ABIERTA.name())
                .containsEntry("etapa_actual", "PROPUESTA");
        assertThat((BigDecimal) migrada.get("valor_estimado")).isEqualByComparingTo("74000000.00");
        assertThat(jdbc.queryForObject("SELECT valor_estimado FROM lectura_oportunidades WHERE id = 21", BigDecimal.class))
                .isEqualByComparingTo("98500000.50");
    }

    private void proyectar(Long id) {
        Document crudo = mongo.getCollection(Oportunidad.COLECCION).find(Filters.eq("_id", id)).first();
        assertThat(crudo).as("oportunidad %s en MongoDB", id).isNotNull();
        Map<String, Object> datos = new LinkedHashMap<>(crudo);
        datos.remove("_id");
        proyector.aplicar(new CambioDocumento(Oportunidad.COLECCION, TipoCambio.INSERCION, id, datos,
                "{\"_data\":\"prueba\"}"));
    }

    private void sembrarMySql() {
        jdbc.update("""
                INSERT INTO prospectos (id, empresa_id, proyecto_id, asesor_id, nombres, telefono, correo, fuente,
                  presupuesto, temperatura, estado, version)
                VALUES (5, 8, 1, 34, 'Ana Gómez', '3001234567', 'Ana@Correo.co', 'WEB', 150000000.00, 'CALIENTE',
                        'CALIFICADO', 2),
                       (6, 8, 1, 34, 'Luis Pardo', '3110000000', NULL, 'REFERIDO', NULL, 'FRIO', 'DESCARTADO', 0)
                """);
        jdbc.update("""
                INSERT INTO oportunidades (id, empresa_id, proyecto_id, codigo, prospecto_id, asesor_id, etapa_actual,
                  estado, probabilidad_cierre, valor_estimado, fecha_ultima_actividad, version)
                VALUES (20, 8, 1, 'OP-ABC', 5, 34, 'PROPUESTA', 'ABIERTA', 70.00, 74000000.00,
                        '2026-10-25 15:00:00', 4)
                """);
        jdbc.update("""
                INSERT INTO oportunidad_lotes (id, oportunidad_id, lote_id, prioridad, seleccionado)
                VALUES (50, 20, 301, 1, 0), (51, 20, 302, 2, 1)
                """);
        jdbc.update("""
                INSERT INTO oportunidad_etapa_historial (id, oportunidad_id, etapa_anterior, etapa_nueva, usuario_id,
                  observacion, creado_en)
                VALUES (60, 20, NULL, 'PROSPECTO_RECIBIDO', 34, 'Creación', '2026-10-20 09:00:00'),
                       (61, 20, 'PROSPECTO_RECIBIDO', 'PROPUESTA', 34, 'Propuesta enviada', '2026-10-24 09:00:00')
                """);
        jdbc.update("""
                INSERT INTO actividades_comerciales (id, oportunidad_id, asesor_id, tipo, resultado, descripcion,
                  fecha_actividad)
                VALUES (70, 20, 34, 'LLAMADA', 'Interesada', 'Primera llamada', '2026-10-21 10:00:00'),
                       (71, 20, 34, 'VISITA', 'Muy interesada', 'Visitó el lote 302', '2026-10-25 15:00:00')
                """);
        jdbc.update("""
                INSERT INTO presentaciones_comerciales (id, oportunidad_id, proyecto_id, asesor_id, fecha_presentacion,
                  resultado, nivel_interes, materiales_json)
                VALUES (80, 20, 1, 34, '2026-10-22 11:00:00', 'Pidió propuesta', 'ALTO', '["brochure.pdf"]')
                """);
        jdbc.update("""
                INSERT INTO citas_comerciales (id, oportunidad_id, asesor_id, tipo, fecha_inicio, fecha_fin, lugar, estado)
                VALUES (90, 20, 34, 'VISITA', '2026-11-03 10:00:00', '2026-11-03 11:00:00', 'Sala de ventas', 'PROGRAMADA')
                """);
    }
}
