package com.inmobiliaria.inmobiliaria_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/v1/auth/cambiar-password",
            "/api/v1/auth/logout",
            "/api/v1/auth/me"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt jwt
                && Boolean.TRUE.equals(jwt.getClaim("requiere_cambio_password"))
                && !ALLOWED_PATHS.contains(request.getRequestURI())) {
            throw new AccessDeniedException(
                    "Debe cambiar la contraseña temporal antes de continuar"
            );
        }
        filterChain.doFilter(request, response);
    }
}
