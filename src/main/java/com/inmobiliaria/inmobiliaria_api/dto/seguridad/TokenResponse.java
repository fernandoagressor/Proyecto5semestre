package com.inmobiliaria.inmobiliaria_api.dto.seguridad;

import java.util.List;

public record TokenResponse(
        String tipo,
        String accessToken,
        String refreshToken,
        long expiraEnSegundos,
        UsuarioAutenticadoResponse usuario
) {
    public record UsuarioAutenticadoResponse(
            Long id,
            Long empresaId,
            String nombreUsuario,
            String correo,
            List<String> roles,
            List<String> permisos,
            boolean requiereCambioPassword,
            boolean mfaHabilitado
    ) {
    }
}
