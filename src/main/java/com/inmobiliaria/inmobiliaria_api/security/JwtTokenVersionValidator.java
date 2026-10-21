package com.inmobiliaria.inmobiliaria_api.security;


import org.antlr.v4.runtime.Token;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenVersionValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error TOKEN_REVOCADO = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "El token fue revocado por un cambio de credenciales",
            null
    );

    private final TokenVersionCache tokenVersionCache;

    public JwtTokenVersionValidator(TokenVersionCache tokenVersionCache) {
        this.tokenVersionCache = tokenVersionCache;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long usuarioId;
        try {
            usuarioId = Long.valueOf(jwt.getSubject());
        } catch (RuntimeException exception) {
            return OAuth2TokenValidatorResult.failure(TOKEN_REVOCADO);
        }

        Object versionToken = jwt.getClaim("token_version");
        long versionEmitida = versionToken instanceof Number number
                ? number.longValue()
                : 0L;
        return tokenVersionCache.obtener(usuarioId)
                .filter(versionActual -> versionActual == versionEmitida)
                .map(version -> OAuth2TokenValidatorResult.success())
                .orElseGet(() -> OAuth2TokenValidatorResult.failure(TOKEN_REVOCADO));
    }
}
