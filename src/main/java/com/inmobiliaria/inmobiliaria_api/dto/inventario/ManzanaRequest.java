package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoEtapa;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManzanaRequest(
        @NotBlank @Size(max = 30) String codigo,
        @NotBlank @Size(max = 120) String nombre,
        @NotNull @Min(1) Integer secuencia,
        EstadoEtapa estado
) {
}
