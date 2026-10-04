package com.inmobiliaria.inmobiliaria_api.dto.cliente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteRequest(
        @NotBlank @Pattern(regexp = "NATURAL|JURIDICA") String tipoPersona,
        @NotBlank @Size(max = 20) String tipoDocumento,
        @NotBlank @Size(max = 40) String numeroDocumento,
        @NotBlank @Size(max = 120) String nombres,
        @Size(max = 120) String apellidos,
        @Size(max = 40) String telefono,
        @Email @Size(max = 180) String correo,
        @Size(max = 240) String direccion,
        @Size(max = 80) String origen,
        @Size(max = 2000) String observaciones
) {
}
