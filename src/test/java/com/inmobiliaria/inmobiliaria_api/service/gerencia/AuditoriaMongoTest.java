package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.AlertaAnomalia;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EventoAuditoria;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.ReporteGenerado;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.IntentoAcceso;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.AlertaAnomaliaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.EventoAuditoriaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.ExportacionAuditoriaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.ReporteGeneradoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.IntentoAccesoRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.migracion.MigradorTablaMongo;
import com.inmobiliaria.inmobiliaria_api.service.seguridad.MigradorIntentosAcceso;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/** Auditoría en MongoDB (rama 7): migración desde MySQL y repositorios. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true, parallel = true)
class AuditoriaMongoTest {

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
    private EventoAuditoriaRepository eventos;
    private AlertaAnomaliaRepository alertas;
    private ExportacionAuditoriaRepository exportaciones;
    private ReporteGeneradoRepository reportes;
    private List<MigradorTablaMongo> migradores;

    @BeforeEach
    void preparar() {
        cliente = MongoClients.create(MONGODB.getReplicaSetUrl());
        mongo = new MongoTemplate(cliente, "auditoria_" + System.nanoTime());
        secuencias = new SecuenciaService(mongo);
        eventos = new EventoAuditoriaRepository(mongo);
        alertas = new AlertaAnomaliaRepository(mongo);
        exportaciones = new ExportacionAuditoriaRepository(mongo);
        reportes = new ReporteGeneradoRepository(mongo);
        DocumentosMigradosRepository documentos = new DocumentosMigradosRepository(mongo);
        migradores = List.of(
                new MigradorEventosAuditoria(jdbc, documentos, eventos, secuencias),
                new MigradorAlertasAnomalia(jdbc, documentos, alertas, secuencias),
                new MigradorExportacionesAuditoria(jdbc, documentos, exportaciones, secuencias),
                new MigradorReportesGenerados(jdbc, documentos, reportes, secuencias),
                new MigradorIntentosAcceso(jdbc, documentos, new IntentoAccesoRepository(mongo), secuencias));
        // Las filas de prueba no tienen empresas, usuarios ni archivos reales detrás.
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
    void copiaTodoUnaSolaVez() {
        assertThat(migradores).extracting(MigradorTablaMongo::migrar).containsExactly(3, 2, 1, 1, 2);
        assertThat(migradores).extracting(MigradorTablaMongo::migrar).containsOnly(0);
        assertThat(mongo.getCollection(IntentoAcceso.COLECCION).countDocuments()).isEqualTo(2);
    }

    @Test
    void laEvidenciaDeLaBitacoraConservaElJsonTalComoEstabaEnMySql() {
        migrar();

        EventoAuditoria evento = eventos.findByIdAndEmpresaId(1L, 8L).orElseThrow();
        assertThat(evento.getModulo()).isEqualTo("VENTAS");
        // Se copia el texto tal como lo guarda MySQL (que ya normaliza el JSON: 74000000.00 → 74000000.0).
        assertThat(evento.getDatosNuevosJson())
                .isEqualTo(jdbc.queryForObject(
                        "SELECT CAST(datos_nuevos_json AS CHAR) FROM auditoria_eventos WHERE id = 1", String.class))
                .contains("VIGENTE");
        assertThat(evento.getDatosAnterioresJson()).contains("BORRADOR");
        assertThat(evento.getCorrelationId()).isEqualTo("0f8c2a8e-5a0b-4f7e-9c3d-1a2b3c4d5e6f");
        assertThat(evento.getOcurridoEn()).isEqualTo(LocalDateTime.parse("2026-10-20T10:00:00"));
        assertThat(eventos.findByIdAndEmpresaId(3L, 8L)).isEmpty();
    }

    @Test
    void losEventosSeFiltranPorEmpresaModuloYFecha() {
        migrar();
        PageRequest pagina = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "ocurridoEn"));

        assertThat(eventos.buscar(where("empresaId").is(8L), pagina).getContent())
                .extracting(EventoAuditoria::getId).containsExactly(2L, 1L);
        assertThat(eventos.buscar(where("empresaId").is(8L).and("modulo").is("VENTAS"), pagina).getContent())
                .extracting(EventoAuditoria::getId).containsExactly(1L);
        assertThat(eventos.buscar(where("empresaId").is(8L)
                        .and("ocurridoEn").gte(LocalDateTime.parse("2026-10-21T00:00:00")), pagina).getContent())
                .extracting(EventoAuditoria::getId).containsExactly(2L);
    }

    @Test
    void dosInvestigadoresSobreLaMismaAlertaProducenConflicto() {
        migrar();
        AlertaAnomalia primero = alertas.findByIdAndEmpresaId(10L, 8L).orElseThrow();
        AlertaAnomalia segundo = alertas.findByIdAndEmpresaId(10L, 8L).orElseThrow();

        primero.asignar(35L);
        assertThat(alertas.guardar(primero).getVersion()).isEqualTo(1L);
        segundo.asignar(36L);

        assertThatThrownBy(() -> alertas.guardar(segundo)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(alertas.findByIdAndEmpresaId(10L, 8L).orElseThrow().getAsignadoA()).isEqualTo(35L);
        assertThat(alertas.findByIdAndEmpresaId(11L, 8L).orElseThrow().getVersion()).isEqualTo(2L);
    }

    @Test
    void reportesYExportacionesSeConsultanYCompletan() {
        migrar();

        ReporteGenerado reporte = reportes.findByIdAndEmpresaId(20L, 8L).orElseThrow();
        reporte.completar(900L, null, LocalDateTime.parse("2026-10-29T10:00:00"));
        ReporteGenerado guardado = reportes.guardar(reporte);
        assertThat(guardado.getEstado()).isEqualTo("GENERADO");
        assertThat(guardado.getVersion()).isEqualTo(1L);
        assertThat(guardado.getFiltrosJson()).contains("2026-09");

        assertThat(exportaciones.findDelUsuario(5L, 8L, 34L)).hasValueSatisfying(exportacion -> {
            assertThat(exportacion.getFormato()).isEqualTo("CSV");
            assertThat(exportacion.getFiltrosJson()).contains("VENTAS");
        });
        assertThat(exportaciones.findDelUsuario(5L, 8L, 35L)).isEmpty();
    }

    @Test
    void lasSecuenciasQuedanPorEncimaDeLosIdsDeMySql() {
        migrar();

        assertThat(secuencias.siguiente(SecuenciasAuditoria.EVENTOS)).isEqualTo(4L);
        assertThat(secuencias.siguiente(SecuenciasAuditoria.ALERTAS)).isEqualTo(12L);
        assertThat(secuencias.siguiente(SecuenciasAuditoria.EXPORTACIONES)).isEqualTo(6L);
        assertThat(secuencias.siguiente(SecuenciasAuditoria.REPORTES)).isEqualTo(21L);
        assertThat(secuencias.siguiente("intentos_acceso")).isEqualTo(102L);
    }

    private void migrar() {
        migradores.forEach(MigradorTablaMongo::migrar);
    }

    private void sembrarMySql() {
        jdbc.update("""
                INSERT INTO auditoria_eventos (id, empresa_id, proyecto_id, actor_id, modulo, accion, entidad_tipo,
                  entidad_id, descripcion, datos_anteriores_json, datos_nuevos_json, ip_origen, agente_usuario,
                  correlation_id, ocurrido_en)
                VALUES (1, 8, 1, 34, 'VENTAS', 'CONTRATO_FIRMAR', 'CONTRATO', 15, 'Contrato firmado',
                        '{"estado": "BORRADOR"}', '{"estado": "VIGENTE", "valor": 74000000.00}', '10.0.0.1',
                        'Mozilla/5.0', '0f8c2a8e-5a0b-4f7e-9c3d-1a2b3c4d5e6f', '2026-10-20 10:00:00'),
                       (2, 8, NULL, 35, 'AUDITORIA', 'ALERTA_ASIGNAR', 'ALERTA_ANOMALIA', 10, 'Alerta asignada',
                        NULL, '35', NULL, NULL, '1f8c2a8e-5a0b-4f7e-9c3d-1a2b3c4d5e6f', '2026-10-22 09:00:00'),
                       (3, 9, NULL, 50, 'VENTAS', 'CONTRATO_FIRMAR', 'CONTRATO', 16, 'Otra empresa',
                        NULL, NULL, NULL, NULL, '2f8c2a8e-5a0b-4f7e-9c3d-1a2b3c4d5e6f', '2026-10-22 10:00:00')
                """);
        jdbc.update("""
                INSERT INTO alertas_anomalia (id, empresa_id, tipo, severidad, usuario_id, evento_auditoria_id,
                  descripcion, estado, asignado_a, resuelta_en, resolucion, version)
                VALUES (10, 8, 'ACCESO_INUSUAL', 'ALTA', 34, 1, 'Ingreso desde tres países en una hora',
                        'ABIERTA', NULL, NULL, NULL, 0),
                       (11, 8, 'EXPORTACION_MASIVA', 'CRITICA', 35, NULL, 'Exportó toda la cartera',
                        'RESUELTA', 9, '2026-10-23 12:00:00', 'Autorizado por gerencia', 2)
                """);
        jdbc.update("""
                INSERT INTO exportaciones_auditoria (id, empresa_id, usuario_id, filtros_json, formato, motivo,
                  estado, expira_en)
                VALUES (5, 8, 34, '{"modulo": "VENTAS"}', 'CSV', 'Revisión trimestral', 'PENDIENTE',
                        '2026-11-05 00:00:00')
                """);
        jdbc.update("""
                INSERT INTO reportes_generados (id, empresa_id, proyecto_id, solicitado_por, tipo_reporte,
                  filtros_json, formato, estado, expira_en, version)
                VALUES (20, 8, 1, 34, 'CARTERA', '{"periodo": "2026-09"}', 'PDF', 'PENDIENTE',
                        '2026-11-05 00:00:00', 0)
                """);
        jdbc.update("""
                INSERT INTO intentos_acceso (id, usuario_id, identificador, resultado, motivo, ip_origen,
                  agente_usuario, fecha_intento)
                VALUES (100, 34, 'ana@correo.co', 'EXITOSO', NULL, '10.0.0.1', 'Mozilla/5.0', '2026-10-28 08:00:00'),
                       (101, NULL, 'desconocido@correo.co', 'FALLIDO', 'Credenciales inválidas', '10.0.0.9',
                        'curl/8.0', '2026-10-28 08:05:00')
                """);
    }
}
