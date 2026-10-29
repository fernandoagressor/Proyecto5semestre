package com.inmobiliaria.inmobiliaria_api.service.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.Oportunidad;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.crm.OportunidadRepository;
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
 * Copia a MongoDB las oportunidades que siguen solo en MySQL (rama 8), con sus lotes de interés,
 * historial de etapas, actividades y presentaciones embebidos, conservando ids y versión.
 * Se desactiva con {@code app.migracion.crm.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.crm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigradorOportunidades extends MigradorTablaMongo {

    private final OportunidadRepository repository;

    public MigradorOportunidades(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
                                 OportunidadRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return Oportunidad.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return Oportunidad.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        Map<String, String> secuencias = new LinkedHashMap<>();
        secuencias.put(SecuenciasCrm.OPORTUNIDADES, "oportunidades");
        secuencias.put(SecuenciasCrm.LOTES_INTERES, "oportunidad_lotes");
        secuencias.put(SecuenciasCrm.HISTORIAL_ETAPAS, "oportunidad_etapa_historial");
        secuencias.put(SecuenciasCrm.ACTIVIDADES, "actividades_comerciales");
        return secuencias;
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, proyecto_id, codigo, prospecto_id, cliente_id, asesor_id, etapa_actual,
                       estado, probabilidad_cierre, valor_estimado, fecha_ultima_actividad, fecha_proxima_accion,
                       motivo_perdida, fecha_cierre, creado_en, actualizado_en, version
                FROM oportunidades
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
        poner(d, "prospectoId", largo(rs, "prospecto_id"));
        poner(d, "clienteId", largo(rs, "cliente_id"));
        poner(d, "asesorId", largo(rs, "asesor_id"));
        poner(d, "etapaActual", rs.getString("etapa_actual"));
        poner(d, "estado", rs.getString("estado"));
        poner(d, "probabilidadCierre", decimal(rs, "probabilidad_cierre"));
        poner(d, "valorEstimado", decimal(rs, "valor_estimado"));
        poner(d, "fechaUltimaActividad", fecha(rs, "fecha_ultima_actividad"));
        poner(d, "fechaProximaAccion", fecha(rs, "fecha_proxima_accion"));
        poner(d, "motivoPerdida", rs.getString("motivo_perdida"));
        poner(d, "fechaCierre", fecha(rs, "fecha_cierre"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        poner(d, "actualizadoEn", fecha(rs, "actualizado_en"));
        d.put("version", rs.getLong("version"));
        return d;
    }

    @Override
    protected void completar(Map<Long, Document> porId) {
        Map<Long, List<Document>> lotes = agrupar("""
                SELECT id, oportunidad_id, lote_id, prioridad, seleccionado, creado_en
                FROM oportunidad_lotes WHERE oportunidad_id IN (%s) ORDER BY prioridad, id
                """, "oportunidad_id", porId.keySet(), rs -> {
            Document l = new Document(ID_EMBEBIDO, rs.getLong("id"));
            l.put("oportunidadId", rs.getLong("oportunidad_id"));
            poner(l, "loteId", largo(rs, "lote_id"));
            l.put("prioridad", rs.getInt("prioridad"));
            l.put("seleccionado", rs.getBoolean("seleccionado"));
            poner(l, "creadoEn", fecha(rs, "creado_en"));
            return l;
        });
        Map<Long, List<Document>> historial = agrupar("""
                SELECT id, oportunidad_id, etapa_anterior, etapa_nueva, usuario_id, observacion, creado_en
                FROM oportunidad_etapa_historial WHERE oportunidad_id IN (%s) ORDER BY creado_en, id
                """, "oportunidad_id", porId.keySet(), rs -> {
            Document h = new Document(ID_EMBEBIDO, rs.getLong("id"));
            h.put("oportunidadId", rs.getLong("oportunidad_id"));
            poner(h, "etapaAnterior", rs.getString("etapa_anterior"));
            poner(h, "etapaNueva", rs.getString("etapa_nueva"));
            poner(h, "usuarioId", largo(rs, "usuario_id"));
            poner(h, "observacion", rs.getString("observacion"));
            poner(h, "creadoEn", fecha(rs, "creado_en"));
            return h;
        });
        Map<Long, List<Document>> actividades = agrupar("""
                SELECT id, oportunidad_id, asesor_id, tipo, resultado, descripcion, fecha_actividad,
                       proxima_accion, fecha_proxima_accion, creado_en
                FROM actividades_comerciales WHERE oportunidad_id IN (%s) ORDER BY fecha_actividad, id
                """, "oportunidad_id", porId.keySet(), rs -> {
            Document a = new Document(ID_EMBEBIDO, rs.getLong("id"));
            a.put("oportunidadId", rs.getLong("oportunidad_id"));
            poner(a, "asesorId", largo(rs, "asesor_id"));
            poner(a, "tipo", rs.getString("tipo"));
            poner(a, "resultado", rs.getString("resultado"));
            poner(a, "descripcion", rs.getString("descripcion"));
            poner(a, "fechaActividad", fecha(rs, "fecha_actividad"));
            poner(a, "proximaAccion", rs.getString("proxima_accion"));
            poner(a, "fechaProximaAccion", fecha(rs, "fecha_proxima_accion"));
            poner(a, "creadoEn", fecha(rs, "creado_en"));
            return a;
        });
        // Las presentaciones no tienen código que las use; se conservan tal cual como evidencia.
        Map<Long, List<Document>> presentaciones = agrupar("""
                SELECT id, oportunidad_id, proyecto_id, asesor_id, fecha_presentacion, resultado, nivel_interes,
                       CAST(materiales_json AS CHAR) materiales_json, observaciones, creado_en
                FROM presentaciones_comerciales WHERE oportunidad_id IN (%s) ORDER BY fecha_presentacion, id
                """, "oportunidad_id", porId.keySet(), rs -> {
            Document p = new Document("id", rs.getLong("id"));
            poner(p, "proyectoId", largo(rs, "proyecto_id"));
            poner(p, "asesorId", largo(rs, "asesor_id"));
            poner(p, "fechaPresentacion", fecha(rs, "fecha_presentacion"));
            poner(p, "resultado", rs.getString("resultado"));
            poner(p, "nivelInteres", rs.getString("nivel_interes"));
            poner(p, "materialesJson", rs.getString("materiales_json"));
            poner(p, "observaciones", rs.getString("observaciones"));
            poner(p, "creadoEn", fecha(rs, "creado_en"));
            return p;
        });
        porId.forEach((id, documento) -> {
            documento.put("lotesInteres", lotes.getOrDefault(id, List.of()));
            documento.put("historialEtapas", historial.getOrDefault(id, List.of()));
            documento.put("actividades", actividades.getOrDefault(id, List.of()));
            documento.put("presentaciones", presentaciones.getOrDefault(id, List.of()));
        });
    }
}
