package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Guarda en MongoDB (colección {@code sync_tokens}) la última posición procesada de cada
 * Change Stream: {@code { _id: "nexora-principal", token: "{...}", actualizadoEn: ... }}.
 */
@Service
public class AlmacenTokenMongo implements AlmacenTokenReanudacion {

    static final String COLECCION = "sync_tokens";

    private final MongoTemplate mongoTemplate;

    public AlmacenTokenMongo(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<String> leer(String flujo) {
        Document documento = coleccion().find(eq("_id", flujo)).first();
        return Optional.ofNullable(documento).map(d -> d.getString("token"));
    }

    @Override
    public void guardar(String flujo, String token) {
        coleccion().updateOne(
                eq("_id", flujo),
                Updates.combine(
                        Updates.set("token", token),
                        Updates.set("actualizadoEn", new Date())),
                new UpdateOptions().upsert(true));
    }

    private MongoCollection<Document> coleccion() {
        return mongoTemplate.getCollection(COLECCION);
    }
}