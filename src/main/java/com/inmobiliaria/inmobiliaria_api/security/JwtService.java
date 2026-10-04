package com.inmobiliaria.inmobiliaria_api.security;

import com.inmobiliaria.inmobiliaria_api.config.SecurityProperties;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public JwtService(JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public AccessToken crearAccessToken(UsuarioPrincipal principal) {
        Instant emitidoEn = Instant.now();
        Instant expiraEn = emitidoEn.plus(properties.accessTokenTtl());
        List<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Map<String, Object> claimsAdicionales = new LinkedHashMap<>();
        claimsAdicionales.put("usuario_id", principal.usuarioId());
        if (principal.empresaId() != null) {
            claimsAdicionales.put("empresa_id", principal.empresaId());
        }
        claimsAdicionales.put("preferred_username", principal.nombreUsuario());
        claimsAdicionales.put("email", principal.correo());
        claimsAdicionales.put("authorities", authorities);
        claimsAdicionales.put(
                "requiere_cambio_password",
                principal.requiereCambioPassword()
        );
        claimsAdicionales.put("mfa_habilitado", principal.mfaHabilitado());
        claimsAdicionales.put("token_version", principal.tokenVersion());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(emitidoEn)
                .expiresAt(expiraEn)
                .subject(principal.usuarioId().toString())
                .claims(map -> map.putAll(claimsAdicionales))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        return new AccessToken(
                token,
                properties.accessTokenTtl().toSeconds()
        );
    }

    public record AccessToken(String valor, long expiraEnSegundos) {
    }
}
