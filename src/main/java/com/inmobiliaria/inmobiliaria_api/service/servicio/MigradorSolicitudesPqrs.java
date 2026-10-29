package com.inmobiliaria.inmobiliaria_api.service.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudPqrs;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.SolicitudPqrsRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Copia a MongoDB las PQRS que todavía están solo en MySQL (rama 5).
 *
 * <ul>
 *   <li>Corre al arrancar y es idempotente: solo inserta las solicitudes cuyo id aún no existe
 *       en MongoDB, así que no pisa lo que ya se atendió en la colección nueva.</li>
 *   <li>Cada solicitud se arma con sus mensajes, adjuntos, historial y la última encuesta
 *       embebidos, conservando los ids y la versión de MySQL.</li>
 *   <li>Antes de copiar sube las secuencias de {@code contadores} al id máximo de cada tabla,
 *       para que una solicitud nueva nunca reciba un id que ya usó MySQL.</li>
 * </ul>
 *
 * <p>Las fechas se convierten con el mismo conversor de Spring Data que usa el repositorio,
 * así un documento migrado se lee igual que uno creado por la aplicación.</p>
 *
 * <p>Se desactiva con {@code app.migracion.pqrs.enabled=false}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.pqrs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigradorSolicitudesPqrs implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigradorSolicitudesPqrs.class);
    static final int LOTE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final SolicitudPqrsRepository repository;
    private final SecuenciaService secuencias;

    public MigradorSolicitudesPqrs(JdbcTemplate jdbcTemplate,
                                   SolicitudPqrsRepository repository,
                                   SecuenciaService secuencias) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
        this.secuencias = secuencias;
    }

    @Override
    public void run(ApplicationArguments args) {
        int migradas = migrar();
        if (migradas > 0) {
            log.info("Se copiaron {} solicitudes PQRS de MySQL a MongoDB", migradas);
        }
    }

    /** @return cuántas solicitudes se insertaron en MongoDB en esta ejecución */
    public int migrar() {
        repository.asegurarIndices();
        asegurarSecuencias();
        int migradas = 0;
        long ultimoId = 0;
        while (true) {
            List<Map<String, Object>> lote = jdbcTemplate.query("""
                    SELECT id, empresa_id, proyecto_id, codigo, cliente_id, contrato_id, asignado_a,
                           tipo, categoria, asunto, descripcion, prioridad, estado, fecha_limite,
                           fecha_primera_respuesta, fecha_resolucion, fecha_cierre, creado_en,
                           actualizado_en, version
                    FROM solicitudes_pqrs
                    WHERE id > ?
                    ORDER BY id
                    LIMIT ?
                    """, (rs, fila) -> solicitud(rs), ultimoId, LOTE);
            if (lote.isEmpty()) {
                return migradas;
            }
            ultimoId = (Long) lote.get(lote.size() - 1).get("_id");
            List<Long> ids = lote.stream().map(s -> (Long) s.get("_id")).toList();
            Set<Long> existentes = repository.idsExistentes(ids);
            List<Long> faltantes = ids.stream().filter(id -> !existentes.contains(id)).toList();
            if (faltantes.isEmpty()) {
                continue;
            }
            Map<Long, List<Document>> mensajes = mensajes(faltantes);
            Map<Long, List<Document>> adjuntos = adjuntos(faltantes);
            Map<Long, List<Document>> historial = historial(faltantes);
            Map<Long, Document> encuestas = encuestas(faltantes);
            List<Document> documentos = new ArrayList<>();
            for (Map<String, Object> datos : lote) {
                Long id = (Long) datos.get("_id");
                if (existentes.contains(id)) {
                    continue;
                }
                Document documento = new Document(datos);
                documento.put("mensajes", mensajes.getOrDefault(id, List.of()));
                documento.put("adjuntos", adjuntos.getOrDefault(id, List.of()));
                documento.put("historial", historial.getOrDefault(id, List.of()));
                poner(documento, "encuesta", encuestas.get(id));
                documentos.add(documento);
            }
            migradas += repository.insertarMigrados(documentos);
        }
    }

    private void asegurarSecuencias() {
        secuencias.asegurarMinimo(SecuenciasPqrs.SOLICITUDES, maximo("solicitudes_pqrs"));
        secuencias.asegurarMinimo(SecuenciasPqrs.MENSAJES, maximo("solicitud_mensajes"));
        secuencias.asegurarMinimo(SecuenciasPqrs.ADJUNTOS, maximo("solicitud_adjuntos"));
        secuencias.asegurarMinimo(SecuenciasPqrs.HISTORIAL, maximo("solicitud_historial"));
    }

    private long maximo(String tabla) {
        Long maximo = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + tabla, Long.class);
        return maximo == null ? 0 : maximo;
    }

    private Map<String, Object> solicitud(ResultSet rs) throws SQLException {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("_id", rs.getLong("id"));
        d.put("_class", SolicitudPqrs.class.getName());
        poner(d, "empresaId", largo(rs, "empresa_id"));
        poner(d, "proyectoId", largo(rs, "proyecto_id"));
        poner(d, "codigo", rs.getString("codigo"));
        poner(d, "clienteId", largo(rs, "cliente_id"));
        poner(d, "contratoId", largo(rs, "contrato_id"));
        poner(d, "asignadoA", largo(rs, "asignado_a"));
        poner(d, "tipo", rs.getString("tipo"));
        poner(d, "categoria", rs.getString("categoria"));
        poner(d, "asunto", rs.getString("asunto"));
        poner(d, "descripcion", rs.getString("descripcion"));
        poner(d, "prioridad", rs.getString("prioridad"));
        poner(d, "estado", rs.getString("estado"));
        poner(d, "fechaLimite", fecha(rs, "fecha_limite"));
        poner(d, "fechaPrimeraRespuesta", fecha(rs, "fecha_primera_respuesta"));
        poner(d, "fechaResolucion", fecha(rs, "fecha_resolucion"));
        poner(d, "fechaCierre", fecha(rs, "fecha_cierre"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        poner(d, "actualizadoEn", fecha(rs, "actualizado_en"));
        d.put("version", rs.getLong("version"));
        return d;
    }

    private Map<Long, List<Document>> mensajes(Collection<Long> solicitudes) {
        return agrupar("""
                SELECT id, solicitud_id, usuario_id, mensaje, es_interno, canal, creado_en
                FROM solicitud_mensajes WHERE solicitud_id IN (%s) ORDER BY creado_en, id
                """, solicitudes, rs -> {
            Document m = new Document("id", rs.getLong("id"));
            poner(m, "usuarioId", largo(rs, "usuario_id"));
            poner(m, "mensaje", rs.getString("mensaje"));
            m.put("interno", rs.getBoolean("es_interno"));
            poner(m, "canal", rs.getString("canal"));
            poner(m, "creadoEn", fecha(rs, "creado_en"));
            return m;
        });
    }

    private Map<Long, List<Document>> adjuntos(Collection<Long> solicitudes) {
        return agrupar("""
                SELECT id, solicitud_id, mensaje_id, archivo_id, creado_en
                FROM solicitud_adjuntos WHERE solicitud_id IN (%s) ORDER BY creado_en, id
                """, solicitudes, rs -> {
            Document a = new Document("id", rs.getLong("id"));
            poner(a, "mensajeId", largo(rs, "mensaje_id"));
            poner(a, "archivoId", largo(rs, "archivo_id"));
            poner(a, "creadoEn", fecha(rs, "creado_en"));
            return a;
        });
    }

    private Map<Long, List<Document>> historial(Collection<Long> solicitudes) {
        return agrupar("""
                SELECT id, solicitud_id, campo, valor_anterior, valor_nuevo, usuario_id, motivo, creado_en
                FROM solicitud_historial WHERE solicitud_id IN (%s) ORDER BY creado_en, id
                """, solicitudes, rs -> {
            Document h = new Document("id", rs.getLong("id"));
            poner(h, "campo", rs.getString("campo"));
            poner(h, "valorAnterior", rs.getString("valor_anterior"));
            poner(h, "valorNuevo", rs.getString("valor_nuevo"));
            poner(h, "usuarioId", largo(rs, "usuario_id"));
            poner(h, "motivo", rs.getString("motivo"));
            poner(h, "creadoEn", fecha(rs, "creado_en"));
            return h;
        });
    }

    /** La última encuesta de cada solicitud (el documento admite una sola). */
    private Map<Long, Document> encuestas(Collection<Long> solicitudes) {
        Map<Long, Document> porSolicitud = new HashMap<>();
        agrupar("""
                SELECT id, solicitud_id, cliente_id, puntuacion, comentario, respondida_en
                FROM encuestas_satisfaccion WHERE solicitud_id IN (%s) ORDER BY id
                """, solicitudes, rs -> {
            Document e = new Document();
            poner(e, "clienteId", largo(rs, "cliente_id"));
            e.put("puntuacion", rs.getInt("puntuacion"));
            poner(e, "comentario", rs.getString("comentario"));
            poner(e, "respondidaEn", fecha(rs, "respondida_en"));
            return e;
        }).forEach((solicitud, lista) -> porSolicitud.put(solicitud, lista.get(lista.size() - 1)));
        return porSolicitud;
    }

    private Map<Long, List<Document>> agrupar(String sql, Collection<Long> solicitudes, Fila fila) {
        String marcadores = solicitudes.stream().map(id -> "?").collect(Collectors.joining(","));
        Map<Long, List<Document>> grupos = new HashMap<>();
        jdbcTemplate.query(sql.formatted(marcadores), rs -> {
            grupos.computeIfAbsent(rs.getLong("solicitud_id"), id -> new ArrayList<>()).add(fila.leer(rs));
        }, solicitudes.toArray());
        return grupos;
    }

    private Object fecha(ResultSet rs, String columna) throws SQLException {
        return repository.valorMongo(rs.getObject(columna, LocalDateTime.class));
    }

    private static Long largo(ResultSet rs, String columna) throws SQLException {
        long valor = rs.getLong(columna);
        return rs.wasNull() ? null : valor;
    }

    /** Spring Data no escribe los campos nulos; el migrador hace lo mismo. */
    private static void poner(Map<String, Object> documento, String campo, Object valor) {
        if (valor != null) {
            documento.put(campo, valor);
        }
    }

    @FunctionalInterface
    private interface Fila {
        Document leer(ResultSet rs) throws SQLException;
    }
}
