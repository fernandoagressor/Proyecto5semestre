package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import static org.assertj.core.api.Assertions.assertThat;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.AlertaAnomalia;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EventoAuditoria;
import com.inmobiliaria.inmobiliaria_api.entity.notificacion.Notificacion;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.IntentoAcceso;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.AlertaAnomaliaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.EventoAuditoriaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.ReporteGeneradoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.notificacion.NotificacionRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.IntentoAccesoRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.notificacion.NotificacionService;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/** Reglas de alertas de anomalía y retención de 90 días (rama 7). */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true, parallel = true)
class DetectorAnomaliasTest {

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
    private static final long USUARIO = 34L;
    private static final long GESTOR = 9L;
    private static final long GESTOR_INACTIVO = 10L;

    @Autowired
    private JdbcTemplate jdbc;

    private MongoClient cliente;
    private MongoTemplate mongo;
    private SecuenciaService secuencias;
    private IntentoAccesoRepository intentos;
    private EventoAuditoriaRepository eventos;
    private DetectorAnomaliasService detector;

    @BeforeEach
    void preparar() {
        cliente = MongoClients.create(MONGODB.getReplicaSetUrl());
        mongo = new MongoTemplate(cliente, "alertas_" + System.nanoTime());
        secuencias = new SecuenciaService(mongo);
        intentos = new IntentoAccesoRepository(mongo);
        eventos = new EventoAuditoriaRepository(mongo);
        detector = new DetectorAnomaliasService(new AlertaAnomaliaRepository(mongo), intentos, eventos,
                secuencias, new NotificacionService(new NotificacionRepository(mongo), secuencias), jdbc);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        sembrarGestores();
    }

