package com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record ReservaVencimientoRequest(
        @NotNull @Future LocalDateTime fechaVencimiento
) {
}
