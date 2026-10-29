package com.inmobiliaria.inmobiliaria_api.service.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.ArticuloConocimiento;
import com.inmobiliaria.inmobiliaria_api.repository.servicio.ArticuloConocimientoRepository;
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
 * Copia a MongoDB los artículos de la base de conocimiento (globales y de empresa) que siguen solo en MySQL (rama 10).
 * Se desactiva con {@code app.migracion.servicio.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.migracion.servicio", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class MigradorArticulosConocimiento extends MigradorTablaMongo {

    private final ArticuloConocimientoRepository repository;

    public MigradorArticulosConocimiento(JdbcTemplate jdbcTemplate, DocumentosMigradosRepository documentos,
            ArticuloConocimientoRepository repository, SecuenciaService secuencias) {
        super(jdbcTemplate, documentos, secuencias);
        this.repository = repository;
    }

    @Override
    protected String coleccion() {
        return ArticuloConocimiento.COLECCION;
    }

    @Override
    protected Class<?> claseDocumento() {
        return ArticuloConocimiento.class;
    }

    @Override
    protected Map<String, String> secuencias() {
        return Map.of(BaseConocimientoService.SECUENCIA_ARTICULOS, "articulos_conocimiento");
    }

    @Override
    protected void prepararColeccion() {
        repository.asegurarIndices();
    }

    @Override
    protected String consultaRaiz() {
        return """
                SELECT id, empresa_id, categoria_id, titulo, slug, contenido, visible_clientes, estado, autor_id, publicado_en,
                       version, creado_en, actualizado_en
                FROM articulos_conocimiento
                WHERE id > ?
                ORDER BY id
                LIMIT ?
                """;
    }

    @Override
    protected Document raiz(ResultSet rs) throws SQLException {
        Document d = new Document();
        poner(d, "empresaId", largo(rs, "empresa_id"));
        poner(d, "categoriaId", largo(rs, "categoria_id"));
        poner(d, "titulo", rs.getString("titulo"));
        poner(d, "slug", rs.getString("slug"));
        poner(d, "contenido", rs.getString("contenido"));
        d.put("visibleClientes", rs.getBoolean("visible_clientes"));
        poner(d, "estado", rs.getString("estado"));
        poner(d, "autorId", largo(rs, "autor_id"));
        poner(d, "publicadoEn", fecha(rs, "publicado_en"));
        poner(d, "creadoEn", fecha(rs, "creado_en"));
        poner(d, "actualizadoEn", fecha(rs, "actualizado_en"));
        d.put("version", rs.getLong("version"));
        return d;
    }
}
