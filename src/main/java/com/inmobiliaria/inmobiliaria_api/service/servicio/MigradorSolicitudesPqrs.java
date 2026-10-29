package com.inmobiliaria.inmobiliaria_api.service.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudPqrs;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.SolicitudPqrsRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.migracion.MigradorTablaMongo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Copia a MongoDB las PQRS que todavía están solo en MySQL (rama 5): cada solicitud con sus
 * mensajes, adjuntos, historial y la última encuesta embebidos, conservando ids y versión.
 *
 * <p>Se desactiva con {@code app.migracion.pqrs.enabled=false}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.pqrs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigradorSolicitudesPqrs extends MigradorTablaMongo {

    private final SolicitudPqrsRepository repository;

    public MigradorSolicitudesPqrs(JdbcTemplate jdbcTemplate,
                                   DocumentosMigradosRepository documentos,
                                   SolicitudPqrsRepository repository,
                                   SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return SolicitudPqrs.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return SolicitudPqrs.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        Map<String, String> secuencias = new LinkedHashMap<>();
        secuencias.put(SecuenciasPqrs.SOLICITUDES, "solicitudes_pqrs");
        secuencias.put(SecuenciasPqrs.MENSAJES, "solicitud_mensajes");
        secuencias.put(SecuenciasPqrs.ADJUNTOS, "solicitud_adjuntos");
        secuencias.put(SecuenciasPqrs.HISTORIAL, "solicitud_historial");
        return secuencias;
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, proyecto_id, codigo, cliente_id, contrato_id, asignado_a,
                       tipo, categoria, asunto, descripcion, prioridad, estado, fecha_limite,
                       fecha_primera_respuesta, fecha_resolucion, fecha_cierre, creado_en,
                       actualizado_en, version
                FROM solicitudes_pqrs
                WHERE id > ?
                ORDER BY id
                LIMIT ?
                """;
    }

    @Override
    protected Document raiz(ResultSet rs) throws SQLException {
        Document d = new Document();
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

    @Override
    protected void completar(Map<Long, Document> porId) {
        Map<Long, List<Document>> mensajes = agrupar("""
                SELECT id, solicitud_id, usuario_id, mensaje, es_interno, canal, creado_en
                FROM solicitud_mensajes WHERE solicitud_id IN (%s) ORDER BY creado_en, id
                """, "solicitud_id", porId.keySet(), rs -> {
            Document m = new Document(ID_EMBEBIDO, rs.getLong("id"));
            poner(m, "usuarioId", largo(rs, "usuario_id"));
            poner(m, "mensaje", rs.getString("mensaje"));
            m.put("interno", rs.getBoolean("es_interno"));
            poner(m, "canal", rs.getString("canal"));
            poner(m, "creadoEn", fecha(rs, "creado_en"));
            return m;
        });
        Map<Long, List<Document>> adjuntos = agrupar("""
                SELECT id, solicitud_id, mensaje_id, archivo_id, creado_en
                FROM solicitud_adjuntos WHERE solicitud_id IN (%s) ORDER BY creado_en, id
                """, "solicitud_id", porId.keySet(), rs -> {
            Document a = new Document(ID_EMBEBIDO, rs.getLong("id"));
            poner(a, "mensajeId", largo(rs, "mensaje_id"));
            poner(a, "archivoId", largo(rs, "archivo_id"));
            poner(a, "creadoEn", fecha(rs, "creado_en"));
            return a;
        });
        Map<Long, List<Document>> historial = agrupar("""
                SELECT id, solicitud_id, campo, valor_anterior, valor_nuevo, usuario_id, motivo, creado_en
                FROM solicitud_historial WHERE solicitud_id IN (%s) ORDER BY creado_en, id
                """, "solicitud_id", porId.keySet(), rs -> {
            Document h = new Document(ID_EMBEBIDO, rs.getLong("id"));
            poner(h, "campo", rs.getString("campo"));
            poner(h, "valorAnterior", rs.getString("valor_anterior"));
            poner(h, "valorNuevo", rs.getString("valor_nuevo"));
            poner(h, "usuarioId", largo(rs, "usuario_id"));
            poner(h, "motivo", rs.getString("motivo"));
            poner(h, "creadoEn", fecha(rs, "creado_en"));
            return h;
        });
        // El documento admite una sola encuesta: se queda la más reciente.
        Map<Long, List<Document>> encuestas = agrupar("""
                SELECT id, solicitud_id, cliente_id, puntuacion, comentario, respondida_en
                FROM encuestas_satisfaccion WHERE solicitud_id IN (%s) ORDER BY id
                """, "solicitud_id", porId.keySet(), rs -> {
            Document e = new Document();
            poner(e, "clienteId", largo(rs, "cliente_id"));
            e.put("puntuacion", rs.getInt("puntuacion"));
            poner(e, "comentario", rs.getString("comentario"));
            poner(e, "respondidaEn", fecha(rs, "respondida_en"));
            return e;
        });
        porId.forEach((id, documento) -> {
            documento.put("mensajes", mensajes.getOrDefault(id, List.of()));
            documento.put("adjuntos", adjuntos.getOrDefault(id, List.of()));
            documento.put("historial", historial.getOrDefault(id, List.of()));
            List<Document> respuestas = encuestas.get(id);
            if (respuestas != null && !respuestas.isEmpty()) {
                documento.put("encuesta", respuestas.get(respuestas.size() - 1));
            }
        });
    }
}
