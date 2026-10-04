package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimientoCrearRequest(
        @NotNull LocalDate fechaMovimiento,
        @NotBlank @Size(max = 180) String referencia,
        @Size(max = 500) String descripcion,
        @NotNull @DecimalMin("0.01") BigDecimal valor
) {
}
