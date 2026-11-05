package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.TipoContrato;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ContratoCondicionesRequest(
        @NotNull(message = "Elige el tipo de contrato") TipoContrato tipoContrato,
        @NotNull @DecimalMin(value = "0.00", message = "No puede ser negativo") BigDecimal otrosConceptos,
        @NotNull @DecimalMin(value = "0.00", message = "No puede ser negativo") BigDecimal descuento,
        @NotNull(message = "Indica la cuota inicial")
        @DecimalMin(value = "0.00", message = "No puede ser negativa") BigDecimal cuotaInicial,
        @Min(value = 1, message = "Debe haber al menos una cuota")
        @Max(value = 360, message = "Máximo 360 cuotas (30 años)") int numeroCuotas,
        @NotNull(message = "Indica la fecha de inicio") LocalDate fechaInicio,
        LocalDate fechaFinalizacion
) {
}
