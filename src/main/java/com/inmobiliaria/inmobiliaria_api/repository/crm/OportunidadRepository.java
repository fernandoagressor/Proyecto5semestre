package com.inmobiliaria.inmobiliaria_api.repository.crm;

import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Oportunidad;
import java.util.List;
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

/** Acceso a la colección {@code oportunidades} de MongoDB. */
@Repository
public class OportunidadRepository implements IndicesMongo {

    private static final List<String> DETALLE = List.of("historialEtapas", "actividades", "presentaciones");

    private final MongoTemplate mongoTemplate;

    public OportunidadRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<Oportunidad> findByIdAndEmpresaId(Long id, Long empresaId) {
        if (id == null || empresaId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                query(where("id").is(id).and("empresaId").is(empresaId)), Oportunidad.class));
    }

    public boolean existsByProspectoIdAndEstado(Long prospectoId, EstadoOportunidad estado) {
        return mongoTemplate.exists(query(where("prospectoId").is(prospectoId).and("estado").is(estado.name())),
                Oportunidad.class);
    }

    public Oportunidad insertar(Oportunidad oportunidad) {
        return mongoTemplate.insert(oportunidad);
    }

    /** Guarda los cambios; lanza {@code OptimisticLockingFailureException} si otro la guardó antes. */
    public Oportunidad guardar(Oportunidad oportunidad) {
        oportunidad.tocar();
        return mongoTemplate.save(oportunidad);
    }

    /** Borra una oportunidad recién creada cuya operación completa falló (compensación). */
    public void eliminar(Long id) {
        mongoTemplate.remove(query(where("id").is(id)), Oportunidad.class);
    }

    /** Página sin historial, actividades ni presentaciones; los objetos no se deben guardar. */
    public Page<Oportunidad> buscarResumen(Criteria filtro, Pageable pagina) {
        long total = mongoTemplate.count(new Query(filtro), Oportunidad.class);
        Query consulta = new Query(filtro).with(pagina);
        DETALLE.forEach(campo -> consulta.fields().exclude(campo));
        return new PageImpl<>(mongoTemplate.find(consulta, Oportunidad.class), pagina, total);
    }

    /** Crea los índices de la colección; es idempotente. */
    @Override
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(Oportunidad.class);
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("codigo", Sort.Direction.ASC)
                .unique().named("uk_oportunidades_empresa_codigo"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .on("creadoEn", Sort.Direction.DESC).named("idx_oportunidades_empresa_estado"));
        indices.ensureIndex(new Index().on("asesorId", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .named("idx_oportunidades_asesor_estado"));
        indices.ensureIndex(new Index().on("prospectoId", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .named("idx_oportunidades_prospecto_estado"));
    }
}
