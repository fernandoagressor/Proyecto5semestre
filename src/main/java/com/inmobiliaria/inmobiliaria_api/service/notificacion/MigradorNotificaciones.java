package com.inmobiliaria.inmobiliaria_api.service.notificacion;

import com.inmobiliaria.inmobiliaria_api.entity.notificacion.Notificacion;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.notificacion.NotificacionRepository;
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
 * Copia a MongoDB las notificaciones que siguen solo en MySQL (rama 6), con sus envíos
 * embebidos, conservando ids y versión.
 *
 * <p>Se desactiva con {@code app.migracion.notificaciones.enabled=false}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.notificaciones", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigradorNotificaciones extends MigradorTablaMongo {

    static final String SECUENCIA_ENVIOS = "envios_notificacion";

    private final NotificacionRepository repository;

    public MigradorNotificaciones(JdbcTemplate jdbcTemplate,
                                  DocumentosMigradosRepository documentos,
                                  NotificacionRepository repository,
                                  SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return Notificacion.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return Notificacion.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        Map<String, String> secuencias = new LinkedHashMap<>();
        secuencias.put(NotificacionService.SECUENCIA, "notificaciones");
        secuencias.put(SECUENCIA_ENVIOS, "envios_notificacion");
        return secuencias;
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, usuario_id, tipo, titulo, mensaje, entidad_tipo, entidad_id,
                       prioridad, leida_en, expira_en, creado_en, actualizado_en, version
                FROM notificaciones
                WHERE id > ?
                ORDER BY id
                LIMIT ?
                """;
    }

    @Override
    protected Document raiz(ResultSet rs) throws SQLException {
        Document d = new Document();
        poner(d, "empresaId", largo(rs, "empresa_id"));
        poner(d, "usuarioId", largo(rs, "usuario_id"));
        poner(d, "tipo", rs.getString("tipo"));
        poner(d, "titulo", rs.getString("titulo"));
        poner(d, "mensaje", rs.getString("mensaje"));
        poner(d, "entidadTipo", rs.getString("entidad_tipo"));
        poner(d, "entidadId", largo(rs, "entidad_id"));
        poner(d, "prioridad", rs.getString("prioridad"));
        poner(d, "leidaEn", fecha(rs, "leida_en"));
        poner(d, "expiraEn", fecha(rs, "expira_en"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        poner(d, "actualizadoEn", fecha(rs, "actualizado_en"));
        d.put("version", rs.getLong("version"));
        return d;
    }

    @Override
    protected void completar(Map<Long, Document> porId) {
        Map<Long, List<Document>> envios = agrupar("""
                SELECT id, notificacion_id, plantilla_id, canal, destinatario, estado, proveedor,
                       referencia_externa, intentos, ultimo_error, enviado_en, entregado_en, creado_en
                FROM envios_notificacion WHERE notificacion_id IN (%s) ORDER BY creado_en, id
                """, "notificacion_id", porId.keySet(), rs -> {
            Document e = new Document(ID_EMBEBIDO, rs.getLong("id"));
            poner(e, "plantillaId", largo(rs, "plantilla_id"));
            poner(e, "canal", rs.getString("canal"));
            poner(e, "destinatario", rs.getString("destinatario"));
            poner(e, "estado", rs.getString("estado"));
            poner(e, "proveedor", rs.getString("proveedor"));
            poner(e, "referenciaExterna", rs.getString("referencia_externa"));
            e.put("intentos", rs.getInt("intentos"));
            poner(e, "ultimoError", rs.getString("ultimo_error"));
            poner(e, "enviadoEn", fecha(rs, "enviado_en"));
            poner(e, "entregadoEn", fecha(rs, "entregado_en"));
            poner(e, "creadoEn", fecha(rs, "creado_en"));
            return e;
        });
        porId.forEach((id, documento) -> documento.put("envios", envios.getOrDefault(id, List.of())));
    }
}
