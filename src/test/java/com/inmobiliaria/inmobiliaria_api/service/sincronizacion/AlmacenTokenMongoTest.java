package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@DataMongoTest
@Import(AlmacenTokenMongo.class)
@Testcontainers(disabledWithoutDocker = true)
class AlmacenTokenMongoTest {

    // MongoDB 8.0 no arranca en kernels Linux 6.19+ (SERVER-121912), como los de GitHub Actions.
    @Container
    static final MongoDBContainer MONGODB =
            new MongoDBContainer(DockerImageName.parse("mongo:8.0.32"))
                    .withReplicaSet()
                    .withEnv("GLIBC_TUNABLES", "glibc.pthread.rseq=1");

    @DynamicPropertySource
    static void conectarMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
    }

    @Autowired
    private AlmacenTokenMongo almacen;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void empezarConLaColeccionVacia() {
        mongoTemplate.dropCollection(AlmacenTokenMongo.COLECCION);
    }

    @Test
    void unFlujoSinTokenGuardadoDevuelveVacio() {
        assertThat(almacen.leer("flujo-nuevo")).isEmpty();
    }

    @Test
    void devuelveElTokenGuardado() {
        almacen.guardar("nexora-principal", "{\"_data\":\"8263A1B2\"}");

        assertThat(almacen.leer("nexora-principal")).contains("{\"_data\":\"8263A1B2\"}");
    }

    @Test
    void guardarOtraVezReemplazaElTokenSinDuplicarDocumentos() {
        almacen.guardar("nexora-principal", "token-1");
        almacen.guardar("nexora-principal", "token-2");

        assertThat(almacen.leer("nexora-principal")).contains("token-2");
        assertThat(mongoTemplate.getCollection(AlmacenTokenMongo.COLECCION).countDocuments())
                .isEqualTo(1);
    }

    @Test
    void cadaFlujoConservaSuPropioToken() {
        almacen.guardar("flujo-a", "token-a");
        almacen.guardar("flujo-b", "token-b");

        assertThat(almacen.leer("flujo-a")).contains("token-a");
        assertThat(almacen.leer("flujo-b")).contains("token-b");
    }
}