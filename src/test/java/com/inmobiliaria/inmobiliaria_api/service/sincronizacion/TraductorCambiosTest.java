package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.mongodb.client.model.changestream.OperationType;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TraductorCambiosTest {

    private static final BsonDocument TOKEN = new BsonDocument("_data", new BsonString("8263A1B2"));

    private final TraductorCambios traductor = new TraductorCambios();

    // Ciclo 1
    @Test
    void unaInsercionSeTraduceConSuIdSusDatosYSuToken() {
        CambioDocumento cambio = traductor.traducir(
                "solicitudes", OperationType.INSERT, clave(15L),
                new Document("_id", 15L).append("estado", "ABIERTA"), TOKEN);

        assertThat(cambio.coleccion()).isEqualTo("solicitudes");
        assertThat(cambio.tipo()).isEqualTo(TipoCambio.INSERCION);
        assertThat(cambio.id()).isEqualTo(15L);
        assertThat(cambio.datos()).containsExactly(entry("estado", "ABIERTA"));
        assertThat(cambio.tokenReanudacion()).isEqualTo(TOKEN.toJson());
    }

    private static BsonDocument clave(long id) {
        return new BsonDocument("_id", new BsonInt64(id));
    }
    // Ciclo 2
    @ParameterizedTest
    @EnumSource(value = OperationType.class, names = {"UPDATE", "REPLACE"})
    void lasActualizacionesYLosReemplazosSonActualizaciones(OperationType operacion) {
        CambioDocumento cambio = traductor.traducir(
                "solicitudes", operacion, clave(15L),
                new Document("_id", 15L).append("estado", "CERRADA"), TOKEN);

        assertThat(cambio.tipo()).isEqualTo(TipoCambio.ACTUALIZACION);
    }
    // Ciclo 3
    @Test
    void unaEliminacionConservaElIdPeroNoTraeDatos() {
        CambioDocumento cambio = traductor.traducir(
                "solicitudes", OperationType.DELETE, clave(15L), null, TOKEN);

        assertThat(cambio.tipo()).isEqualTo(TipoCambio.ELIMINACION);
        assertThat(cambio.id()).isEqualTo(15L);
        assertThat(cambio.datos()).isEmpty();
    }
}