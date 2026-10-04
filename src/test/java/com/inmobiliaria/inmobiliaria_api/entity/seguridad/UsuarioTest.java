package com.inmobiliaria.inmobiliaria_api.entity.seguridad;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioTest {

    @Test
    void bloqueaTemporalmenteAlAlcanzarElMaximoDeIntentos() {
        Usuario usuario = usuario();
        LocalDateTime ahora = LocalDateTime.of(2026, 7, 31, 10, 0);
        LocalDateTime bloqueo = ahora.plusMinutes(15);

        for (int intento = 1; intento < 5; intento++) {
            assertThat(usuario.registrarAccesoFallido(5, ahora, bloqueo)).isFalse();
        }
        assertThat(usuario.registrarAccesoFallido(5, ahora, bloqueo)).isTrue();

        assertThat(usuario.getIntentosFallidos()).isEqualTo(5);
        assertThat(usuario.estaBloqueado(ahora)).isTrue();
        assertThat(usuario.estaBloqueado(bloqueo.plusSeconds(1))).isFalse();
    }

    @Test
    void unAccesoExitosoLimpiaIntentosYBloqueoTemporal() {
        Usuario usuario = usuario();
        LocalDateTime ahora = LocalDateTime.of(2026, 7, 31, 10, 0);
        usuario.registrarAccesoFallido(
                1,
                ahora,
                ahora.plusMinutes(15)
        );

        usuario.registrarAccesoExitoso(ahora.plusMinutes(1));

        assertThat(usuario.getIntentosFallidos()).isZero();
        assertThat(usuario.getBloqueadoHasta()).isNull();
        assertThat(usuario.getUltimoAccesoEn()).isEqualTo(ahora.plusMinutes(1));
    }

    @Test
    void reiniciaElContadorCuandoElBloqueoTemporalYaExpiro() {
        Usuario usuario = usuario();
        LocalDateTime ahora = LocalDateTime.of(2026, 7, 31, 10, 0);
        usuario.registrarAccesoFallido(1, ahora, ahora.plusMinutes(15));

        boolean bloqueado = usuario.registrarAccesoFallido(
                5,
                ahora.plusMinutes(16),
                ahora.plusMinutes(31)
        );

        assertThat(bloqueado).isFalse();
        assertThat(usuario.getIntentosFallidos()).isEqualTo(1);
        assertThat(usuario.getBloqueadoHasta()).isNull();
    }

    @Test
    void cambiarPasswordHabilitaLaClaveDefinitivaEInvalidaTokensPrevios() {
        Usuario usuario = usuario();
        usuario.registrarAccesoFallido(
                1,
                LocalDateTime.of(2026, 8, 10, 10, 0),
                LocalDateTime.of(2026, 8, 10, 10, 15)
        );

        usuario.cambiarPassword("nuevo-hash");

        assertThat(usuario.getPasswordHash()).isEqualTo("nuevo-hash");
        assertThat(usuario.isRequiereCambioPassword()).isFalse();
        assertThat(usuario.getIntentosFallidos()).isZero();
        assertThat(usuario.getBloqueadoHasta()).isNull();
        assertThat(usuario.getTokenVersion()).isEqualTo(1L);
    }

    private Usuario usuario() {
        return new Usuario(
                1L,
                1L,
                "admin",
                "admin@example.com",
                "hash"
        );
    }
}
