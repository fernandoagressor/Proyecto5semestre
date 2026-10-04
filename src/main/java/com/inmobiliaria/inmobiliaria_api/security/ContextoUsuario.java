package com.inmobiliaria.inmobiliaria_api.security;

import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.NoAutorizadoException;
import org.springframework.security.oauth2.jwt.Jwt;

public record ContextoUsuario(Long usuarioId, Long empresaId) {

    public static ContextoUsuario empresa(Jwt jwt) {
        Long usuarioId = numero(jwt.getSubject());
        Long empresaId = numero(jwt.getClaim("empresa_id"));
        if (usuarioId == null || empresaId == null) {
            throw new NoAutorizadoException(
                    "La sesión no tiene un usuario y una empresa válidos"
            );
        }
        return new ContextoUsuario(usuarioId, empresaId);
    }

    private static Long numero(Object valor) {
        if (valor instanceof Number number) {
            return number.longValue();
        }
        if (valor instanceof String texto) {
            try {
                return Long.valueOf(texto);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
