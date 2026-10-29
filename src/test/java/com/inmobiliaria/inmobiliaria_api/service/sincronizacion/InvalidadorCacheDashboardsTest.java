package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Invalidación de la caché de dashboards en Redis al proyectar cambios (rama 11). */
@Testcontainers(disabledWithoutDocker = true)
class InvalidadorCacheDashboardsTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.2.10-alpine3.22")).withExposedPorts(6379);

    private LettuceConnectionFactory conexiones;
    private StringRedisTemplate redis;
    private InvalidadorCacheDashboards invalidador;

    @BeforeEach
    void preparar() {
        conexiones = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        conexiones.afterPropertiesSet();
        conexiones.start();
        redis = new StringRedisTemplate(conexiones);
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> proveedor = mock(ObjectProvider.class);
        when(proveedor.getIfAvailable()).thenReturn(redis);
        invalidador = new InvalidadorCacheDashboards(proveedor);

        guardar(InvalidadorCacheDashboards.llave("gerencial", 8, 34) + ":null:2026-10-01:2026-10-29");
        guardar(InvalidadorCacheDashboards.llave("comercial", 8, 35));
        guardar(InvalidadorCacheDashboards.llave("servicio", 9, 34));
        guardar(InvalidadorCacheDashboards.llave("gerencial", 18, 8));
        guardar("nexora:token-version::34");
    }

    @AfterEach
    void cerrar() {
        conexiones.destroy();
    }

    @Test
    void borraSoloLosDashboardsDeLaEmpresaQueCambio() {
        invalidador.proyectado(cambio(Map.of("empresaId", 8L, "estado", "VENCIDA")));
        invalidador.proyectado(cambio(Map.of("empresaId", 8L, "estado", "COMPLETADA")));

        assertThat(invalidador.vaciar()).isEqualTo(2);

        assertThat(redis.keys("nexora:*")).containsExactlyInAnyOrder(
                InvalidadorCacheDashboards.llave("servicio", 9, 34),
                InvalidadorCacheDashboards.llave("gerencial", 18, 8),
                "nexora:token-version::34");
        assertThat(invalidador.vaciar()).as("ya no queda nada anotado").isZero();
    }

    @Test
    void unCambioSinEmpresaBorraLosDashboardsDeTodasPeroNoOtrasCaches() {
        invalidador.proyectado(cambio(Map.of()));

        assertThat(invalidador.vaciar()).isEqualTo(4);

        assertThat(redis.keys("nexora:*")).containsExactly("nexora:token-version::34");
    }

    private void guardar(String llave) {
        redis.opsForValue().set(llave, "{}");
    }

    private static CambioDocumento cambio(Map<String, Object> datos) {
        return new CambioDocumento("tareas", TipoCambio.ACTUALIZACION, 1L, datos, "token");
    }
}
