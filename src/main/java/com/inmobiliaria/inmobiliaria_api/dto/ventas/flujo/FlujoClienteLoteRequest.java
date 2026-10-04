package com.inmobiliaria.inmobiliaria_api.dto.ventas.flujo;

import com.inmobiliaria.inmobiliaria_api.dto.cliente.ClienteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FlujoClienteLoteRequest(
        @NotNull @Valid ClienteRequest cliente,
        @NotNull Long proyectoId,
        @NotNull Long loteId,
        @NotNull @Future LocalDateTime fechaVencimiento,
        @NotNull @DecimalMin("0.01") BigDecimal valorReserva,
        @NotBlank @Size(max = 40) String formaPago
) {
}
