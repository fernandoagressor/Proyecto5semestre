package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo para rechazar el contrato firmado que subió el cliente; lo ve en su portal. */
public record FirmaRechazoRequest(
        @NotBlank(message = "Escribe el motivo para que el cliente lo corrija") @Size(max = 400) String motivo
) {
}
