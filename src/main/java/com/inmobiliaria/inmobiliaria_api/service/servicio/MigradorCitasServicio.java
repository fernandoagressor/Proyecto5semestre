package com.inmobiliaria.inmobiliaria_api.service.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.CitaServicio;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.CitaServicioRepository;
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

/**
 * Copia a MongoDB las citas de servicio que siguen solo en MySQL (rama 10).
 * Se desactiva con {@code app.migracion.servicio.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.servicio", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class MigradorCitasServicio extends MigradorTablaMongo {

    private final CitaServicioRepository repository;

    public MigradorCitasServicio(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
            CitaServicioRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return CitaServicio.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return CitaServicio.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(CitaServicioService.SECUENCIA, "citas_servicio");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, proyecto_id, cliente_id, solicitud_id, responsable_id, tipo, fecha_inicio, fecha_fin,
                       lugar, estado, resultado, version, creado_en, actualizado_en
                FROM citas_servicio
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
        poner(d, "clienteId", largo(rs, "cliente_id"));
        poner(d, "solicitudId", largo(rs, "solicitud_id"));
        poner(d, "responsableId", largo(rs, "responsable_id"));
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
