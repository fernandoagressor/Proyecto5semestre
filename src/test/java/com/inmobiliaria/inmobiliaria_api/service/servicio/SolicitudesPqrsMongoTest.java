package com.inmobiliaria.inmobiliaria_api.service.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.EstadoSolicitud;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.PrioridadServicio;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudMensaje;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudPqrs;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.TipoSolicitud;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.SolicitudPqrsRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.CambioDocumento;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.TipoCambio;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura.ProyectorSolicitudesPqrs;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
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
 * PQRS en MongoDB (rama 5): migración desde MySQL, repositorio y contrato con el proyector
 * de la tabla espejo. MySQL corre Flyway completo, así que también valida V24.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProyectorSolicitudesPqrs.class)
@Testcontainers(disabledWithoutDocker = true, parallel = true)
class SolicitudesPqrsMongoTest {

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

    private static final LocalDateTime CREADA = LocalDateTime.parse("2026-10-20T10:00:00.123456");
    private static final LocalDateTime RESUELTA = LocalDateTime.parse("2026-10-20T15:00:00");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProyectorSolicitudesPqrs proyector;

    private MongoClient cliente;
    private MongoTemplate mongo;
    private SolicitudPqrsRepository repository;
    private SecuenciaService secuencias;
    private MigradorSolicitudesPqrs migrador;

    @BeforeEach
    void preparar() {
        cliente = MongoClients.create(MONGODB.getReplicaSetUrl());
        mongo = new MongoTemplate(cliente, "pqrs_" + System.nanoTime());
        repository = new SolicitudPqrsRepository(mongo);
        secuencias = new SecuenciaService(mongo);
        migrador = new MigradorSolicitudesPqrs(jdbc, new DocumentosMigradosRepository(mongo), repository, secuencias);
        // Las filas de prueba no tienen empresas, clientes ni archivos reales detrás.
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
    void copiaLaSolicitudConSusPartesEmbebidas() {
        assertThat(migrador.migrar()).isEqualTo(2);

        SolicitudPqrs solicitud = repository.findByIdAndEmpresaId(40L, 8L).orElseThrow();
        assertThat(solicitud.getCodigo()).isEqualTo("PQRS-20261020-AAAA0001");
        assertThat(solicitud.getTipo()).isEqualTo(TipoSolicitud.QUEJA);
        assertThat(solicitud.getPrioridad()).isEqualTo(PrioridadServicio.ALTA);
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.RESUELTA);
        assertThat(solicitud.getProyectoId()).isEqualTo(1L);
        assertThat(solicitud.getVersion()).isEqualTo(3L);
        // MongoDB guarda milisegundos: los microsegundos de MySQL se truncan.
        assertThat(solicitud.getCreadoEn()).isEqualTo(CREADA.truncatedTo(ChronoUnit.MILLIS));
        assertThat(solicitud.getFechaResolucion()).isEqualTo(RESUELTA);
        assertThat(solicitud.mensajes(true)).extracting(SolicitudMensaje::getId).containsExactly(500L, 501L);
        assertThat(solicitud.mensajes(false)).extracting(SolicitudMensaje::getId).containsExactly(500L);
        assertThat(solicitud.getAdjuntos()).singleElement().satisfies(adjunto -> {
            assertThat(adjunto.getArchivoId()).isEqualTo(900L);
            assertThat(adjunto.getMensajeId()).isEqualTo(500L);
        });
        assertThat(solicitud.getHistorial()).singleElement()
                .satisfies(cambio -> assertThat(cambio.getValorNuevo()).isEqualTo("RESUELTA"));
        assertThat(solicitud.getEncuesta().getPuntuacion()).isEqualTo(5);
        assertThat(solicitud.getEncuesta().getComentario()).isEqualTo("Excelente");

        SolicitudPqrs sinPartes = repository.findByIdAndEmpresaId(41L, 8L).orElseThrow();
        assertThat(sinPartes.getProyectoId()).isNull();
        assertThat(sinPartes.mensajes(true)).isEmpty();
        assertThat(sinPartes.getEncuesta()).isNull();
        assertThat(repository.findByIdAndEmpresaId(40L, 9L)).isEmpty();
    }

