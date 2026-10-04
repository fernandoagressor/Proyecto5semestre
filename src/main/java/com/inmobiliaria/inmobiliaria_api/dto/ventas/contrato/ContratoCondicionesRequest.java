package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.TipoContrato;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ContratoCondicionesRequest(
        @NotNull TipoContrato tipoContrato,
        @NotNull @DecimalMin("0.00") BigDecimal otrosConceptos,
        @NotNull @DecimalMin("0.00") BigDecimal descuento,
        @NotNull @DecimalMin("0.00") BigDecimal cuotaInicial,
        @Min(1) int numeroCuotas,
        @NotNull LocalDate fechaInicio,
        LocalDate fechaFinalizacion
) {
}
