package com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReservaMotivoRequest(
        @NotBlank @Size(max = 400) String motivo
) {
}
