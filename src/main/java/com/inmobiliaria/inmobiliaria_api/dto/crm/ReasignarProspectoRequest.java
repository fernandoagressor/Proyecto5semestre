package com.inmobiliaria.inmobiliaria_api.dto.crm;

import jakarta.validation.constraints.NotNull;

public record ReasignarProspectoRequest(
        @NotNull Long asesorId
) {
}
