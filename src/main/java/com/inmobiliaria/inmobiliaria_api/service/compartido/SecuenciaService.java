package com.inmobiliaria.inmobiliaria_api.service.compartido;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Genera identificadores numéricos (Long) para documentos de MongoDB,
 * equivalentes al AUTO_INCREMENT de MySQL.
 *
 * <p>Cada secuencia es un documento de la colección {@code contadores}:
 * {@code { _id: "solicitudes", valor: 15 }}. El incremento es atómico,
 * por lo que dos peticiones simultáneas nunca reciben el mismo número.</p>
 *
 * <p>Los incrementos no participan en transacciones, igual que AUTO_INCREMENT:
 * si una transacción hace rollback, el número se pierde y queda un hueco.</p>
 */
@Service
public class SecuenciaService {

    static final String COLECCION = "contadores";

    private final MongoTemplate mongoTemplate;

    public SecuenciaService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Devuelve el siguiente número de la secuencia, empezando en 1. */
    public long siguiente(String secuencia) {
        Document contador = coleccion().findOneAndUpdate(
                eq("_id", secuencia),
                Updates.inc("valor", 1L),
                new FindOneAndUpdateOptions()
                        .upsert(true)
                        .returnDocument(ReturnDocument.AFTER)
        );
        return contador.get("valor", Number.class).longValue();
    }

    /**
     * Garantiza que la secuencia sea al menos {@code minimo}. Nunca la hace retroceder.
     * Se usa al migrar datos existentes: si la última PQRS de MySQL tiene id 15,
     * se llama con 15 y el siguiente documento nuevo recibirá 16.
     */
    public void asegurarMinimo(String secuencia, long minimo) {
        coleccion().updateOne(
                eq("_id", secuencia),
                Updates.max("valor", minimo),
                new UpdateOptions().upsert(true)
        );
    }

    private MongoCollection<Document> coleccion() {
        return mongoTemplate.getCollection(COLECCION);
    }
}