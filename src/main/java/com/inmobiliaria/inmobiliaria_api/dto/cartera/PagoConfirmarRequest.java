package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PagoConfirmarRequest(
        @NotEmpty @Valid List<PagoAplicacionRequest> aplicaciones,
        @Size(max = 500) String motivo
) {
}
