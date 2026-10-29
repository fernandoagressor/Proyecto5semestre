package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.GestionCobro;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

/** Acceso a la colección {@code gestiones_cobro} de MongoDB. Solo inserta y consulta. */
@Repository
public class GestionCobroRepository implements IndicesMongo {

    private final MongoTemplate mongoTemplate;

    public GestionCobroRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public GestionCobro insertar(GestionCobro gestion) {
        return mongoTemplate.insert(gestion);
    }

    /** Gestiones de un contrato, de la más reciente a la más antigua. */
    public List<GestionCobro> listarPorContrato(Long empresaId, Long contratoId) {
        return mongoTemplate.find(query(where("empresaId").is(empresaId).and("contratoId").is(contratoId))
                .with(Sort.by(Sort.Direction.DESC, "fechaGestion").and(Sort.by(Sort.Direction.DESC, "id"))),
                GestionCobro.class);
    }

    /** Promesas de pago con fecha desde el corte; {@code proyectoId} nulo cuenta toda la empresa. */
    public long contarPromesasDesde(Long empresaId, LocalDate desde, Long proyectoId) {
        Criteria filtro = where("empresaId").is(empresaId).and("promesaPagoFecha").gte(desde);
        if (proyectoId != null) {
            filtro = filtro.and("proyectoId").is(proyectoId);
        }
        return mongoTemplate.count(query(filtro), GestionCobro.class);
    }

    /** Crea los índices de la colección; es idempotente. */
    @Override
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(GestionCobro.class);
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("contratoId", Sort.Direction.ASC)
                .on("fechaGestion", Sort.Direction.DESC).named("idx_gestiones_cobro_contrato"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("promesaPagoFecha", Sort.Direction.ASC)
                .named("idx_gestiones_cobro_promesas"));
        indices.ensureIndex(new Index().on("gestorId", Sort.Direction.ASC).on("fechaGestion", Sort.Direction.DESC)
                .named("idx_gestiones_cobro_gestor"));
    }
}
