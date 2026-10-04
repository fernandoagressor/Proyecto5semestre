package com.inmobiliaria.inmobiliaria_api.dto.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClienteEstadoRequest(
        @NotBlank @Pattern(regexp = "ACTIVO|INACTIVO|PENDIENTE") String estado
) {
}