    @Test
    void esIdempotenteYNoPisaLoAtendidoEnMongo() {
        migrador.migrar();
        SolicitudPqrs atendida = repository.findByIdAndEmpresaId(41L, 8L).orElseThrow();
        atendida.asignar(77L);
        repository.guardar(atendida);

        assertThat(migrador.migrar()).isZero();

        assertThat(mongo.getCollection(SolicitudPqrs.COLECCION).countDocuments()).isEqualTo(2);
        SolicitudPqrs releida = repository.findByIdAndEmpresaId(41L, 8L).orElseThrow();
        assertThat(releida.getAsignadoA()).isEqualTo(77L);
        assertThat(releida.getEstado()).isEqualTo(EstadoSolicitud.EN_PROCESO);
    }

    @Test
    void lasSecuenciasQuedanPorEncimaDeLosIdsDeMySql() {
        migrador.migrar();

        assertThat(secuencias.siguiente(SecuenciasPqrs.SOLICITUDES)).isEqualTo(42L);
        assertThat(secuencias.siguiente(SecuenciasPqrs.MENSAJES)).isEqualTo(502L);
        assertThat(secuencias.siguiente(SecuenciasPqrs.ADJUNTOS)).isEqualTo(71L);
        assertThat(secuencias.siguiente(SecuenciasPqrs.HISTORIAL)).isEqualTo(91L);
    }

