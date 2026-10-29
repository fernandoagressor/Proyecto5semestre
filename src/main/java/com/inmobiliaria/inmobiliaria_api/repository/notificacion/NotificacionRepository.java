package com.inmobiliaria.inmobiliaria_api.repository.notificacion;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.notificacion.Notificacion;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** Acceso a la colección {@code notificaciones} de MongoDB. */
@Repository
public class NotificacionRepository {

    private final MongoTemplate mongoTemplate;

    public NotificacionRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Notificacion insertar(Notificacion notificacion) {
        return mongoTemplate.insert(notificacion);
    }

    public Optional<Notificacion> findByIdAndUsuarioId(Long id, Long usuarioId) {
        if (id == null || usuarioId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongoTemplate.findOne(
                query(where("id").is(id).and("usuarioId").is(usuarioId)), Notificacion.class));
    }

    /**
     * Notificaciones vigentes del usuario (sin vencimiento o con vencimiento futuro), opcionalmente
     * filtradas por leídas / no leídas, de la más reciente a la más antigua.
     */
    public Page<Notificacion> buscarVigentes(Long usuarioId, Boolean leida, LocalDateTime ahora, Pageable pagina) {
        Criteria filtro = vigentes(usuarioId, leida, ahora);
        long total = mongoTemplate.count(new Query(filtro), Notificacion.class);
        Query consulta = new Query(filtro).with(pagina);
        consulta.fields().exclude("envios");
        return new PageImpl<>(mongoTemplate.find(consulta, Notificacion.class), pagina, total);
    }

    public long contarVigentesNoLeidas(Long usuarioId, LocalDateTime ahora) {
        return mongoTemplate.count(new Query(vigentes(usuarioId, false, ahora)), Notificacion.class);
    }

    /**
     * Marca la notificación como leída con una actualización atómica. Si ya estaba leída no la
     * toca, así dos clics seguidos no producen conflicto.
     */
    public Optional<Notificacion> marcarLeida(Long id, Long usuarioId, LocalDateTime fecha) {
        mongoTemplate.updateFirst(
                query(where("id").is(id).and("usuarioId").is(usuarioId).and("leidaEn").is(null)),
                new Update().set("leidaEn", fecha).set("actualizadoEn", fecha),
                Notificacion.class);
        return findByIdAndUsuarioId(id, usuarioId);
    }

    /**
     * Crea los índices; es idempotente. El de {@code expiraEn} es TTL: MongoDB borra la
     * notificación cuando vence (las que no tienen vencimiento no se borran).
     */
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(Notificacion.class);
        indices.ensureIndex(new Index().on("usuarioId", Sort.Direction.ASC).on("leidaEn", Sort.Direction.ASC)
                .on("creadoEn", Sort.Direction.DESC).named("idx_notificaciones_usuario_leida_creado"));
        indices.ensureIndex(new Index().on("expiraEn", Sort.Direction.ASC)
                .expire(Duration.ZERO).named("ttl_notificaciones_expira"));
    }

    private static Criteria vigentes(Long usuarioId, Boolean leida, LocalDateTime ahora) {
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(where("usuarioId").is(usuarioId));
        condiciones.add(new Criteria().orOperator(where("expiraEn").is(null), where("expiraEn").gt(ahora)));
        if (leida != null) {
            condiciones.add(leida ? where("leidaEn").ne(null) : where("leidaEn").is(null));
        }
        return new Criteria().andOperator(condiciones);
    }
}
