package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoEtapa;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Crear o editar una etapa. Código y secuencia son opcionales: si no llegan, se asignan solos
 * (E01, E02…). Al crear, {@code cantidadManzanas} genera de una vez esas manzanas (M01, M02…).
 */
public record EtapaRequest(
        @Size(max = 30) String codigo,
        @NotBlank(message = "Escribe el nombre de la etapa") @Size(max = 120) String nombre,
        @Min(1) Integer secuencia,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        EstadoEtapa estado,
        @Min(value = 0, message = "La cantidad de manzanas no puede ser negativa")
        @Max(value = 100, message = "Máximo 100 manzanas por etapa en una sola vez")
        Integer cantidadManzanas
) {
}
