package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ConfiguracionCarteraRequest(
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal tasaEfectivaAnual,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal tasaMoraEfectivaAnual,
        @NotNull Periodicidad periodicidad,
        @Min(0) @Max(365) int diasPrimeraCuota,
        boolean autoGenerarPlan
) {
}
