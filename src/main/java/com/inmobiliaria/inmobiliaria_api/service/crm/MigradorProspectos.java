package com.inmobiliaria.inmobiliaria_api.service.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.Prospecto;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.DocumentosMigradosRepository;
import com.inmobiliaria.inmobiliaria_api.repository.crm.ProspectoRepository;
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
 * Copia a MongoDB los prospectos que siguen solo en MySQL (rama 8), conservando ids y versión.
 * Se desactiva con {@code app.migracion.crm.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.crm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigradorProspectos extends MigradorTablaMongo {

    private final ProspectoRepository repository;

    public MigradorProspectos(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
                              ProspectoRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return Prospecto.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return Prospecto.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(SecuenciasCrm.PROSPECTOS, "prospectos");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, proyecto_id, persona_id, cliente_id, asesor_id, nombres, telefono, correo,
                       fuente, presupuesto, temperatura, estado, observaciones, creado_en, actualizado_en, version
                FROM prospectos
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
        poner(d, "personaId", largo(rs, "persona_id"));
        poner(d, "clienteId", largo(rs, "cliente_id"));
        poner(d, "asesorId", largo(rs, "asesor_id"));
        poner(d, "nombres", rs.getString("nombres"));
        poner(d, "telefono", rs.getString("telefono"));
        poner(d, "correo", rs.getString("correo"));
        poner(d, "fuente", rs.getString("fuente"));
        poner(d, "presupuesto", decimal(rs, "presupuesto"));
        poner(d, "temperatura", rs.getString("temperatura"));
        poner(d, "estado", rs.getString("estado"));
        poner(d, "observaciones", rs.getString("observaciones"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        poner(d, "actualizadoEn", fecha(rs, "actualizado_en"));
        d.put("version", rs.getLong("version"));
        return d;
    }
}
