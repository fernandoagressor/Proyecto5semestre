package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.CanalCobro;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoCuentaGenerado;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.GestionCobro;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoTarea;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.PrioridadTarea;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.Tarea;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.TareaHistorial;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.EstadoCuentaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.GestionCobroRepository;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.TareaRepository;
import com.inmobiliaria.inmobiliaria_api.service.cartera.MigradorEstadosCuenta;
import com.inmobiliaria.inmobiliaria_api.service.cartera.MigradorGestionesCobro;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.migracion.InicializadorIndicesMongo;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.CambioDocumento;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.TipoCambio;
import com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura.ProyectorTareas;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Seguimiento en MongoDB (rama 9): tareas, gestiones de cobro y estados de cuenta. Migración desde
 * MySQL, consultas, vencimiento automático, índices y contrato con el proyector de tareas.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProyectorTareas.class)
@Testcontainers(disabledWithoutDocker = true, parallel = true)
class SeguimientoMongoTest {

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

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProyectorTareas proyector;

    private MongoClient cliente;
    private MongoTemplate mongo;
    private SecuenciaService secuencias;
    private TareaRepository tareas;
    private GestionCobroRepository gestiones;
    private EstadoCuentaRepository estados;
    private MigradorTareas migradorTareas;
    private MigradorGestionesCobro migradorGestiones;
    private MigradorEstadosCuenta migradorEstados;

