package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record LoteActualizarRequest(
        @NotBlank @Size(max = 50) String codigo,
        @NotBlank @Size(max = 30) String numero,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal areaM2,
        @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2)
        BigDecimal frenteM,
        @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2)
        BigDecimal fondoM,
        @Size(max = 300) String ubicacionReferencia,
        @Size(max = 10000) String descripcion
) {
}
