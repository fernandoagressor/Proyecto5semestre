package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.Tarea;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.TareaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.migracion.MigradorTablaMongo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Copia a MongoDB las tareas que siguen solo en MySQL (rama 9), con su historial de
 * {@code tarea_historial} embebido. Se desactiva con {@code app.migracion.seguimiento.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.seguimiento", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class MigradorTareas extends MigradorTablaMongo {

    private final TareaRepository repository;

    public MigradorTareas(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
                          TareaRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return Tarea.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return Tarea.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(TareaService.SECUENCIA, "tareas", TareaService.SECUENCIA_HISTORIAL, "tarea_historial");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, proyecto_id, asignado_a, creado_por, titulo, descripcion, tipo, prioridad,
                       estado, fecha_vencimiento, entidad_tipo, entidad_id, regla_origen, completada_en, version,
                       creado_en, actualizado_en
                FROM tareas
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
        poner(d, "asignadoA", largo(rs, "asignado_a"));
        poner(d, "creadoPor", largo(rs, "creado_por"));
        poner(d, "titulo", rs.getString("titulo"));
        poner(d, "descripcion", rs.getString("descripcion"));
        poner(d, "tipo", rs.getString("tipo"));
        poner(d, "prioridad", rs.getString("prioridad"));
        poner(d, "estado", rs.getString("estado"));
        poner(d, "fechaVencimiento", fecha(rs, "fecha_vencimiento"));
        poner(d, "entidadTipo", rs.getString("entidad_tipo"));
        poner(d, "entidadId", largo(rs, "entidad_id"));
        poner(d, "reglaOrigen", rs.getString("regla_origen"));
        poner(d, "completadaEn", fecha(rs, "completada_en"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        poner(d, "actualizadoEn", fecha(rs, "actualizado_en"));
        d.put("version", rs.getLong("version"));
        return d;
    }

    @Override
    protected void completar(Map<Long, Document> porId) {
        Map<Long, List<Document>> historial = agrupar("""
                SELECT id, tarea_id, estado_anterior, estado_nuevo, usuario_id, comentario, creado_en
                FROM tarea_historial
                WHERE tarea_id IN (%s)
                ORDER BY tarea_id, creado_en, id
                """, "tarea_id", porId.keySet(), rs -> {
            Document h = new Document(ID_EMBEBIDO, rs.getLong("id"));
            h.put("tareaId", rs.getLong("tarea_id"));
            poner(h, "estadoAnterior", rs.getString("estado_anterior"));
            poner(h, "estadoNuevo", rs.getString("estado_nuevo"));
            poner(h, "usuarioId", largo(rs, "usuario_id"));
            poner(h, "comentario", rs.getString("comentario"));
            poner(h, "creadoEn", fecha(rs, "creado_en"));
            return h;
        });
        porId.forEach((id, documento) -> documento.put("historial", historial.getOrDefault(id, List.of())));
    }
}
