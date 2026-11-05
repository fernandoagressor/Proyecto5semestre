package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoEtapa;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Crear o editar una manzana. Código y secuencia son opcionales: si no llegan, se asignan solos. */
public record ManzanaRequest(
        @Size(max = 30) String codigo,
        @NotBlank(message = "Escribe el nombre de la manzana") @Size(max = 120) String nombre,
        @Min(1) Integer secuencia,
        EstadoEtapa estado
) {
}
