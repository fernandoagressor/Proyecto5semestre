package com.inmobiliaria.inmobiliaria_api.repository.crm;

import com.inmobiliaria.inmobiliaria_api.repository.compartido.IndicesMongo;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Prospecto;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
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

/** Acceso a la colección {@code prospectos} de MongoDB. */
@Repository
public class ProspectoRepository implements IndicesMongo {

    private final MongoTemplate mongoTemplate;

    public ProspectoRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<Prospecto> findByIdAndEmpresaId(Long id, Long empresaId) {
        if (id == null || empresaId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                query(where("id").is(id).and("empresaId").is(empresaId)), Prospecto.class));
    }

    public Prospecto insertar(Prospecto prospecto) {
        return mongoTemplate.insert(prospecto);
    }

    /** Guarda los cambios; lanza {@code OptimisticLockingFailureException} si otro lo guardó antes. */
    public Prospecto guardar(Prospecto prospecto) {
        prospecto.tocar();
        return mongoTemplate.save(prospecto);
    }

    /** Borra un prospecto recién creado cuya operación completa falló (compensación). */
    public void eliminar(Long id) {
        mongoTemplate.remove(query(where("id").is(id)), Prospecto.class);
    }

    public Page<Prospecto> buscar(Criteria filtro, Pageable pagina) {
        long total = mongoTemplate.count(new Query(filtro), Prospecto.class);
        return new PageImpl<>(mongoTemplate.find(new Query(filtro).with(pagina), Prospecto.class), pagina, total);
    }

    /**
     * Prospectos activos (ni descartados ni convertidos) de la empresa con el mismo correo (sin
     * distinguir mayúsculas) o el mismo teléfono.
     */
    public long contarContactosActivos(Long empresaId, String correo, String telefono, Long excluirId) {
        List<Criteria> contacto = new ArrayList<>();
        if (correo != null) contacto.add(where("correo").regex("^" + Pattern.quote(correo) + "$", "i"));
        if (telefono != null) contacto.add(where("telefono").is(telefono));
        if (contacto.isEmpty()) {
            return 0;
        }
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(where("empresaId").is(empresaId));
        condiciones.add(where("estado").nin(EstadoProspecto.DESCARTADO.name(), EstadoProspecto.CONVERTIDO.name()));
        if (excluirId != null) condiciones.add(where("id").ne(excluirId));
        condiciones.add(new Criteria().orOperator(contacto));
        return mongoTemplate.count(new Query(new Criteria().andOperator(condiciones)), Prospecto.class);
    }

    /** Crea los índices de la colección; es idempotente. */
    @Override
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(Prospecto.class);
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("creadoEn", Sort.Direction.DESC)
                .named("idx_prospectos_empresa_creado"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("telefono", Sort.Direction.ASC)
                .named("idx_prospectos_empresa_telefono"));
        indices.ensureIndex(new Index().on("empresaId", Sort.Direction.ASC).on("correo", Sort.Direction.ASC)
                .named("idx_prospectos_empresa_correo"));
        indices.ensureIndex(new Index().on("asesorId", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .named("idx_prospectos_asesor_estado"));
    }
}
