package com.inmobiliaria.inmobiliaria_api.controller.seguridad;

import com.inmobiliaria.inmobiliaria_api.dto.seguridad.CambioContextoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.seguridad.CambioPasswordRequest;
import com.inmobiliaria.inmobiliaria_api.dto.seguridad.LoginRequest;
import com.inmobiliaria.inmobiliaria_api.dto.seguridad.RefreshRequest;
import com.inmobiliaria.inmobiliaria_api.dto.seguridad.TokenResponse;
import com.inmobiliaria.inmobiliaria_api.service.seguridad.AuthService;
import com.inmobiliaria.inmobiliaria_api.service.seguridad.CredencialesService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CredencialesService credencialesService;

    public AuthController(
            AuthService authService,
            CredencialesService credencialesService
    ) {
        this.authService = authService;
        this.credencialesService = credencialesService;
    }

    @PostMapping("/login")
    public TokenResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return authService.login(
                request,
                resolverIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(
            @Valid @RequestBody RefreshRequest request
    ) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @Valid @RequestBody RefreshRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        authService.logout(
                request.refreshToken(),
                Long.valueOf(jwt.getSubject())
        );
    }

    @PostMapping("/cambiar-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPassword(
            @Valid @RequestBody CambioPasswordRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        credencialesService.cambiarPassword(
                Long.valueOf(jwt.getSubject()),
                request
        );
    }

    @PostMapping("/contexto")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public TokenResponse cambiarContexto(
            @Valid @RequestBody CambioContextoRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return authService.cambiarContexto(
                request.refreshToken(),
                Long.valueOf(jwt.getSubject()),
                request.empresaId()
        );
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("usuarioId", Long.valueOf(jwt.getSubject()));
        response.put("empresaId", claimLong(jwt, "empresa_id"));
        response.put(
                "nombreUsuario",
                jwt.getClaimAsString("preferred_username")
        );
        response.put("correo", jwt.getClaimAsString("email"));
        response.put("authorities", claimLista(jwt, "authorities"));
        response.put(
                "requiereCambioPassword",
                Boolean.TRUE.equals(jwt.getClaim("requiere_cambio_password"))
        );
        response.put(
                "mfaHabilitado",
                Boolean.TRUE.equals(jwt.getClaim("mfa_habilitado"))
        );
        return response;
    }

    private String resolverIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Long claimLong(Jwt jwt, String name) {
        Object value = jwt.getClaim(name);
        return value instanceof Number number ? number.longValue() : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> claimLista(Jwt jwt, String name) {
        Object value = jwt.getClaim(name);
        return value instanceof List<?> list
                ? (List<String>) list
                : List.of();
    }
}
