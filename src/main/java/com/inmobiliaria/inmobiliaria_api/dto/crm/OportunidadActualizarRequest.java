package com.inmobiliaria.inmobiliaria_api.dto.crm;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OportunidadActualizarRequest(
        @DecimalMin("0.00") BigDecimal valorEstimado,
        LocalDateTime fechaProximaAccion
) {
}
