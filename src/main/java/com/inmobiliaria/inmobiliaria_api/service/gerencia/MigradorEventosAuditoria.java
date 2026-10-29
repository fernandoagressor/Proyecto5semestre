package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EventoAuditoria;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.EventoAuditoriaRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.migracion.MigradorTablaMongo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Copia a MongoDB las filas de `auditoria_eventos` que aún no están en la colección (rama 7),
 * conservando ids. Se desactiva con {@code app.migracion.auditoria.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.auditoria", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigradorEventosAuditoria extends MigradorTablaMongo {

    private final EventoAuditoriaRepository repository;

    public MigradorEventosAuditoria(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
              EventoAuditoriaRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return EventoAuditoria.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return EventoAuditoria.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(SecuenciasAuditoria.EVENTOS, "auditoria_eventos");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, proyecto_id, actor_id, modulo, accion, entidad_tipo, entidad_id,
                       descripcion, CAST(datos_anteriores_json AS CHAR) datos_anteriores_json,
                       CAST(datos_nuevos_json AS CHAR) datos_nuevos_json, ip_origen, agente_usuario,
                       correlation_id, ocurrido_en
                FROM auditoria_eventos
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
        poner(d, "actorId", largo(rs, "actor_id"));
        poner(d, "modulo", rs.getString("modulo"));
        poner(d, "accion", rs.getString("accion"));
        poner(d, "entidadTipo", rs.getString("entidad_tipo"));
        poner(d, "entidadId", largo(rs, "entidad_id"));
        poner(d, "descripcion", rs.getString("descripcion"));
        poner(d, "datosAnterioresJson", rs.getString("datos_anteriores_json"));
        poner(d, "datosNuevosJson", rs.getString("datos_nuevos_json"));
        poner(d, "ipOrigen", rs.getString("ip_origen"));
        poner(d, "agenteUsuario", rs.getString("agente_usuario"));
        poner(d, "correlationId", rs.getString("correlation_id"));
        poner(d, "ocurridoEn", fecha(rs, "ocurrido_en"));
        return d;
    }
}
