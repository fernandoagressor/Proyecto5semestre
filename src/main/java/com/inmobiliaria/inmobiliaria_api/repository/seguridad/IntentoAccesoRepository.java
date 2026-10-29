package com.inmobiliaria.inmobiliaria_api.repository.seguridad;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.inmobiliaria.inmobiliaria_api.entity.seguridad.IntentoAcceso;
import java.time.Duration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Repository;

/**
 * Historial de intentos de acceso en la colección {@code intentos_acceso}; solo se inserta.
 * MongoDB borra cada intento {@link #RETENCION} después de ocurrido (índice TTL).
 */
@Repository
public class IntentoAccesoRepository {

    /** Tiempo que se conserva cada intento. */
    public static final Duration RETENCION = Duration.ofDays(90);

    private final MongoTemplate mongoTemplate;

    public IntentoAccesoRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public IntentoAcceso insertar(IntentoAcceso intento) {
        return mongoTemplate.insert(intento);
    }

    /** Cuántos inicios de sesión exitosos tiene el usuario en el historial, sin contar {@code excluirId}. */
    public long contarExitosos(Long usuarioId, Long excluirId) {
        return mongoTemplate.count(query(where("usuarioId").is(usuarioId).and("resultado").is("EXITOSO")
                .and("id").ne(excluirId)), IntentoAcceso.class);
    }

    /** Si el usuario ya había iniciado sesión con éxito desde esa IP, sin contar {@code excluirId}. */
    public boolean existeExitosoDesdeIp(Long usuarioId, String ip, Long excluirId) {
        return mongoTemplate.exists(query(where("usuarioId").is(usuarioId).and("resultado").is("EXITOSO")
                .and("ipOrigen").is(ip).and("id").ne(excluirId)), IntentoAcceso.class);
    }

    /** Crea los índices de la colección (incluido el TTL de {@link #RETENCION}); es idempotente. */
    public void asegurarIndices() {
        IndexOperations indices = mongoTemplate.indexOps(IntentoAcceso.class);
        indices.ensureIndex(new Index().on("identificador", Sort.Direction.ASC)
                .on("fechaIntento", Sort.Direction.DESC).named("idx_intentos_acceso_identificador_fecha"));
        indices.ensureIndex(new Index().on("ipOrigen", Sort.Direction.ASC)
                .on("fechaIntento", Sort.Direction.DESC).named("idx_intentos_acceso_ip_fecha"));
        indices.ensureIndex(new Index().on("usuarioId", Sort.Direction.ASC)
                .on("fechaIntento", Sort.Direction.DESC).named("idx_intentos_acceso_usuario_fecha"));
        indices.ensureIndex(new Index().on("fechaIntento", Sort.Direction.ASC)
                .expire(RETENCION).named("ttl_intentos_acceso_90_dias"));
    }
}
