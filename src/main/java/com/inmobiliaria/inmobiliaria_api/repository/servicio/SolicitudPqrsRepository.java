package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.EstadoSolicitud;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudPqrs;
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

/**
 * Acceso a la colección {@code solicitudes_pqrs} de MongoDB.
 *
 * <p>Se implementa sobre {@link MongoTemplate} y no como interfaz de Spring Data para que no
 * haya ambigüedad con los repositorios JPA del mismo proyecto.</p>
 */
@Repository
public class SolicitudPqrsRepository {

    private static final List<String> DETALLE = List.of("mensajes", "adjuntos", "historial");

    private final MongoTemplate mongoTemplate;

    public SolicitudPqrsRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<SolicitudPqrs> findByIdAndEmpresaId(Long id, Long empresaId) {
        if (id == null || empresaId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                query(where("id").is(id).and("empresaId").is(empresaId)), SolicitudPqrs.class));
    }

    /** Inserta una solicitud nueva; falla con {@code DuplicateKeyException} si el id o el código ya existen. */
    public SolicitudPqrs insertar(SolicitudPqrs solicitud) {
        return mongoTemplate.insert(solicitud);
    }

    /**
     * Guarda los cambios de una solicitud leída antes. Si otro proceso la guardó entretanto,
     * lanza {@code OptimisticLockingFailureException}.
     */
    public SolicitudPqrs guardar(SolicitudPqrs solicitud) {
        solicitud.tocar();
        return mongoTemplate.save(solicitud);
    }

    /**
     * Página de solicitudes sin la conversación, los adjuntos ni el historial (no se necesitan
     * para listar). Los objetos devueltos son de solo lectura: no se deben guardar.
     */
    public Page<SolicitudPqrs> buscarResumen(Criteria filtro, Pageable pagina) {
        long total = mongoTemplate.count(new Query(filtro), SolicitudPqrs.class);
        Query consulta = new Query(filtro).with(pagina);
        DETALLE.forEach(campo -> consulta.fields().exclude(campo));
        return new PageImpl<>(mongoTemplate.find(consulta, SolicitudPqrs.class), pagina, total);
    }

    /** Solicitudes del cliente que siguen en atención (ni cerradas ni canceladas). */
    public long contarAbiertasDeCliente(Long empresaId, Long clienteId) {
        return mongoTemplate.count(query(where("empresaId").is(empresaId)
                .and("clienteId").is(clienteId)
                .and("estado").nin(EstadoSolicitud.CERRADA.name(), EstadoSolicitud.CANCELADA.name())),
                SolicitudPqrs.class);
    }

    /** Crea los índices de la colección; es idempotente. */
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(SolicitudPqrs.class);
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("codigo", Sort.Direction.ASC)
                .unique().named("uk_solicitudes_pqrs_empresa_codigo"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("creadoEn", Sort.Direction.DESC)
                .named("idx_solicitudes_pqrs_empresa_creado"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("clienteId", Sort.Direction.ASC)
                .on("estado", Sort.Direction.ASC).named("idx_solicitudes_pqrs_empresa_cliente_estado"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("asignadoA", Sort.Direction.ASC)
                .on("estado", Sort.Direction.ASC).named("idx_solicitudes_pqrs_empresa_asignado_estado"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("proyectoId", Sort.Direction.ASC)
                .named("idx_solicitudes_pqrs_empresa_proyecto"));
    }
}
