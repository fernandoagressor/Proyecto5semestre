package com.inmobiliaria.inmobiliaria_api.repository.crm;

import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.crm.CitaComercial;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoCitaComercial;
import java.time.LocalDateTime;
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

/** Acceso a la colección {@code citas_comerciales} de MongoDB. */
@Repository
public class CitaComercialRepository implements IndicesMongo {

    private static final List<String> FINALIZADAS = List.of(EstadoCitaComercial.CANCELADA.name(),
            EstadoCitaComercial.REALIZADA.name(), EstadoCitaComercial.NO_ASISTIO.name());

    private final MongoTemplate mongoTemplate;

    public CitaComercialRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<CitaComercial> findByOportunidadIdOrderByFechaInicioAsc(Long oportunidadId) {
        return mongoTemplate.find(query(where("oportunidadId").is(oportunidadId))
                .with(Sort.by(Sort.Direction.ASC, "fechaInicio").and(Sort.by("id"))), CitaComercial.class);
    }

    public Optional<CitaComercial> findByIdAndOportunidadId(Long id, Long oportunidadId) {
        return Optional.ofNullable(mongoTemplate.findOne(
                query(where("id").is(id).and("oportunidadId").is(oportunidadId)), CitaComercial.class));
    }

    public CitaComercial insertar(CitaComercial cita) {
        return mongoTemplate.insert(cita);
    }

    /** Guarda los cambios; lanza {@code OptimisticLockingFailureException} si otro la guardó antes. */
    public CitaComercial guardar(CitaComercial cita) {
        cita.tocar();
        return mongoTemplate.save(cita);
    }

    public Page<CitaComercial> buscar(Criteria filtro, Pageable pagina) {
        long total = mongoTemplate.count(new Query(filtro), CitaComercial.class);
        return new PageImpl<>(mongoTemplate.find(new Query(filtro).with(pagina), CitaComercial.class), pagina, total);
    }

    /** Citas vigentes del asesor que se cruzan con el horario indicado. */
    public long contarCrucesAgenda(Long asesorId, LocalDateTime inicio, LocalDateTime fin, Long excluirId) {
        Criteria filtro = where("asesorId").is(asesorId)
                .and("estado").nin(FINALIZADAS)
                .and("fechaInicio").lt(fin)
                .and("fechaFin").gt(inicio);
        if (excluirId != null) {
            filtro = filtro.and("id").ne(excluirId);
        }
        return mongoTemplate.count(new Query(filtro), CitaComercial.class);
    }

    /** Crea los índices de la colección; es idempotente. */
    @Override
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(CitaComercial.class);
        indices.ensureIndex(new Index().on("asesorId", Sort.Direction.ASC).on("fechaInicio", Sort.Direction.ASC)
                .named("idx_citas_comerciales_asesor_inicio"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("fechaInicio", Sort.Direction.ASC)
                .named("idx_citas_comerciales_empresa_inicio"));
        indices.ensureIndex(new Index().on("oportunidadId", Sort.Direction.ASC).on("fechaInicio", Sort.Direction.ASC)
                .named("idx_citas_comerciales_oportunidad"));
    }
}
