package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Cuántas manzanas (máximo 100) o lotes (máximo 500) generar de una vez. */
public record GenerarRequest(
        @NotNull(message = "Indica cuántos quieres crear")
        @Min(value = 1, message = "Debe ser al menos 1")
        @Max(value = 500, message = "Máximo 500 en una sola vez")
        Integer cantidad
) {
}
