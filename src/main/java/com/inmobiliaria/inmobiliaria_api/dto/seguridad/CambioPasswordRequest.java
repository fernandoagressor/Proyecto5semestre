package com.inmobiliaria.inmobiliaria_api.dto.seguridad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CambioPasswordRequest(
        @NotBlank(message = "La contraseña actual es obligatoria")
        @Size(max = 72, message = "La contraseña actual no puede superar 72 caracteres")
        String passwordActual,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 12, max = 72, message = "La nueva contraseña debe tener entre 12 y 72 caracteres")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S+$",
                message = "La nueva contraseña debe incluir mayúscula, minúscula, número y símbolo, sin espacios"
        )
        String passwordNueva,

        @NotBlank(message = "La confirmación de contraseña es obligatoria")
        @Size(max = 72, message = "La confirmación no puede superar 72 caracteres")
        String confirmacionPassword
) {
}