    @AfterEach
    void cerrar() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        mongo.getDb().drop();
        cliente.close();
    }

    @Test
    void unaCuentaBloqueadaCreaUnaAlertaYAvisaSoloALosGestoresActivos() {
        detector.evaluarIntento(intento("BLOQUEADO", "10.0.0.9"), EMPRESA);
        detector.evaluarIntento(intento("BLOQUEADO", "10.0.0.9"), EMPRESA);

        assertThat(alertas()).singleElement().satisfies(alerta -> {
            assertThat(alerta.getTipo()).isEqualTo(DetectorAnomaliasService.CUENTA_BLOQUEADA);
            assertThat(alerta.getSeveridad()).isEqualTo("ALTA");
            assertThat(alerta.getUsuarioId()).isEqualTo(USUARIO);
            assertThat(alerta.getEstado()).isEqualTo(AlertaAnomalia.ABIERTA);
            assertThat(alerta.getDescripcion()).contains("10.0.0.9");
        });
        assertThat(mongo.findAll(Notificacion.class)).singleElement().satisfies(notificacion -> {
            assertThat(notificacion.getUsuarioId()).isEqualTo(GESTOR);
            assertThat(notificacion.getTipo()).isEqualTo("ALERTA_ANOMALIA");
            assertThat(notificacion.getPrioridad()).isEqualTo("ALTA");
        });
    }

    @Test
    void cerrarLaAlertaPermiteQueSeVuelvaACrear() {
        detector.evaluarIntento(intento("BLOQUEADO", null), EMPRESA);
        AlertaAnomalia alerta = alertas().get(0);
        alerta.cerrar("Usuario confirmó que olvidó la clave", false, GESTOR, LocalDateTime.now());
        new AlertaAnomaliaRepository(mongo).guardar(alerta);

        detector.evaluarIntento(intento("BLOQUEADO", null), EMPRESA);

        assertThat(alertas()).hasSize(2);
    }

    @Test
    void elPrimerAccesoNoAlertaPeroUnaIpNuevaSi() {
        detector.evaluarIntento(intento("EXITOSO", "10.0.0.1"), EMPRESA);
        detector.evaluarIntento(intento("EXITOSO", "10.0.0.1"), EMPRESA);
        assertThat(alertas()).isEmpty();

        detector.evaluarIntento(intento("EXITOSO", "181.50.3.7"), EMPRESA);

        assertThat(alertas()).singleElement().satisfies(alerta -> {
            assertThat(alerta.getTipo()).isEqualTo(DetectorAnomaliasService.IP_NUEVA);
            assertThat(alerta.getSeveridad()).isEqualTo("BAJA");
            assertThat(alerta.getDescripcion()).contains("181.50.3.7");
        });
    }

    @Test
    void tresExportacionesEnUnDiaCreanUnaAlerta() {
        LocalDateTime inicio = LocalDateTime.parse("2026-10-29T09:00:00");
        detector.evaluarEvento(evento("AUDITORIA", "AUDITORIA_EXPORTAR_SOLICITAR", inicio));
        detector.evaluarEvento(evento("AUDITORIA", "AUDITORIA_EXPORTAR_SOLICITAR", inicio.plusHours(2)));
        assertThat(alertas()).isEmpty();

        EventoAuditoria tercera = evento("AUDITORIA", "AUDITORIA_EXPORTAR_SOLICITAR", inicio.plusHours(5));
        detector.evaluarEvento(tercera);

        assertThat(alertas()).singleElement().satisfies(alerta -> {
            assertThat(alerta.getTipo()).isEqualTo(DetectorAnomaliasService.EXPORTACIONES_REPETIDAS);
            assertThat(alerta.getEventoAuditoriaId()).isEqualTo(tercera.getId());
        });
    }

    @Test
    void lasExportacionesEspaciadasNoAlertan() {
        LocalDateTime inicio = LocalDateTime.parse("2026-10-27T09:00:00");
        detector.evaluarEvento(evento("AUDITORIA", "AUDITORIA_EXPORTAR_SOLICITAR", inicio));
        detector.evaluarEvento(evento("AUDITORIA", "AUDITORIA_EXPORTAR_SOLICITAR", inicio.plusDays(1)));
        detector.evaluarEvento(evento("AUDITORIA", "AUDITORIA_EXPORTAR_SOLICITAR", inicio.plusDays(2)));

        assertThat(alertas()).isEmpty();
    }

    @Test
    void unaAccionDeSuperadministracionDeNocheAlerta() {
        detector.evaluarEvento(evento("SUPERADMINISTRACION", "USUARIO_ROLES", LocalDateTime.parse("2026-10-29T10:00:00")));
        assertThat(alertas()).isEmpty();

        EventoAuditoria nocturno = evento("SUPERADMINISTRACION", "USUARIO_ROLES", LocalDateTime.parse("2026-10-29T23:30:00"));
        detector.evaluarEvento(nocturno);

        assertThat(alertas()).singleElement().satisfies(alerta -> {
            assertThat(alerta.getTipo()).isEqualTo(DetectorAnomaliasService.ADMIN_FUERA_DE_HORARIO);
            assertThat(alerta.getSeveridad()).isEqualTo("MEDIA");
            assertThat(alerta.getEventoAuditoriaId()).isEqualTo(nocturno.getId());
        });
    }

    @Test
    void elHorarioLaboralVaDeLasSeisALasVeintidos() {
        assertThat(DetectorAnomaliasService.fueraDeHorario(LocalDateTime.parse("2026-10-29T05:59:00"))).isTrue();
        assertThat(DetectorAnomaliasService.fueraDeHorario(LocalDateTime.parse("2026-10-29T06:00:00"))).isFalse();
        assertThat(DetectorAnomaliasService.fueraDeHorario(LocalDateTime.parse("2026-10-29T21:59:00"))).isFalse();
        assertThat(DetectorAnomaliasService.fueraDeHorario(LocalDateTime.parse("2026-10-29T22:00:00"))).isTrue();
    }

    @Test
    void sinEmpresaNoSeCreaNada() {
        detector.evaluarIntento(intento("BLOQUEADO", "10.0.0.9"), null);

        assertThat(alertas()).isEmpty();
    }

    @Test
    void losIntentosYLosReportesSeBorranALos90Dias() {
        intentos.asegurarIndices();
        new ReporteGeneradoRepository(mongo).asegurarIndices();

        assertThat(ttl(IntentoAcceso.COLECCION, "fechaIntento")).isEqualTo(90L * 24 * 3600);
        assertThat(ttl("reportes_generados", "creadoEn")).isEqualTo(90L * 24 * 3600);
    }

    private List<AlertaAnomalia> alertas() {
        return mongo.findAll(AlertaAnomalia.class);
    }

    private Long ttl(String coleccion, String campo) {
        List<Document> indices = mongo.getCollection(coleccion).listIndexes().into(new ArrayList<>());
        return indices.stream()
                .filter(indice -> indice.get("key", Document.class).containsKey(campo)
                        && indice.containsKey("expireAfterSeconds"))
                .map(indice -> indice.get("expireAfterSeconds", Number.class).longValue())
                .findFirst()
                .orElse(null);
    }

    private IntentoAcceso intento(String resultado, String ip) {
        IntentoAcceso intento = new IntentoAcceso(USUARIO, "ana@correo.co", resultado, null, ip,
                "Mozilla/5.0", LocalDateTime.now());
        intento.identificar(secuencias.siguiente("intentos_acceso"));
        intentos.insertar(intento);
        return intento;
    }

    private EventoAuditoria evento(String modulo, String accion, LocalDateTime fecha) {
        return eventos.insertar(new EventoAuditoria(secuencias.siguiente("auditoria_eventos"), EMPRESA, null,
                USUARIO, modulo, accion, "USUARIO", 50L, "Prueba", null, null, null, null,
                UUID.randomUUID().toString(), fecha));
    }

    private void sembrarGestores() {
        Long permiso = jdbc.queryForObject("SELECT id FROM permisos WHERE codigo = 'AUDITORIA_ALERTAS_GESTIONAR'", Long.class);
        jdbc.update("""
                INSERT INTO roles (id, empresa_id, codigo, nombre, activo) VALUES (900, ?, 'AUDITOR_PRUEBA', 'Auditor', 1)
                """, EMPRESA);
        jdbc.update("INSERT INTO rol_permisos (rol_id, permiso_id, concedido) VALUES (900, ?, 1)", permiso);
        jdbc.update("""
                INSERT INTO usuarios (id, empresa_id, persona_id, nombre_usuario, correo, password_hash, estado)
                VALUES (?, ?, 9001, 'gestor.prueba', 'gestor@prueba.co', 'x', 'ACTIVO'),
                       (?, ?, 9002, 'gestor.inactivo', 'inactivo@prueba.co', 'x', 'INACTIVO')
                """, GESTOR, EMPRESA, GESTOR_INACTIVO, EMPRESA);
        jdbc.update("INSERT INTO usuario_roles (usuario_id, rol_id) VALUES (?, 900), (?, 900)", GESTOR, GESTOR_INACTIVO);
    }
}
