package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.TipoFirmante;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record ContratoFirmanteRequest(
        @NotNull Long personaId,
        @NotNull TipoFirmante tipoFirmante,
        @Min(1) int ordenFirma,
        LocalDateTime fechaProgramada
) {
}
