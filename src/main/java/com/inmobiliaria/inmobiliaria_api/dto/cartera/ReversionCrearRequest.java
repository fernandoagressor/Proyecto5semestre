package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReversionCrearRequest(
        @NotBlank @Size(max = 500) String motivo,
        Long evidenciaId
) {
}
