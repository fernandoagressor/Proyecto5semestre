package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.CanalCobro;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GestionCobroRequest(
        @NotNull Long contratoId,
        Long cuotaId,
        @NotNull CanalCobro canal,
        @NotBlank @Size(max = 120) String resultado,
        @Size(max = 2000) String detalle,
        LocalDate promesaPagoFecha,
        @DecimalMin("0.01") BigDecimal promesaPagoValor
) {
}
