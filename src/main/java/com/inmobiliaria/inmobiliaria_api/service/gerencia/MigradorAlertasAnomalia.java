package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.AlertaAnomalia;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.AlertaAnomaliaRepository;
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
 * Copia a MongoDB las filas de `alertas_anomalia` que aún no están en la colección (rama 7),
 * conservando ids. Se desactiva con {@code app.migracion.auditoria.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.auditoria", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigradorAlertasAnomalia extends MigradorTablaMongo {

    private final AlertaAnomaliaRepository repository;

    public MigradorAlertasAnomalia(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
              AlertaAnomaliaRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return AlertaAnomalia.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return AlertaAnomalia.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(SecuenciasAuditoria.ALERTAS, "alertas_anomalia");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, tipo, severidad, usuario_id, evento_auditoria_id, descripcion,
                       estado, asignado_a, resuelta_en, resolucion, creado_en, actualizado_en, version
                FROM alertas_anomalia
                WHERE id > ?
                ORDER BY id
                LIMIT ?
                """;
    }

    @Override
    protected Document raiz(ResultSet rs) throws SQLException {
        Document d = new Document();
        poner(d, "empresaId", largo(rs, "empresa_id"));
        poner(d, "tipo", rs.getString("tipo"));
        poner(d, "severidad", rs.getString("severidad"));
        poner(d, "usuarioId", largo(rs, "usuario_id"));
        poner(d, "eventoAuditoriaId", largo(rs, "evento_auditoria_id"));
        poner(d, "descripcion", rs.getString("descripcion"));
        poner(d, "estado", rs.getString("estado"));
        poner(d, "asignadoA", largo(rs, "asignado_a"));
        poner(d, "resueltaEn", fecha(rs, "resuelta_en"));
        poner(d, "resolucion", rs.getString("resolucion"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        poner(d, "actualizadoEn", fecha(rs, "actualizado_en"));
        d.put("version", rs.getLong("version"));
        return d;
    }
}
