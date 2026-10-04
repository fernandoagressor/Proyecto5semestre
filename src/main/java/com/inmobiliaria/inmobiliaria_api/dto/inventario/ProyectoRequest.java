package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ProyectoRequest(
        @NotBlank @Size(max = 40) String codigo,
        @NotBlank @Size(max = 180) String nombre,
        @Size(max = 10000) String descripcion,
        @NotBlank @Size(max = 100) String ciudad,
        @NotBlank @Size(max = 100) String departamento,
        @Size(max = 240) String direccion,
        LocalDate fechaInicio,
        LocalDate fechaFinEstimada,
        @Min(1) Integer maxLotes,
        @Size(max = 500) String imagenPrincipalUrl,
        Long gerenteId
) {
}
