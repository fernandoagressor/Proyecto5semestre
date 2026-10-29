package com.inmobiliaria.inmobiliaria_api.service.soporte;

import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketSoporte;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.soporte.TicketSoporteRepository;
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
 * Copia a MongoDB los tickets de soporte que siguen solo en MySQL (rama 6), con sus mensajes,
 * adjuntos e historial embebidos, conservando ids y versión.
 *
 * <p>Se desactiva con {@code app.migracion.soporte.enabled=false}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.soporte", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigradorTicketsSoporte extends MigradorTablaMongo {

    private final TicketSoporteRepository repository;

    public MigradorTicketsSoporte(JdbcTemplate jdbcTemplate,
                                  DocumentosMigradosRepository documentos,
                                  TicketSoporteRepository repository,
                                  SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return TicketSoporte.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return TicketSoporte.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        Map<String, String> secuencias = new LinkedHashMap<>();
        secuencias.put(TicketSoporteService.SECUENCIA_TICKETS, "tickets_soporte");
        secuencias.put(TicketSoporteService.SECUENCIA_MENSAJES, "ticket_mensajes");
        secuencias.put(TicketSoporteService.SECUENCIA_ADJUNTOS, "ticket_adjuntos");
        secuencias.put(TicketSoporteService.SECUENCIA_HISTORIAL, "ticket_historial");
        return secuencias;
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, proyecto_id, codigo, solicitante_id, asignado_a, asunto, categoria,
                       prioridad, descripcion, estado, fecha_primera_respuesta, fecha_resolucion,
                       fecha_cierre, creado_en, actualizado_en, version
                FROM tickets_soporte
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
        poner(d, "solicitanteId", largo(rs, "solicitante_id"));
        poner(d, "asignadoA", largo(rs, "asignado_a"));
        poner(d, "asunto", rs.getString("asunto"));
        poner(d, "categoria", rs.getString("categoria"));
        poner(d, "prioridad", rs.getString("prioridad"));
        poner(d, "descripcion", rs.getString("descripcion"));
        poner(d, "estado", rs.getString("estado"));
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
                SELECT id, ticket_id, usuario_id, mensaje, es_respuesta_interna, creado_en
                FROM ticket_mensajes WHERE ticket_id IN (%s) ORDER BY creado_en, id
                """, "ticket_id", porId.keySet(), rs -> {
            Document m = new Document(ID_EMBEBIDO, rs.getLong("id"));
            poner(m, "usuarioId", largo(rs, "usuario_id"));
            poner(m, "mensaje", rs.getString("mensaje"));
            m.put("interno", rs.getBoolean("es_respuesta_interna"));
            poner(m, "creadoEn", fecha(rs, "creado_en"));
            return m;
        });
        Map<Long, List<Document>> adjuntos = agrupar("""
                SELECT id, ticket_id, mensaje_id, archivo_id, creado_en
                FROM ticket_adjuntos WHERE ticket_id IN (%s) ORDER BY creado_en, id
                """, "ticket_id", porId.keySet(), rs -> {
            Document a = new Document(ID_EMBEBIDO, rs.getLong("id"));
            poner(a, "mensajeId", largo(rs, "mensaje_id"));
            poner(a, "archivoId", largo(rs, "archivo_id"));
            poner(a, "creadoEn", fecha(rs, "creado_en"));
            return a;
        });
        Map<Long, List<Document>> historial = agrupar("""
                SELECT id, ticket_id, campo, valor_anterior, valor_nuevo, usuario_id, motivo, creado_en
                FROM ticket_historial WHERE ticket_id IN (%s) ORDER BY creado_en, id
                """, "ticket_id", porId.keySet(), rs -> {
            Document h = new Document(ID_EMBEBIDO, rs.getLong("id"));
            poner(h, "campo", rs.getString("campo"));
            poner(h, "valorAnterior", rs.getString("valor_anterior"));
            poner(h, "valorNuevo", rs.getString("valor_nuevo"));
            poner(h, "usuarioId", largo(rs, "usuario_id"));
            poner(h, "motivo", rs.getString("motivo"));
            poner(h, "creadoEn", fecha(rs, "creado_en"));
            return h;
        });
        porId.forEach((id, documento) -> {
            documento.put("mensajes", mensajes.getOrDefault(id, List.of()));
            documento.put("adjuntos", adjuntos.getOrDefault(id, List.of()));
            documento.put("historial", historial.getOrDefault(id, List.of()));
        });
    }
}
