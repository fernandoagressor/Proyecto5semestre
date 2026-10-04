package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CambioPrecioLoteRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
        BigDecimal valor,
        @NotBlank @Size(max = 300) String motivo
) {
}
