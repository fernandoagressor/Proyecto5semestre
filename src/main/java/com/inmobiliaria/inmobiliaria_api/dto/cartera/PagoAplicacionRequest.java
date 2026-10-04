package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConceptoPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PagoAplicacionRequest(
        Long cuotaId,
        Long facturaId,
        @NotNull ConceptoPago concepto,
        @NotNull @DecimalMin("0.01") BigDecimal valor
) {
}
