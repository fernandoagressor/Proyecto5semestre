package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import com.mongodb.client.model.changestream.OperationType;
import org.bson.BsonDocument;
import org.bson.Document;

import java.util.LinkedHashMap;
import java.util.Map;

public class TraductorCambios {

    public CambioDocumento traducir(String coleccion, OperationType operacion,
                                    BsonDocument clave, Document documento, BsonDocument token) {
        Map<String, Object> datos = documento == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(documento);
        datos.remove("_id");
        return new CambioDocumento(coleccion,
                operacion == OperationType.INSERT ? TipoCambio.INSERCION
                        : operacion == OperationType.DELETE ? TipoCambio.ELIMINACION
                        : TipoCambio.ACTUALIZACION,
                clave.get("_id").asNumber().longValue(), datos, token.toJson());
    }

}