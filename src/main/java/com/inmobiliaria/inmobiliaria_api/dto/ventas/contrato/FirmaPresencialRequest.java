package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Firma en la oficina: el contrato firmado por todos (PDF o imagen ya subido a documentos) y,
 * opcionalmente, cuándo se firmó (por defecto, ahora).
 */
public record FirmaPresencialRequest(
        @NotNull(message = "Adjunta el contrato firmado") Long archivoId,
        LocalDateTime firmadaEn
) {
}
