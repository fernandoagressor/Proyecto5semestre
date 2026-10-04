package com.inmobiliaria.inmobiliaria_api.dto.seguridad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CambioContextoRequest(
        @NotNull Long empresaId,
        @NotBlank String refreshToken
) {
}
