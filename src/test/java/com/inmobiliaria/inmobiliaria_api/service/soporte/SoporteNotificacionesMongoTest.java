package com.inmobiliaria.inmobiliaria_api.service.soporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inmobiliaria.inmobiliaria_api.entity.notificacion.Notificacion;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.PrioridadServicio;
import com.inmobiliaria.inmobiliaria_api.entity.soporte.EstadoTicket;
import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketMensaje;
import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketSoporte;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.notificacion.NotificacionRepository;
import com.inmobiliaria.inmobiliaria_api.repository.soporte.TicketSoporteRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.notificacion.MigradorNotificaciones;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.CambioDocumento;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.TipoCambio;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura.ProyectorTicketsSoporte;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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

/**
 * Tickets de soporte y notificaciones en MongoDB (rama 6): migración desde MySQL, repositorios
 * y contrato con el proyector de tickets. MySQL corre Flyway completo, así que también valida V25.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProyectorTicketsSoporte.class)
@Testcontainers(disabledWithoutDocker = true, parallel = true)
class SoporteNotificacionesMongoTest {

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

    private static final LocalDateTime CREADO = LocalDateTime.parse("2026-10-20T10:00:00.250");
    private static final LocalDateTime LEIDA = LocalDateTime.parse("2026-10-21T08:00:00");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProyectorTicketsSoporte proyector;

    private MongoClient cliente;
    private MongoTemplate mongo;
    private TicketSoporteRepository tickets;
    private NotificacionRepository notificaciones;
    private SecuenciaService secuencias;
    private MigradorTicketsSoporte migradorTickets;
    private MigradorNotificaciones migradorNotificaciones;

    @BeforeEach
    void preparar() {
        cliente = MongoClients.create(MONGODB.getReplicaSetUrl());
        mongo = new MongoTemplate(cliente, "soporte_" + System.nanoTime());
        tickets = new TicketSoporteRepository(mongo);
        notificaciones = new NotificacionRepository(mongo);
        secuencias = new SecuenciaService(mongo);
        DocumentosMigradosRepository documentos = new DocumentosMigradosRepository(mongo);
        migradorTickets = new MigradorTicketsSoporte(jdbc, documentos, tickets, secuencias);
        migradorNotificaciones = new MigradorNotificaciones(jdbc, documentos, notificaciones, secuencias);
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
    void copiaLosTicketsConSusPartesEmbebidas() {
        assertThat(migradorTickets.migrar()).isEqualTo(2);

        TicketSoporte ticket = tickets.findByIdAndEmpresaId(60L, 8L).orElseThrow();
        assertThat(ticket.getCodigo()).isEqualTo("SUP-20261020-AAAA0001");
        assertThat(ticket.getEstado()).isEqualTo(EstadoTicket.EN_PROCESO);
        assertThat(ticket.getPrioridad()).isEqualTo(PrioridadServicio.ALTA);
        assertThat(ticket.getAsignadoA()).isEqualTo(35L);
        assertThat(ticket.getVersion()).isEqualTo(2L);
        assertThat(ticket.getCreadoEn()).isEqualTo(CREADO);
        assertThat(ticket.mensajes(true)).extracting(TicketMensaje::getId).containsExactly(700L, 701L);
        assertThat(ticket.mensajes(false)).extracting(TicketMensaje::getId).containsExactly(700L);
        assertThat(ticket.getAdjuntos()).singleElement().satisfies(adjunto -> {
            assertThat(adjunto.getArchivoId()).isEqualTo(901L);
            assertThat(adjunto.getMensajeId()).isEqualTo(700L);
        });
        assertThat(ticket.getHistorial()).singleElement()
                .satisfies(cambio -> assertThat(cambio.getValorNuevo()).isEqualTo("EN_PROCESO"));

        // Un ticket de plataforma (sin empresa) también se migra.
        Document sinEmpresa = mongo.getCollection(TicketSoporte.COLECCION).find(Filters.eq("_id", 61L)).first();
        assertThat(sinEmpresa).isNotNull().doesNotContainKey("empresaId");
        assertThat(migradorTickets.migrar()).isZero();
    }

    @Test
    void copiaLasNotificacionesConSusEnvios() {
        assertThat(migradorNotificaciones.migrar()).isEqualTo(4);

        Notificacion notificacion = notificaciones.findByIdAndUsuarioId(300L, 34L).orElseThrow();
        assertThat(notificacion.getTipo()).isEqualTo("SOPORTE_RESPUESTA");
        assertThat(notificacion.getEntidadId()).isEqualTo(60L);
        assertThat(notificacion.getLeidaEn()).isNull();
        assertThat(notificacion.getVersion()).isEqualTo(1L);
        assertThat(notificacion.getEnvios()).singleElement().satisfies(envio -> {
            assertThat(envio.getId()).isEqualTo(20L);
            assertThat(envio.getEstado()).isEqualTo("ENVIADO");
            assertThat(envio.getIntentos()).isEqualTo(1);
            assertThat(envio.getCanal()).isEqualTo("EMAIL");
        });
        assertThat(notificaciones.findByIdAndUsuarioId(300L, 99L)).isEmpty();
        assertThat(migradorNotificaciones.migrar()).isZero();
    }

    @Test
    void lasSecuenciasQuedanPorEncimaDeLosIdsDeMySql() {
        migradorTickets.migrar();
        migradorNotificaciones.migrar();

        assertThat(secuencias.siguiente("tickets_soporte")).isEqualTo(62L);
        assertThat(secuencias.siguiente("ticket_mensajes")).isEqualTo(702L);
        assertThat(secuencias.siguiente("ticket_adjuntos")).isEqualTo(81L);
        assertThat(secuencias.siguiente("ticket_historial")).isEqualTo(96L);
        assertThat(secuencias.siguiente("notificaciones")).isEqualTo(304L);
        assertThat(secuencias.siguiente("envios_notificacion")).isEqualTo(21L);
    }

    @Test
    void soloSeListanLasNotificacionesVigentes() {
        migradorNotificaciones.migrar();
        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Bogota"));
        PageRequest pagina = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "creadoEn"));

        assertThat(notificaciones.buscarVigentes(34L, null, ahora, pagina).getContent())
                .extracting(Notificacion::getId).containsExactly(301L, 300L);
        assertThat(notificaciones.buscarVigentes(34L, true, ahora, pagina).getContent())
                .extracting(Notificacion::getId).containsExactly(301L);
        assertThat(notificaciones.buscarVigentes(34L, false, ahora, pagina).getContent())
                .extracting(Notificacion::getId).containsExactly(300L);
        assertThat(notificaciones.contarVigentesNoLeidas(34L, ahora)).isEqualTo(1);
    }

    @Test
    void marcarLeidaEsIdempotenteYSoloParaElDueno() {
        migradorNotificaciones.migrar();

        assertThat(notificaciones.marcarLeida(300L, 34L, LEIDA).orElseThrow().getLeidaEn()).isEqualTo(LEIDA);
        assertThat(notificaciones.marcarLeida(300L, 34L, LEIDA.plusHours(1)).orElseThrow().getLeidaEn())
                .isEqualTo(LEIDA);
        assertThat(notificaciones.marcarLeida(303L, 34L, LEIDA)).isEmpty();
        assertThat(notificaciones.findByIdAndUsuarioId(303L, 99L).orElseThrow().getLeidaEn()).isNull();
        assertThat(notificaciones.contarVigentesNoLeidas(34L, LocalDateTime.now(ZoneId.of("America/Bogota"))))
                .isZero();
    }

    @Test
    void elCodigoDelTicketEsUnico() {
        migradorTickets.migrar();
        TicketSoporte repetido = nuevo(62L, "SUP-20261020-AAAA0001");

        assertThatThrownBy(() -> tickets.insertar(repetido)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void losTicketsCumplenElContratoDelProyectorDeLectura() {
        migradorTickets.migrar();
        tickets.insertar(nuevo(62L, "SUP-NUEVO-1"));

        proyectar(60L);
        proyectar(61L);
        proyectar(62L);

        assertThat(jdbc.queryForMap("SELECT empresa_id, proyecto_id, estado FROM lectura_tickets_soporte WHERE id = 60"))
                .containsEntry("estado", "EN_PROCESO");
        assertThat(jdbc.queryForObject("SELECT empresa_id FROM lectura_tickets_soporte WHERE id = 61", Long.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT estado FROM lectura_tickets_soporte WHERE id = 62", String.class))
                .isEqualTo("ABIERTO");
    }

    private void proyectar(Long id) {
        Document crudo = mongo.getCollection(TicketSoporte.COLECCION).find(Filters.eq("_id", id)).first();
        assertThat(crudo).as("ticket %s en MongoDB", id).isNotNull();
        Map<String, Object> datos = new LinkedHashMap<>(crudo);
        datos.remove("_id");
        proyector.aplicar(new CambioDocumento(TicketSoporte.COLECCION, TipoCambio.INSERCION, id, datos,
                "{\"_data\":\"prueba\"}"));
    }

    private static TicketSoporte nuevo(long id, String codigo) {
        TicketSoporte ticket = new TicketSoporte(8L, 1L, codigo, 34L, "Error al exportar", "REPORTES",
                PrioridadServicio.MEDIA, "El reporte de cartera no descarga");
        ticket.identificar(id);
        ticket.agregarMensaje(id * 10, 34L, "El reporte de cartera no descarga", false);
        return ticket;
    }

    private void sembrarMySql() {
        jdbc.update("""
                INSERT INTO tickets_soporte (id, empresa_id, proyecto_id, codigo, solicitante_id, asignado_a,
                  asunto, categoria, prioridad, descripcion, estado, fecha_primera_respuesta, creado_en,
                  actualizado_en, version)
                VALUES (60, 8, 1, 'SUP-20261020-AAAA0001', 34, 35, 'No puedo ingresar', 'ACCESO', 'ALTA',
                  'El sistema dice usuario bloqueado', 'EN_PROCESO', '2026-10-20 11:00:00', ?, ?, 2)
                """, Timestamp.valueOf(CREADO), Timestamp.valueOf(CREADO));
        jdbc.update("""
                INSERT INTO tickets_soporte (id, codigo, solicitante_id, asunto, categoria, prioridad,
                  descripcion, estado, creado_en, actualizado_en)
                VALUES (61, 'SUP-20261021-AAAA0002', 34, 'Lentitud', 'RENDIMIENTO', 'BAJA',
                  'La plataforma está lenta', 'ABIERTO', '2026-10-21 09:00:00', '2026-10-21 09:00:00')
                """);
        jdbc.update("""
                INSERT INTO ticket_mensajes (id, ticket_id, usuario_id, mensaje, es_respuesta_interna, creado_en)
                VALUES (700, 60, 34, 'El sistema dice usuario bloqueado', 0, '2026-10-20 10:00:01'),
                       (701, 60, 35, 'Revisar intentos fallidos en Redis', 1, '2026-10-20 10:30:00')
                """);
        jdbc.update("""
                INSERT INTO ticket_adjuntos (id, ticket_id, mensaje_id, archivo_id, creado_en)
                VALUES (80, 60, 700, 901, '2026-10-20 10:00:02')
                """);
        jdbc.update("""
                INSERT INTO ticket_historial (id, ticket_id, campo, valor_anterior, valor_nuevo, usuario_id, creado_en)
                VALUES (95, 60, 'estado', 'ABIERTO', 'EN_PROCESO', 35, '2026-10-20 11:00:00')
                """);
        jdbc.update("""
                INSERT INTO notificaciones (id, empresa_id, usuario_id, tipo, titulo, mensaje, entidad_tipo,
                  entidad_id, prioridad, leida_en, expira_en, creado_en, actualizado_en, version)
                VALUES (300, 8, 34, 'SOPORTE_RESPUESTA', 'Nueva respuesta', 'Tu ticket tiene respuesta',
                        'TICKET_SOPORTE', 60, 'MEDIA', NULL, NULL, '2026-10-20 11:00:00', '2026-10-20 11:00:00', 1),
                       (301, 8, 34, 'SOPORTE_ESTADO', 'Estado actualizado', 'El ticket cambió',
                        'TICKET_SOPORTE', 60, 'MEDIA', '2026-10-21 07:00:00', NULL, '2026-10-21 06:00:00',
                        '2026-10-21 07:00:00', 0),
                       (302, 8, 34, 'AVISO', 'Aviso vencido', 'Ya no aplica', NULL, NULL, 'BAJA', NULL,
                        '2026-01-01 00:00:00', '2025-12-01 00:00:00', '2025-12-01 00:00:00', 0),
                       (303, 8, 99, 'AVISO', 'De otro usuario', 'No es tuya', NULL, NULL, 'BAJA', NULL, NULL,
                        '2026-10-21 06:00:00', '2026-10-21 06:00:00', 0)
                """);
        jdbc.update("""
                INSERT INTO envios_notificacion (id, notificacion_id, canal, destinatario, estado, proveedor,
                  referencia_externa, intentos, enviado_en, creado_en)
                VALUES (20, 300, 'EMAIL', 'cliente@correo.com', 'ENVIADO', 'smtp', 'ref-1', 1,
                        '2026-10-20 11:00:05', '2026-10-20 11:00:01')
                """);
    }
}
