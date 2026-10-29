package com.inmobiliaria.inmobiliaria_api.repository.soporte;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketSoporte;
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

/** Acceso a la colección {@code tickets_soporte} de MongoDB. */
@Repository
public class TicketSoporteRepository {

    private static final List<String> DETALLE = List.of("mensajes", "adjuntos", "historial");

    private final MongoTemplate mongoTemplate;

    public TicketSoporteRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<TicketSoporte> findByIdAndEmpresaId(Long id, Long empresaId) {
        if (id == null || empresaId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                query(where("id").is(id).and("empresaId").is(empresaId)), TicketSoporte.class));
    }

    /** Inserta un ticket nuevo; falla con {@code DuplicateKeyException} si el id o el código ya existen. */
    public TicketSoporte insertar(TicketSoporte ticket) {
        return mongoTemplate.insert(ticket);
    }

    /** Guarda los cambios; lanza {@code OptimisticLockingFailureException} si otro lo guardó antes. */
    public TicketSoporte guardar(TicketSoporte ticket) {
        ticket.tocar();
        return mongoTemplate.save(ticket);
    }

    /** Página de tickets sin conversación, adjuntos ni historial; los objetos no se deben guardar. */
    public Page<TicketSoporte> buscarResumen(Criteria filtro, Pageable pagina) {
        long total = mongoTemplate.count(new Query(filtro), TicketSoporte.class);
        Query consulta = new Query(filtro).with(pagina);
        DETALLE.forEach(campo -> consulta.fields().exclude(campo));
        return new PageImpl<>(mongoTemplate.find(consulta, TicketSoporte.class), pagina, total);
    }

    /** Crea los índices de la colección; es idempotente. */
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(TicketSoporte.class);
        indices.ensureIndex(new Index().on("codigo", Sort.Direction.ASC)
                .unique().named("uk_tickets_soporte_codigo"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .named("idx_tickets_soporte_empresa_estado"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("creadoEn", Sort.Direction.DESC)
                .named("idx_tickets_soporte_empresa_creado"));
        indices.ensureIndex(new Index().on("solicitanteId", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .named("idx_tickets_soporte_solicitante_estado"));
        indices.ensureIndex(new Index().on("asignadoA", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .named("idx_tickets_soporte_asignado_estado"));
    }
}
