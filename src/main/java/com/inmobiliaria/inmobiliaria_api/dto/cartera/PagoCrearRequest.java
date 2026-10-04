package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.OrigenPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoCrearRequest(
        Long contratoId,
        Long reservaId,
        @NotNull Long clienteId,
        @NotNull Long metodoPagoId,
        @NotNull @PastOrPresent LocalDateTime fechaPago,
        @NotNull @DecimalMin(value = "0.01") BigDecimal valor,
        @Size(max = 180) String referencia,
        Long comprobanteId,
        @NotNull OrigenPago origen,
        @Size(max = 2000) String observaciones
) {
}
