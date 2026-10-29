package com.inmobiliaria.inmobiliaria_api.service.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.inmobiliaria.inmobiliaria_api.dto.servicio.ConocimientoDtos;
import com.inmobiliaria.inmobiliaria_api.entity.cliente.Cliente;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.ArticuloConocimiento;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.CategoriaConocimiento;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.CitaServicio;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.ComunicacionCliente;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.EstadoCitaServicio;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.ArticuloConocimientoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.CategoriaConocimientoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.CitaServicioRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.ComunicacionClienteRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.CambioDocumento;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.TipoCambio;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura.ProyectorCitasServicio;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
 * Servicio al cliente en MongoDB (rama 10): citas de servicio, comunicaciones y base de
 * conocimiento. Migración desde MySQL, consultas, contenido global y contrato con el proyector.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProyectorCitasServicio.class)
@Testcontainers(disabledWithoutDocker = true, parallel = true)
class ServicioClienteMongoTest {

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
    private static final LocalDateTime HOY = LocalDateTime.parse("2026-10-29T12:00:00");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProyectorCitasServicio proyector;

    private MongoClient cliente;
    private MongoTemplate mongo;
    private SecuenciaService secuencias;
    private CitaServicioRepository citas;
    private ComunicacionClienteRepository comunicaciones;
    private CategoriaConocimientoRepository categorias;
    private ArticuloConocimientoRepository articulos;
    private DocumentosMigradosRepository documentos;

