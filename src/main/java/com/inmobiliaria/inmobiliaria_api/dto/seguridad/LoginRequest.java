package com.inmobiliaria.inmobiliaria_api.dto.seguridad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El identificador es obligatorio")
        @Size(max = 180, message = "El identificador no puede superar 180 caracteres")
        String identificador,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 200, message = "La contraseña no puede superar 200 caracteres")
        String password
) {
}
