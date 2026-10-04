package com.inmobiliaria.inmobiliaria_api.dto.crm;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OportunidadRequest(
        @NotNull Long proyectoId,
        @NotNull Long prospectoId,
        Long asesorId,
        @DecimalMin("0.00") BigDecimal valorEstimado,
        LocalDateTime fechaProximaAccion
) {
}
