package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RefinanciacionCrearRequest(
        @NotNull Long contratoId,
        @Min(1) @Max(600) int numeroCuotas,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal tasaEfectivaAnual,
        @NotNull @FutureOrPresent LocalDate fechaPrimeraCuota,
        @NotBlank @Size(max = 2000) String motivo
) {
}
