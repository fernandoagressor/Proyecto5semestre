package com.inmobiliaria.inmobiliaria_api.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenOpacoServiceTest {

    private final TokenOpacoService service = new TokenOpacoService();

    @Test
    void generaTokensNoPredeciblesYAlmacenaSoloHashSha256() {
        String primero = service.generar();
        String segundo = service.generar();

        assertThat(primero).isNotBlank().isNotEqualTo(segundo);
        assertThat(service.hash(primero))
                .hasSize(64)
                .doesNotContain(primero);
        assertThat(service.hash(primero)).isEqualTo(service.hash(primero));
    }
}
