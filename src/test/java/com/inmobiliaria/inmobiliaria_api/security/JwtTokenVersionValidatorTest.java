package com.inmobiliaria.inmobiliaria_api.security;

import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenVersionValidatorTest {

    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final JwtTokenVersionValidator validator = new JwtTokenVersionValidator(repository);

    @Test
    void aceptaUnTokenConLaVersionVigente() {
        when(repository.findTokenVersionById(7L)).thenReturn(Optional.of(2L));

        assertThat(validator.validate(jwt(2L)).hasErrors()).isFalse();
    }

    @Test
    void rechazaUnTokenEmitidoAntesDelCambioDePassword() {
        when(repository.findTokenVersionById(7L)).thenReturn(Optional.of(3L));

        assertThat(validator.validate(jwt(2L)).hasErrors()).isTrue();
    }

    @Test
    void conservaTokensDelCorteAnteriorMientrasNoCambienLasCredenciales() {
        when(repository.findTokenVersionById(7L)).thenReturn(Optional.of(0L));

        assertThat(validator.validate(jwt(null)).hasErrors()).isFalse();
    }

    private Jwt jwt(Long tokenVersion) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("7")
                .issuedAt(Instant.parse("2026-08-10T15:00:00Z"))
                .expiresAt(Instant.parse("2026-08-10T15:15:00Z"));
        if (tokenVersion != null) {
            builder.claim("token_version", tokenVersion);
        }
        return builder.build();
    }
}
