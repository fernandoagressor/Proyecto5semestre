package com.inmobiliaria.inmobiliaria_api.dto.crm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CitaComercialRequest(
        @NotBlank @Size(max = 30) String tipo,
        @NotNull LocalDateTime fechaInicio,
        @NotNull LocalDateTime fechaFin,
        @Size(max = 300) String lugar
) {
}
