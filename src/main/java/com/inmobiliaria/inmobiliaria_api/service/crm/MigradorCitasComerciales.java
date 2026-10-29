package com.inmobiliaria.inmobiliaria_api.service.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.CitaComercial;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.crm.CitaComercialRepository;
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
 * Copia a MongoDB las citas comerciales que siguen solo en MySQL (rama 8). Toma la empresa y el
 * proyecto de la oportunidad para que la agenda pueda filtrarse por alcance.
 * Se desactiva con {@code app.migracion.crm.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.crm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigradorCitasComerciales extends MigradorTablaMongo {

    private final CitaComercialRepository repository;

    public MigradorCitasComerciales(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
                                    CitaComercialRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return CitaComercial.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return CitaComercial.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(SecuenciasCrm.CITAS, "citas_comerciales");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT c.id, o.empresa_id, o.proyecto_id, c.oportunidad_id, c.asesor_id, c.tipo, c.fecha_inicio,
                       c.fecha_fin, c.lugar, c.estado, c.resultado, c.creado_en, c.actualizado_en, c.version
                FROM citas_comerciales c
                JOIN oportunidades o ON o.id = c.oportunidad_id
                WHERE c.id > ?
                ORDER BY c.id
                LIMIT ?
                """;
    }

    @Override
    protected Document raiz(ResultSet rs) throws SQLException {
        Document d = new Document();
        poner(d, "empresaId", largo(rs, "empresa_id"));
        poner(d, "proyectoId", largo(rs, "proyecto_id"));
        poner(d, "oportunidadId", largo(rs, "oportunidad_id"));
        poner(d, "asesorId", largo(rs, "asesor_id"));
        poner(d, "tipo", rs.getString("tipo"));
        poner(d, "fechaInicio", fecha(rs, "fecha_inicio"));
        poner(d, "fechaFin", fecha(rs, "fecha_fin"));
        poner(d, "lugar", rs.getString("lugar"));
        poner(d, "estado", rs.getString("estado"));
        poner(d, "resultado", rs.getString("resultado"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        poner(d, "actualizadoEn", fecha(rs, "actualizado_en"));
        d.put("version", rs.getLong("version"));
        return d;
    }
}
