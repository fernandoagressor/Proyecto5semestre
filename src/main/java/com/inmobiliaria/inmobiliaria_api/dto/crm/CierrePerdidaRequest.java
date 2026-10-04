package com.inmobiliaria.inmobiliaria_api.dto.crm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CierrePerdidaRequest(
        @NotBlank @Size(max = 300) String motivo
) {
}
