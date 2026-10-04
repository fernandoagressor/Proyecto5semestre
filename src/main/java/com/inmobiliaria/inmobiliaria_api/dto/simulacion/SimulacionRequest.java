package com.inmobiliaria.inmobiliaria_api.dto.simulacion;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SimulacionRequest(
        @NotNull Long oportunidadId,
        @NotNull Long loteId,
        @NotNull @DecimalMin("0.00") BigDecimal descuento,
        @NotNull @DecimalMin("0.00") BigDecimal cuotaInicial,
        @Min(1) @Max(600) int numeroCuotas,
        @NotNull @DecimalMin("0.000000") @DecimalMax("9.999999")
        BigDecimal tasaEfectivaAnual,
        @NotNull Periodicidad periodicidad,
        @NotNull LocalDate fechaPrimeraCuota,
        @NotNull LocalDateTime vigenteHasta
) {
}
