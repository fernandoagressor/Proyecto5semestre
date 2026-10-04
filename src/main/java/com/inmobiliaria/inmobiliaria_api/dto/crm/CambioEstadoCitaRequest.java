package com.inmobiliaria.inmobiliaria_api.dto.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoCitaComercial;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambioEstadoCitaRequest(
        @NotNull EstadoCitaComercial estado,
        @Size(max = 5000) String resultado
) {
}
