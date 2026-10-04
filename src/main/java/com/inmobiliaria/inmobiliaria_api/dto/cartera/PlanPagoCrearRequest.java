package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PlanPagoCrearRequest(
        @NotNull Long contratoId,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal tasaEfectivaAnual,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal tasaMoraEfectivaAnual,
        @Min(1) @Max(600) int numeroCuotas,
        @NotNull Periodicidad periodicidad,
        @NotNull @FutureOrPresent LocalDate fechaPrimeraCuota
) {
}
