package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.ExportacionAuditoria;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.ExportacionAuditoriaRepository;
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
 * Copia a MongoDB las filas de `exportaciones_auditoria` que aún no están en la colección (rama 7),
 * conservando ids. Se desactiva con {@code app.migracion.auditoria.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.auditoria", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigradorExportacionesAuditoria extends MigradorTablaMongo {

    private final ExportacionAuditoriaRepository repository;

    public MigradorExportacionesAuditoria(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
              ExportacionAuditoriaRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return ExportacionAuditoria.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return ExportacionAuditoria.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(SecuenciasAuditoria.EXPORTACIONES, "exportaciones_auditoria");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, usuario_id, CAST(filtros_json AS CHAR) filtros_json, formato,
                       archivo_id, motivo, estado, expira_en, creado_en, actualizado_en
                FROM exportaciones_auditoria
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
        poner(d, "filtrosJson", rs.getString("filtros_json"));
        poner(d, "formato", rs.getString("formato"));
        poner(d, "archivoId", largo(rs, "archivo_id"));
        poner(d, "motivo", rs.getString("motivo"));
        poner(d, "estado", rs.getString("estado"));
        poner(d, "expiraEn", fecha(rs, "expira_en"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        poner(d, "actualizadoEn", fecha(rs, "actualizado_en"));
        return d;
    }
}
