package com.inmobiliaria.inmobiliaria_api.dto.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.TemperaturaProspecto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProspectoCalificacionRequest(
        @NotNull EstadoProspecto estado,
        @NotNull TemperaturaProspecto temperatura,
        @Size(max = 5000) String justificacion
) {
}