    @BeforeEach
    void preparar() {
        cliente = MongoClients.create(MONGODB.getReplicaSetUrl());
        mongo = new MongoTemplate(cliente, "servicio_" + System.nanoTime());
        secuencias = new SecuenciaService(mongo);
        citas = new CitaServicioRepository(mongo);
        comunicaciones = new ComunicacionClienteRepository(mongo);
        categorias = new CategoriaConocimientoRepository(mongo);
        articulos = new ArticuloConocimientoRepository(mongo);
        documentos = new DocumentosMigradosRepository(mongo);
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
    void copiaLasCitasYDetectaCrucesYLaProximaCitaDelCliente() {
        MigradorCitasServicio migrador = new MigradorCitasServicio(jdbc, documentos, citas, secuencias);
        assertThat(migrador.migrar()).isEqualTo(3);
        assertThat(migrador.migrar()).isZero();
        assertThat(secuencias.siguiente(CitaServicioService.SECUENCIA)).isEqualTo(13L);

        CitaServicio cita = citas.findByIdAndEmpresaId(10L, EMPRESA).orElseThrow();
        assertThat(cita.getEstado()).isEqualTo(EstadoCitaServicio.CONFIRMADA);
        assertThat(cita.getVersion()).isEqualTo(1L);

        LocalDateTime inicio = LocalDateTime.parse("2026-11-03T10:00:00");
        assertThat(citas.existeCruce(34L, inicio.plusMinutes(30), inicio.plusHours(2), null)).isTrue();
        assertThat(citas.existeCruce(34L, inicio.plusMinutes(30), inicio.plusHours(2), 10L)).isFalse();
        // La cita 12 está cancelada: no ocupa la agenda.
        assertThat(citas.existeCruce(34L, LocalDateTime.parse("2026-11-04T09:30:00"),
                LocalDateTime.parse("2026-11-04T10:30:00"), null)).isFalse();

        assertThat(citas.proximaDeCliente(EMPRESA, 40L, HOY)).contains(inicio);
        assertThat(citas.proximaDeCliente(EMPRESA, 41L, HOY)).isEmpty();
    }

    @Test
    void unaCopiaVencidaDeLaCitaNoPisaLosCambios() {
        new MigradorCitasServicio(jdbc, documentos, citas, secuencias).migrar();
        CitaServicio primera = citas.findByIdAndEmpresaId(10L, EMPRESA).orElseThrow();
        CitaServicio vieja = citas.findByIdAndEmpresaId(10L, EMPRESA).orElseThrow();

        primera.completar("Entrega realizada");
        citas.guardar(primera);
        vieja.cancelar("El cliente no puede");

        assertThatThrownBy(() -> citas.guardar(vieja)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void lasCitasCumplenElContratoDelProyectorDeLectura() {
        new MigradorCitasServicio(jdbc, documentos, citas, secuencias).migrar();
        Document crudo = mongo.getCollection(CitaServicio.COLECCION).find(Filters.eq("_id", 10L)).first();
        Map<String, Object> datos = new LinkedHashMap<>(crudo);
        datos.remove("_id");

        proyector.aplicar(new CambioDocumento(CitaServicio.COLECCION, TipoCambio.ACTUALIZACION, 10L, datos,
                "{\"_data\":\"prueba\"}"));

        assertThat(jdbc.queryForObject("SELECT estado FROM lectura_citas_servicio WHERE id = 10", String.class))
                .isEqualTo("CONFIRMADA");
    }

    @Test
    void copiaLasComunicaciones() {
        MigradorComunicacionesCliente migrador =
                new MigradorComunicacionesCliente(jdbc, documentos, comunicaciones, secuencias);
        assertThat(migrador.migrar()).isEqualTo(2);
        assertThat(migrador.migrar()).isZero();

        ComunicacionCliente masiva = comunicaciones.findByIdAndEmpresaId(21L, EMPRESA).orElseThrow();
        assertThat(masiva.isMasiva()).isTrue();
        assertThat(masiva.getClienteId()).isNull();
        assertThat(masiva.getEstado()).isEqualTo("PROGRAMADA");
        assertThat(comunicaciones.findByIdAndEmpresaId(20L, EMPRESA).orElseThrow().getEnviadaEn())
                .isEqualTo(LocalDateTime.parse("2026-10-20T08:00:00"));
        assertThat(secuencias.siguiente(ComunicacionClienteService.SECUENCIA)).isEqualTo(22L);
    }

    @Test
    void laBaseDeConocimientoMezclaLoGlobalConLoDeLaEmpresaYFiltraElPortal() {
        new MigradorCategoriasConocimiento(jdbc, documentos, categorias, secuencias).migrar();
        new MigradorArticulosConocimiento(jdbc, documentos, articulos, secuencias).migrar();

        assertThat(categorias.listarDisponibles(EMPRESA)).extracting(CategoriaConocimiento::getNombre)
                .containsExactly("Escrituración", "Pagos");
        assertThat(categorias.listarDisponibles(9L)).extracting(CategoriaConocimiento::getNombre)
                .as("otra empresa solo ve lo global").containsExactly("Escrituración");
        assertThat(categorias.findDisponible(2L, 9L)).isEmpty();

        ServicioClienteAlcanceService alcance = mock(ServicioClienteAlcanceService.class);
        BaseConocimientoService servicio = new BaseConocimientoService(categorias, articulos, alcance, secuencias);
        ContextoUsuario interno = new ContextoUsuario(34L, EMPRESA);
        ContextoUsuario portal = new ContextoUsuario(60L, EMPRESA);
        when(alcance.clienteAutenticado(interno)).thenReturn(Optional.empty());
        when(alcance.clienteAutenticado(portal)).thenReturn(Optional.of(mock(Cliente.class)));

        assertThat(servicio.listar(interno, null, null, 0, 10).contenido())
                .extracting(ConocimientoDtos.ArticuloResponse::id).containsExactly(31L, 30L, 32L);
        assertThat(servicio.listar(portal, null, null, 0, 10).contenido())
                .as("el portal solo ve lo publicado y visible").extracting(ConocimientoDtos.ArticuloResponse::id)
                .containsExactly(31L, 30L);
        assertThat(servicio.listar(interno, null, "cómo (PAGAR", 0, 10).contenido())
                .as("texto sin distinguir mayúsculas y sin interpretar como regex")
                .extracting(ConocimientoDtos.ArticuloResponse::id).containsExactly(31L);
        assertThat(servicio.listar(interno, 1L, null, 0, 10).contenido())
                .extracting(ConocimientoDtos.ArticuloResponse::id).containsExactly(30L);
    }

    @Test
    void elSlugYElNombreDeCategoriaSonUnicosPorEmpresaIncluyendoLoGlobal() {
        new MigradorCategoriasConocimiento(jdbc, documentos, categorias, secuencias).migrar();
        new MigradorArticulosConocimiento(jdbc, documentos, articulos, secuencias).migrar();

        CategoriaConocimiento global = new CategoriaConocimiento(null, "Escrituración", null, (short) 3);
        global.identificar(secuencias.siguiente(BaseConocimientoService.SECUENCIA_CATEGORIAS));
        assertThatThrownBy(() -> categorias.insertar(global)).isInstanceOf(DataIntegrityViolationException.class);

        CategoriaConocimiento deOtra = new CategoriaConocimiento(9L, "Escrituración", null, (short) 1);
        deOtra.identificar(secuencias.siguiente(BaseConocimientoService.SECUENCIA_CATEGORIAS));
        categorias.insertar(deOtra);

        ArticuloConocimiento repetido = new ArticuloConocimiento(EMPRESA, 2L, "Otro", "como-pagar-cuota",
                "Contenido", false, 34L);
        repetido.identificar(secuencias.siguiente(BaseConocimientoService.SECUENCIA_ARTICULOS));
        assertThatThrownBy(() -> articulos.insertar(repetido)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void sembrarMySql() {
        jdbc.update("""
                INSERT INTO citas_servicio (id, empresa_id, proyecto_id, cliente_id, solicitud_id, responsable_id, tipo,
                  fecha_inicio, fecha_fin, lugar, estado, resultado, version)
                VALUES (10, 8, 1, 40, NULL, 34, 'ENTREGA', '2026-11-03 10:00:00', '2026-11-03 11:00:00',
                        'Lote 301', 'CONFIRMADA', NULL, 1),
                       (11, 8, 1, 40, NULL, 34, 'VISITA_TECNICA', '2026-09-03 10:00:00', '2026-09-03 11:00:00',
                        NULL, 'REALIZADA', 'Sin novedades', 2),
                       (12, 8, 1, 41, NULL, 34, 'VISITA_TECNICA', '2026-11-04 09:00:00', '2026-11-04 11:00:00',
                        NULL, 'CANCELADA', 'No asistirá', 1)
                """);
        jdbc.update("""
                INSERT INTO comunicaciones_cliente (id, empresa_id, cliente_id, enviado_por, canal, asunto, contenido,
                  es_masiva, estado, programada_para, enviada_en, version)
                VALUES (20, 8, 40, 34, 'EMAIL', 'Bienvenida', 'Hola Ana', 0, 'ENVIADA', NULL,
                        '2026-10-20 08:00:00', 1),
                       (21, 8, NULL, 34, 'SMS', NULL, 'Recuerde su cuota', 1, 'PROGRAMADA',
                        '2026-11-01 08:00:00', NULL, 0)
                """);
        jdbc.update("""
                INSERT INTO categorias_conocimiento (id, empresa_id, nombre, descripcion, orden, activo)
                VALUES (1, NULL, 'Escrituración', 'Global', 1, 1),
                       (2, 8, 'Pagos', NULL, 2, 1),
                       (3, 8, 'Antigua', NULL, 3, 0)
                """);
        jdbc.update("""
                INSERT INTO articulos_conocimiento (id, empresa_id, categoria_id, titulo, slug, contenido,
                  visible_clientes, estado, autor_id, publicado_en, version)
                VALUES (30, NULL, 1, 'Qué necesito para escriturar', 'que-necesito-para-escriturar', 'Texto', 1,
                        'PUBLICADO', NULL, '2026-09-01 10:00:00', 0),
                       (31, 8, 2, 'Cómo (pagar) mi cuota', 'como-pagar-cuota', 'Texto', 1, 'PUBLICADO', 34,
                        '2026-10-01 10:00:00', 0),
                       (32, 8, 2, 'Borrador interno', 'borrador-interno', 'Texto', 0, 'BORRADOR', 34, NULL, 0)
                """);
    }
}
