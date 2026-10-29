package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.CitaServicio;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.EstadoCitaServicio;
import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
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

/** Acceso a la colección {@code citas_servicio} de MongoDB. */
@Repository
public class CitaServicioRepository implements IndicesMongo {

    private static final List<String> TERMINADAS =
            List.of(EstadoCitaServicio.CANCELADA.name(), EstadoCitaServicio.REALIZADA.name());
    private static final List<String> PENDIENTES = List.of(EstadoCitaServicio.PROGRAMADA.name(),
            EstadoCitaServicio.CONFIRMADA.name(), EstadoCitaServicio.REPROGRAMADA.name());

    private final MongoTemplate mongoTemplate;

    public CitaServicioRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<CitaServicio> findByIdAndEmpresaId(Long id, Long empresaId) {
        if (id == null || empresaId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                query(where("id").is(id).and("empresaId").is(empresaId)), CitaServicio.class));
    }

    public CitaServicio insertar(CitaServicio cita) {
        return mongoTemplate.insert(cita);
    }

    /** Guarda los cambios; lanza {@code OptimisticLockingFailureException} si otro la guardó antes. */
    public CitaServicio guardar(CitaServicio cita) {
        cita.tocar();
        return mongoTemplate.save(cita);
    }

    public Page<CitaServicio> buscar(Criteria filtro, Pageable pagina) {
        long total = mongoTemplate.count(new Query(filtro), CitaServicio.class);
        return new PageImpl<>(mongoTemplate.find(new Query(filtro).with(pagina), CitaServicio.class), pagina, total);
    }

    /** Si el responsable ya tiene una cita activa que se cruza con el rango. */
    public boolean existeCruce(Long responsableId, LocalDateTime inicio, LocalDateTime fin, Long excluirId) {
        Criteria filtro = where("responsableId").is(responsableId).and("estado").nin(TERMINADAS)
                .and("fechaInicio").lt(fin).and("fechaFin").gt(inicio);
        if (excluirId != null) {
            filtro = filtro.and("id").ne(excluirId);
        }
        return mongoTemplate.exists(query(filtro), CitaServicio.class);
    }

    /** Fecha de la próxima cita pendiente del cliente, para el resumen del portal. */
    public Optional<LocalDateTime> proximaDeCliente(Long empresaId, Long clienteId, LocalDateTime ahora) {
        Query consulta = query(where("empresaId").is(empresaId).and("clienteId").is(clienteId)
                .and("estado").in(PENDIENTES).and("fechaInicio").gt(ahora))
                .with(Sort.by(Sort.Direction.ASC, "fechaInicio")).limit(1);
        consulta.fields().include("fechaInicio");
        return Optional.ofNullable(mongoTemplate.findOne(consulta, CitaServicio.class)).map(CitaServicio::getFechaInicio);
    }

    @Override
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(CitaServicio.class);
        indices.ensureIndex(new Index().on("responsableId", Sort.Direction.ASC).on("fechaInicio", Sort.Direction.ASC)
                .on("fechaFin", Sort.Direction.ASC).named("idx_citas_servicio_responsable_fechas"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("fechaInicio", Sort.Direction.ASC)
                .named("idx_citas_servicio_empresa_inicio"));
        indices.ensureIndex(new Index().on("clienteId", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .on("fechaInicio", Sort.Direction.ASC).named("idx_citas_servicio_cliente_estado_inicio"));
    }
}
