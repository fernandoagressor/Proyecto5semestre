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

/**
 * Reservar un lote de una vez. El cliente llega de una de dos formas: {@code clienteId} (uno que
 * ya existe) o {@code cliente} (datos de uno nuevo; si su documento ya es cliente de la empresa,
 * se usa ese).
 */
public record FlujoClienteLoteRequest(
        @Valid ClienteRequest cliente,
        @NotNull Long proyectoId,
        @NotNull Long loteId,
        @NotNull(message = "Indica hasta cuándo va la reserva")
        @Future(message = "El vencimiento debe estar en el futuro") LocalDateTime fechaVencimiento,
        @NotNull(message = "Indica el valor de la reserva")
        @DecimalMin(value = "0.01", message = "El valor de la reserva debe ser mayor que cero") BigDecimal valorReserva,
        @NotBlank @Size(max = 40) String formaPago,
        Long clienteId
) {
}
