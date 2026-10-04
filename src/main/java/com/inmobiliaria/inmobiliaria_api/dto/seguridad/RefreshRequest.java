package com.inmobiliaria.inmobiliaria_api.dto.seguridad;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "El refresh token es obligatorio")
        String refreshToken
) {
}
