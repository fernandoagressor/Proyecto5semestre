package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoLote;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambioEstadoLoteRequest(
        @NotNull EstadoLote estado,
        @NotBlank @Size(max = 300) String motivo
) {
}
