package com.inmobiliaria.inmobiliaria_api.dto.crm;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OportunidadLoteRequest(
        @NotNull Long loteId,
        @Min(1) Integer prioridad
) {
}
