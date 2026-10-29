package com.inmobiliaria.inmobiliaria_api.repository.compartido;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.Projections;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

/**
 * Escritura de documentos armados por los migradores MySQL → MongoDB.
 *
 * <p>Trabaja con documentos crudos de cualquier colección: el migrador decide la forma y aquí
 * solo se consulta qué ids ya existen y se insertan los que faltan.</p>
 */
@Repository
public class DocumentosMigradosRepository {

    private final MongoTemplate mongoTemplate;

    public DocumentosMigradosRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** De los ids recibidos, devuelve los que ya existen en la colección. */
    public Set<Long> idsExistentes(String coleccion, Collection<Long> ids) {
        Set<Long> existentes = new HashSet<>();
        if (ids.isEmpty()) {
            return existentes;
        }
        mongoTemplate.getCollection(coleccion)
                .find(Filters.in("_id", ids))
                .projection(Projections.include("_id"))
                .forEach(documento -> existentes.add(documento.get("_id", Number.class).longValue()));
        return existentes;
    }

    /**
     * Inserta los documentos. Los que ya existan (otra instancia los migró al mismo tiempo)
     * se ignoran.
     *
     * @return cuántos se insertaron
     */
    public int insertar(String coleccion, List<Document> documentos) {
        if (documentos.isEmpty()) {
            return 0;
        }
        try {
            mongoTemplate.getCollection(coleccion).insertMany(documentos, new InsertManyOptions().ordered(false));
            return documentos.size();
        } catch (MongoBulkWriteException e) {
            boolean soloDuplicados = e.getWriteErrors().stream()
                    .allMatch(error -> ErrorCategory.fromErrorCode(error.getCode()) == ErrorCategory.DUPLICATE_KEY);
            if (!soloDuplicados || e.getWriteConcernError() != null) {
                throw e;
            }
            return documentos.size() - e.getWriteErrors().size();
        }
    }

    /** Convierte un valor Java al tipo que guarda Spring Data (por ejemplo LocalDateTime → Date). */
    public Object valorMongo(Object valor) {
        return valor == null ? null : mongoTemplate.getConverter().convertToMongoType(valor);
    }
}
