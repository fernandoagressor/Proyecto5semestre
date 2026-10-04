package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContratoDocumentoRequest(
        @NotNull Long archivoId,
        @NotBlank @Size(max = 400) String motivo
) {
}
