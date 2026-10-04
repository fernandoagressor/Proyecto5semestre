package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoProyecto;
import jakarta.validation.constraints.NotNull;

public record CambioEstadoProyectoRequest(
        @NotNull EstadoProyecto estado
) {
}
