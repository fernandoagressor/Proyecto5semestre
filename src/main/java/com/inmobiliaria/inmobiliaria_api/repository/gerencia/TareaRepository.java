package com.inmobiliaria.inmobiliaria_api.repository.gerencia;

import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoTarea;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.Tarea;
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

/** Acceso a la colección {@code tareas} de MongoDB. */
@Repository
public class TareaRepository implements IndicesMongo {

    private final MongoTemplate mongoTemplate;

    public TareaRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<Tarea> findByIdAndEmpresaId(Long id, Long empresaId) {
        if (id == null || empresaId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                query(where("id").is(id).and("empresaId").is(empresaId)), Tarea.class));
    }

    public Tarea insertar(Tarea tarea) {
        return mongoTemplate.insert(tarea);
    }

    /** Guarda los cambios; lanza {@code OptimisticLockingFailureException} si otro la guardó antes. */
    public Tarea guardar(Tarea tarea) {
        tarea.tocar();
        return mongoTemplate.save(tarea);
    }

    /** Página de tareas sin historial; los objetos no se deben guardar. */
    public Page<Tarea> buscarResumen(Criteria filtro, Pageable pagina) {
        long total = mongoTemplate.count(new Query(filtro), Tarea.class);
        Query consulta = new Query(filtro).with(pagina);
        consulta.fields().exclude("historial");
        return new PageImpl<>(mongoTemplate.find(consulta, Tarea.class), pagina, total);
    }

    /** Tareas pendientes o en proceso cuyo vencimiento ya pasó (de todas las empresas). */
    public List<Tarea> buscarPorVencer(LocalDateTime ahora) {
        return mongoTemplate.find(query(where("estado").in(EstadoTarea.PENDIENTE.name(), EstadoTarea.EN_PROCESO.name())
                .and("fechaVencimiento").lt(ahora)), Tarea.class);
    }

    /** Crea los índices de la colección; es idempotente. */
    @Override
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(Tarea.class);
        indices.ensureIndex(new Index().on("asignadoA", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .on("fechaVencimiento", Sort.Direction.ASC).named("idx_tareas_asignado_estado_vencimiento"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .named("idx_tareas_empresa_estado"));
        indices.ensureIndex(new Index().on("estado", Sort.Direction.ASC).on("fechaVencimiento", Sort.Direction.ASC)
                .named("idx_tareas_estado_vencimiento"));
        indices.ensureIndex(new Index().on("entidadTipo", Sort.Direction.ASC).on("entidadId", Sort.Direction.ASC)
                .named("idx_tareas_entidad"));
    }
}