    @BeforeEach
    void preparar() {
        cliente = MongoClients.create(MONGODB.getReplicaSetUrl());
        mongo = new MongoTemplate(cliente, "seguimiento_" + System.nanoTime());
        secuencias = new SecuenciaService(mongo);
        tareas = new TareaRepository(mongo);
        gestiones = new GestionCobroRepository(mongo);
        estados = new EstadoCuentaRepository(mongo);
        DocumentosMigradosRepository documentos = new DocumentosMigradosRepository(mongo);
        migradorTareas = new MigradorTareas(jdbc, documentos, tareas, secuencias);
        migradorGestiones = new MigradorGestionesCobro(jdbc, documentos, gestiones, secuencias);
        migradorEstados = new MigradorEstadosCuenta(jdbc, documentos, estados, secuencias);
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
    void copiaLasTareasConSuHistorialUnaSolaVez() {
        assertThat(migradorTareas.migrar()).isEqualTo(2);
        assertThat(migradorTareas.migrar()).isZero();

        Tarea tarea = tareas.findByIdAndEmpresaId(30L, EMPRESA).orElseThrow();
        assertThat(tarea.getEstado()).isEqualTo(EstadoTarea.EN_PROCESO);
        assertThat(tarea.getPrioridad()).isEqualTo(PrioridadTarea.ALTA);
        assertThat(tarea.getFechaVencimiento()).isEqualTo(LocalDateTime.parse("2026-10-01T17:00:00"));
        assertThat(tarea.getVersion()).isEqualTo(2L);
        assertThat(tarea.getHistorial()).extracting(TareaHistorial::getId).containsExactly(300L, 301L);
        assertThat(tarea.getHistorial()).extracting(TareaHistorial::getEstadoNuevo)
                .containsExactly("PENDIENTE", "EN_PROCESO");
        assertThat(tareas.findByIdAndEmpresaId(31L, EMPRESA).orElseThrow().getHistorial()).isEmpty();

        assertThat(secuencias.siguiente(TareaService.SECUENCIA)).isEqualTo(32L);
        assertThat(secuencias.siguiente(TareaService.SECUENCIA_HISTORIAL)).isEqualTo(302L);
    }

    @Test
    void elVencimientoAutomaticoDejaHistorialYNoTocaLasYaCerradas() {
        migradorTareas.migrar();
        TareaService servicio = new TareaService(tareas, null, null, secuencias);

        assertThat(servicio.procesarVencidas()).isEqualTo(1);
        assertThat(servicio.procesarVencidas()).as("ya no quedan por vencer").isZero();

        Tarea vencida = tareas.findByIdAndEmpresaId(30L, EMPRESA).orElseThrow();
        assertThat(vencida.getEstado()).isEqualTo(EstadoTarea.VENCIDA);
        assertThat(vencida.getHistorial()).last().satisfies(h -> {
            assertThat(h.getEstadoAnterior()).isEqualTo("EN_PROCESO");
            assertThat(h.getComentario()).isEqualTo("Vencimiento automático");
        });
        assertThat(tareas.findByIdAndEmpresaId(31L, EMPRESA).orElseThrow().getEstado())
                .isEqualTo(EstadoTarea.COMPLETADA);
    }

    @Test
    void unaCopiaVencidaDeLaTareaNoPisaLosCambios() {
        migradorTareas.migrar();
        Tarea primera = tareas.findByIdAndEmpresaId(30L, EMPRESA).orElseThrow();
        Tarea vieja = tareas.findByIdAndEmpresaId(30L, EMPRESA).orElseThrow();

        primera.cambiarEstado(EstadoTarea.COMPLETADA, LocalDateTime.parse("2026-10-29T10:00:00"));
        tareas.guardar(primera);
        vieja.reasignar(99L);

        assertThatThrownBy(() -> tareas.guardar(vieja)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void lasTareasCumplenElContratoDelProyectorDeLectura() {
        migradorTareas.migrar();
        Document crudo = mongo.getCollection(Tarea.COLECCION).find(Filters.eq("_id", 30L)).first();
        Map<String, Object> datos = new LinkedHashMap<>(crudo);
        datos.remove("_id");

        proyector.aplicar(new CambioDocumento(Tarea.COLECCION, TipoCambio.INSERCION, 30L, datos,
                "{\"_data\":\"prueba\"}"));

        assertThat(jdbc.queryForMap("SELECT empresa_id, proyecto_id, estado FROM lectura_tareas WHERE id = 30"))
                .containsEntry("estado", "EN_PROCESO");
    }

    @Test
    void copiaLasGestionesConElProyectoDelContratoYCuentaLasPromesas() {
        assertThat(migradorGestiones.migrar()).isEqualTo(2);
        assertThat(migradorGestiones.migrar()).isZero();

        List<GestionCobro> delContrato = gestiones.listarPorContrato(EMPRESA, 70L);
        assertThat(delContrato).extracting(GestionCobro::getId).containsExactly(81L, 80L);
        GestionCobro promesa = delContrato.get(0);
        assertThat(promesa.getProyectoId()).as("copiado del contrato").isEqualTo(1L);
        assertThat(promesa.getCanal()).isEqualTo(CanalCobro.LLAMADA);
        assertThat(promesa.getPromesaPagoFecha()).isEqualTo(LocalDate.parse("2026-11-05"));
        assertThat(promesa.getPromesaPagoValor()).isEqualByComparingTo("1500000.00");

        LocalDate corte = LocalDate.parse("2026-10-29");
        assertThat(gestiones.contarPromesasDesde(EMPRESA, corte, null)).isEqualTo(1);
        assertThat(gestiones.contarPromesasDesde(EMPRESA, corte, 1L)).isEqualTo(1);
        assertThat(gestiones.contarPromesasDesde(EMPRESA, corte, 2L)).isZero();
        assertThat(gestiones.contarPromesasDesde(EMPRESA, corte.plusMonths(1), null)).isZero();

        Document crudo = mongo.getCollection(GestionCobro.COLECCION).find(Filters.eq("_id", 81L)).first();
        assertThat(crudo.get("promesaPagoValor")).isInstanceOf(Decimal128.class);
    }

    @Test
    void elEstadoDeCuentaEsUnicoPorContratoYCorteAunqueElMigradorEsteApagado() {
        // Sin migrador: el inicializador crea igual el índice único.
        new InicializadorIndicesMongo(List.of(tareas, gestiones, estados)).run(null);
        EstadoCuentaGenerado primero = nuevoEstado();
        estados.insertar(primero);

        assertThatThrownBy(() -> estados.insertar(nuevoEstado()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void copiaLosEstadosDeCuentaConSusSaldos() {
        assertThat(migradorEstados.migrar()).isEqualTo(1);
        assertThat(migradorEstados.migrar()).isZero();

        Document crudo = mongo.getCollection(EstadoCuentaGenerado.COLECCION).find(Filters.eq("_id", 90L)).first();
        assertThat(crudo.get("saldoTotal")).isInstanceOf(Decimal128.class);
        assertThat(((Decimal128) crudo.get("saldoTotal")).bigDecimalValue()).isEqualByComparingTo("80500000.00");
        EstadoCuentaGenerado estado = mongo.findById(90L, EstadoCuentaGenerado.class);
        assertThat(estado.getFechaCorte()).isEqualTo(LocalDate.parse("2026-09-30"));
        assertThat(estado.getArchivoId()).isEqualTo(400L);
        assertThat(secuencias.siguiente("estados_cuenta")).isEqualTo(91L);
    }

    private EstadoCuentaGenerado nuevoEstado() {
        EstadoCuentaGenerado estado = new EstadoCuentaGenerado(EMPRESA, 70L, LocalDate.parse("2026-10-29"),
                new BigDecimal("80000000.00"), BigDecimal.ZERO, 401L, 34L);
        estado.identificar(secuencias.siguiente("estados_cuenta"));
        return estado;
    }

    private void sembrarMySql() {
        jdbc.update("""
                INSERT INTO tareas (id, empresa_id, proyecto_id, asignado_a, creado_por, titulo, tipo, prioridad, estado,
                  fecha_vencimiento, version)
                VALUES (30, 8, 1, 34, 34, 'Llamar a Ana', 'SEGUIMIENTO', 'ALTA', 'EN_PROCESO',
                        '2026-10-01 17:00:00', 2),
                       (31, 8, NULL, 35, 34, 'Revisar planos', 'REVISION', 'MEDIA', 'COMPLETADA',
                        '2026-09-01 17:00:00', 1)
                """);
        jdbc.update("UPDATE tareas SET completada_en = '2026-08-30 10:00:00' WHERE id = 31");
        jdbc.update("""
                INSERT INTO tarea_historial (id, tarea_id, estado_anterior, estado_nuevo, usuario_id, comentario,
                  creado_en)
                VALUES (301, 30, 'PENDIENTE', 'EN_PROCESO', 34, 'Empecé', '2026-09-20 09:00:00'),
                       (300, 30, NULL, 'PENDIENTE', 34, 'Tarea creada', '2026-09-15 09:00:00')
                """);
        jdbc.update("""
                INSERT INTO contratos (id, empresa_id, proyecto_id, codigo, cliente_principal_id, lote_id,
                  tipo_contrato, valor_lote, valor_total, cuota_inicial, saldo_financiado, numero_cuotas,
                  fecha_inicio, estado)
                VALUES (70, 8, 1, 'CT-70', 40, 301, 'COMPRAVENTA', 100000000.00, 100000000.00, 20000000.00,
                        80000000.00, 24, '2026-01-15', 'VIGENTE')
                """);
        jdbc.update("""
                INSERT INTO gestiones_cobro (id, empresa_id, contrato_id, cuota_id, gestor_id, canal, resultado,
                  detalle, promesa_pago_fecha, promesa_pago_valor, fecha_gestion)
                VALUES (80, 8, 70, NULL, 34, 'SMS', 'Sin respuesta', NULL, NULL, NULL, '2026-10-20 10:00:00'),
                       (81, 8, 70, 700, 34, 'LLAMADA', 'Promete pagar', 'Paga el 5', '2026-11-05', 1500000.00,
                        '2026-10-28 15:00:00')
                """);
        jdbc.update("""
                INSERT INTO estados_cuenta_generados (id, empresa_id, contrato_id, fecha_corte, saldo_capital,
                  saldo_mora, saldo_total, archivo_id, generado_por)
                VALUES (90, 8, 70, '2026-09-30', 80000000.00, 500000.00, 80500000.00, 400, 34)
                """);
    }
}
