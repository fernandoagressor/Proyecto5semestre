package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.ArticuloConocimiento;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * Acceso a la colección {@code articulos_conocimiento} de MongoDB. Un artículo sin
 * {@code empresaId} es global.
 */
@Repository
public class ArticuloConocimientoRepository implements IndicesMongo {

    private final MongoTemplate mongoTemplate;

    public ArticuloConocimientoRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Filtro de lo que ve una empresa: lo global y lo suyo. */
    public static Criteria disponiblePara(Long empresaId) {
        return CategoriaConocimientoRepository.disponiblePara(empresaId);
    }

    public Optional<ArticuloConocimiento> findById(Long id) {
        return Optional.ofNullable(id == null ? null : mongoTemplate.findById(id, ArticuloConocimiento.class));
    }

    public Optional<ArticuloConocimiento> findDisponible(Long id, Long empresaId) {
        return Optional.ofNullable(mongoTemplate.findOne(
                query(new Criteria().andOperator(where("id").is(id), disponiblePara(empresaId))),
                ArticuloConocimiento.class));
    }

    /** Inserta; falla con {@code DuplicateKeyException} si el slug ya existe en esa empresa. */
    public ArticuloConocimiento insertar(ArticuloConocimiento articulo) {
        return mongoTemplate.insert(articulo);
    }

    /**
     * Guarda los cambios; lanza {@code OptimisticLockingFailureException} si otro lo guardó antes
     * o {@code DuplicateKeyException} si el slug nuevo ya existe.
     */
    public ArticuloConocimiento guardar(ArticuloConocimiento articulo) {
        articulo.tocar();
        return mongoTemplate.save(articulo);
    }

    public Page<ArticuloConocimiento> buscar(Criteria filtro, Pageable pagina) {
        long total = mongoTemplate.count(new Query(filtro), ArticuloConocimiento.class);
        Query consulta = new Query(filtro).with(pagina);
        return new PageImpl<>(mongoTemplate.find(consulta, ArticuloConocimiento.class), pagina, total);
    }

    @Override
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(ArticuloConocimiento.class);
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("slug", Sort.Direction.ASC)
                .unique().named("uk_articulos_conocimiento_empresa_slug"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("visibleClientes", Sort.Direction.ASC)
                .on("estado", Sort.Direction.ASC).on("publicadoEn", Sort.Direction.DESC)
                .named("idx_articulos_conocimiento_portal"));
        indices.ensureIndex(new Index().on("categoriaId", Sort.Direction.ASC).named("idx_articulos_conocimiento_categoria"));
    }
}
