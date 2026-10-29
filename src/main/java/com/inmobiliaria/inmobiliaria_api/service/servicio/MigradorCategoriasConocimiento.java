package com.inmobiliaria.inmobiliaria_api.service.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.CategoriaConocimiento;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.CategoriaConocimientoRepository;
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
 * Copia a MongoDB las categorías de la base de conocimiento (globales y de empresa) que siguen solo en MySQL (rama 10).
 * Se desactiva con {@code app.migracion.servicio.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.servicio", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class MigradorCategoriasConocimiento extends MigradorTablaMongo {

    private final CategoriaConocimientoRepository repository;

    public MigradorCategoriasConocimiento(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
            CategoriaConocimientoRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return CategoriaConocimiento.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return CategoriaConocimiento.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(BaseConocimientoService.SECUENCIA_CATEGORIAS, "categorias_conocimiento");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, nombre, descripcion, orden, activo, creado_en, actualizado_en
                FROM categorias_conocimiento
                WHERE id > ?
                ORDER BY id
                LIMIT ?
                """;
    }

    @Override
    protected Document raiz(ResultSet rs) throws SQLException {
        Document d = new Document();
        poner(d, "empresaId", largo(rs, "empresa_id"));
        poner(d, "nombre", rs.getString("nombre"));
        poner(d, "descripcion", rs.getString("descripcion"));
        d.put("orden", rs.getInt("orden"));
        d.put("activo", rs.getBoolean("activo"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        poner(d, "actualizadoEn", fecha(rs, "actualizado_en"));
        return d;
    }
}