    @Test
    void dosAgentesGuardandoLaMismaSolicitudProducenConflicto() {
        repository.insertar(nueva(100L, "PQRS-NUEVA-1"));
        SolicitudPqrs agenteA = repository.findByIdAndEmpresaId(100L, 8L).orElseThrow();
        SolicitudPqrs agenteB = repository.findByIdAndEmpresaId(100L, 8L).orElseThrow();

        agenteA.asignar(5L);
        repository.guardar(agenteA);
        agenteB.asignar(6L);

        assertThatThrownBy(() -> repository.guardar(agenteB))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repository.findByIdAndEmpresaId(100L, 8L).orElseThrow().getAsignadoA()).isEqualTo(5L);
    }

    @Test
    void elCodigoEsUnicoPorEmpresa() {
        repository.asegurarIndices();
        repository.insertar(nueva(101L, "PQRS-REPETIDO"));

        assertThatThrownBy(() -> repository.insertar(nueva(102L, "PQRS-REPETIDO")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elResumenFiltraOrdenaYNoTraeLaConversacion() {
        migrador.migrar();
        repository.insertar(nueva(103L, "PQRS-NUEVA-3"));

        Page<SolicitudPqrs> abiertas = repository.buscarResumen(
                where("empresaId").is(8L).and("estado").is(EstadoSolicitud.ABIERTA.name()),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "creadoEn")));

        assertThat(abiertas.getTotalElements()).isEqualTo(2);
        assertThat(abiertas.getContent()).extracting(SolicitudPqrs::getId).containsExactly(103L, 41L);
        assertThat(abiertas.getContent().get(0).mensajes(true)).isEmpty();
        assertThat(repository.contarAbiertasDeCliente(8L, 5L)).isEqualTo(3);
        assertThat(repository.contarAbiertasDeCliente(8L, 6L)).isZero();
    }

    @Test
    void losDocumentosCumplenElContratoDelProyectorDeLectura() {
        migrador.migrar();
        repository.insertar(nueva(104L, "PQRS-NUEVA-4"));

        proyectar(40L);
        proyectar(104L);

        Map<String, Object> migrada = jdbc.queryForMap(
                "SELECT estado, tipo, empresa_id, proyecto_id FROM lectura_solicitudes_pqrs WHERE id = 40");
        assertThat(migrada).containsEntry("estado", "RESUELTA").containsEntry("tipo", "QUEJA");
        assertThat(fecha("lectura_solicitudes_pqrs", "creado_en", "id", 40L))
                .isEqualTo(CREADA.truncatedTo(ChronoUnit.MILLIS));
        assertThat(jdbc.queryForObject(
                "SELECT puntuacion FROM lectura_encuestas_satisfaccion WHERE solicitud_id = 40", Integer.class))
                .isEqualTo(5);
        assertThat(fecha("lectura_encuestas_satisfaccion", "respondida_en", "solicitud_id", 40L))
                .isEqualTo(RESUELTA.plusHours(1));

        SolicitudPqrs nueva = repository.findByIdAndEmpresaId(104L, 8L).orElseThrow();
        assertThat(fecha("lectura_solicitudes_pqrs", "creado_en", "id", 104L))
                .isEqualTo(nueva.getCreadoEn());
        assertThat(fecha("lectura_solicitudes_pqrs", "fecha_limite", "id", 104L))
                .isEqualTo(nueva.getFechaLimite());
    }

    private void proyectar(Long id) {
        Document crudo = mongo.getCollection(SolicitudPqrs.COLECCION).find(Filters.eq("_id", id)).first();
        assertThat(crudo).as("documento %s en MongoDB", id).isNotNull();
        Map<String, Object> datos = new LinkedHashMap<>(crudo);
        datos.remove("_id");
        proyector.aplicar(new CambioDocumento(SolicitudPqrs.COLECCION, TipoCambio.INSERCION, id, datos,
                "{\"_data\":\"prueba\"}"));
    }

    private LocalDateTime fecha(String tabla, String columna, String llave, Long id) {
        Timestamp valor = jdbc.queryForObject(
                "SELECT " + columna + " FROM " + tabla + " WHERE " + llave + " = ?", Timestamp.class, id);
        return valor == null ? null : valor.toLocalDateTime();
    }

    private static SolicitudPqrs nueva(long id, String codigo) {
        SolicitudPqrs solicitud = new SolicitudPqrs(8L, 2L, codigo, 5L, null, TipoSolicitud.PETICION,
                "INFORMACION", "Asunto", "Detalle", PrioridadServicio.MEDIA,
                LocalDateTime.now().plusDays(3).truncatedTo(ChronoUnit.MILLIS));
        solicitud.identificar(id);
        solicitud.agregarMensaje(id * 10, 3L, "Detalle", false, "PORTAL");
        return solicitud;
    }

    private void sembrarMySql() {
        jdbc.update("""
                INSERT INTO solicitudes_pqrs (id, empresa_id, proyecto_id, codigo, cliente_id, tipo, categoria,
                  asunto, descripcion, prioridad, estado, fecha_limite, fecha_resolucion, creado_en,
                  actualizado_en, version)
                VALUES (40, 8, 1, 'PQRS-20261020-AAAA0001', 5, 'QUEJA', 'ENTREGA', 'Grieta en el muro',
                  'La pared del lote 12 tiene una grieta', 'ALTA', 'RESUELTA', '2026-10-21 10:00:00', ?, ?, ?, 3)
                """, Timestamp.valueOf(RESUELTA), Timestamp.valueOf(CREADA), Timestamp.valueOf(RESUELTA));
        jdbc.update("""
                INSERT INTO solicitudes_pqrs (id, empresa_id, codigo, cliente_id, tipo, categoria, asunto,
                  descripcion, prioridad, estado, creado_en, actualizado_en)
                VALUES (41, 8, 'PQRS-20261021-AAAA0002', 5, 'PETICION', 'INFORMACION', 'Paz y salvo',
                  'Necesito el paz y salvo', 'MEDIA', 'ABIERTA', '2026-10-21 09:00:00', '2026-10-21 09:00:00')
                """);
        jdbc.update("""
                INSERT INTO solicitud_mensajes (id, solicitud_id, usuario_id, mensaje, es_interno, canal, creado_en)
                VALUES (500, 40, 34, 'La pared del lote 12 tiene una grieta', 0, 'PORTAL', '2026-10-20 10:00:01'),
                       (501, 40, 35, 'Enviar maestro de obra', 1, 'INTERNO', '2026-10-20 11:00:00')
                """);
        jdbc.update("""
                INSERT INTO solicitud_adjuntos (id, solicitud_id, mensaje_id, archivo_id, creado_en)
                VALUES (70, 40, 500, 900, '2026-10-20 10:00:02')
                """);
        jdbc.update("""
                INSERT INTO solicitud_historial (id, solicitud_id, campo, valor_anterior, valor_nuevo, usuario_id, creado_en)
                VALUES (90, 40, 'estado', 'EN_PROCESO', 'RESUELTA', 35, ?)
                """, Timestamp.valueOf(RESUELTA));
        jdbc.update("""
                INSERT INTO encuestas_satisfaccion (id, empresa_id, cliente_id, solicitud_id, puntuacion,
                  comentario, respondida_en)
                VALUES (1, 8, 5, 40, 3, NULL, '2026-10-20 15:30:00'),
                       (2, 8, 5, 40, 5, 'Excelente', '2026-10-20 16:00:00')
                """);
    }
}
