package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.ReporteGenerado;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.ReporteGeneradoRepository;
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
 * Copia a MongoDB las filas de `reportes_generados` que aún no están en la colección (rama 7),
 * conservando ids. Se desactiva con {@code app.migracion.auditoria.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.auditoria", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigradorReportesGenerados extends MigradorTablaMongo {

    private final ReporteGeneradoRepository repository;

    public MigradorReportesGenerados(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
              ReporteGeneradoRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return ReporteGenerado.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return ReporteGenerado.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(SecuenciasAuditoria.REPORTES, "reportes_generados");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, proyecto_id, solicitado_por, tipo_reporte,
                       CAST(filtros_json AS CHAR) filtros_json, formato, archivo_id, estado, generado_en,
                       expira_en, detalle_error, creado_en, actualizado_en, version
                FROM reportes_generados
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
        poner(d, "solicitadoPor", largo(rs, "solicitado_por"));
        poner(d, "tipoReporte", rs.getString("tipo_reporte"));
        poner(d, "filtrosJson", rs.getString("filtros_json"));
        poner(d, "formato", rs.getString("formato"));
        poner(d, "archivoId", largo(rs, "archivo_id"));
        poner(d, "estado", rs.getString("estado"));
        poner(d, "generadoEn", fecha(rs, "generado_en"));
        poner(d, "expiraEn", fecha(rs, "expira_en"));
        poner(d, "detalleError", rs.getString("detalle_error"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        poner(d, "actualizadoEn", fecha(rs, "actualizado_en"));
        d.put("version", rs.getLong("version"));
        return d;
    }
}
