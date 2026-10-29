package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.CategoriaConocimiento;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

/**
 * Acceso a la colección {@code categorias_conocimiento} de MongoDB. Una categoría sin
 * {@code empresaId} es global (la ven todas las empresas).
 */
@Repository
public class CategoriaConocimientoRepository implements IndicesMongo {

    private final MongoTemplate mongoTemplate;

    public CategoriaConocimientoRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Filtro de lo que ve una empresa: lo global y lo suyo. */
    static Criteria disponiblePara(Long empresaId) {
        return new Criteria().orOperator(where("empresaId").is(null), where("empresaId").is(empresaId));
    }

    public List<CategoriaConocimiento> listarDisponibles(Long empresaId) {
        return mongoTemplate.find(query(new Criteria().andOperator(disponiblePara(empresaId), where("activo").is(true)))
                .with(Sort.by("orden", "nombre")), CategoriaConocimiento.class);
    }

    public Optional<CategoriaConocimiento> findDisponible(Long id, Long empresaId) {
        return Optional.ofNullable(mongoTemplate.findOne(
                query(new Criteria().andOperator(where("id").is(id), disponiblePara(empresaId))),
                CategoriaConocimiento.class));
    }

    /** Inserta; falla con {@code DuplicateKeyException} si la empresa ya tiene una categoría con ese nombre. */
    public CategoriaConocimiento insertar(CategoriaConocimiento categoria) {
        return mongoTemplate.insert(categoria);
    }

    @Override
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(CategoriaConocimiento.class);
        // Sin empresaId cuenta como null: el nombre también es único entre las globales.
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("nombre", Sort.Direction.ASC)
                .unique().named("uk_categorias_conocimiento_empresa_nombre"));
    }
}
