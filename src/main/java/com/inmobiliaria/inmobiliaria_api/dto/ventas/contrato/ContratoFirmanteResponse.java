package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.ContratoFirmante;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.EstadoFirmante;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.TipoFirmante;
import java.time.LocalDateTime;
import java.util.List;

public record ContratoFirmanteResponse(
        Long id,
        Long personaId,
        TipoFirmante tipoFirmante,
        int ordenFirma,
        EstadoFirmante estado,
        LocalDateTime fechaProgramada,
        LocalDateTime fechaFirma,
        Long archivoFirmaId,
        Long version,
        List<FirmaContratoResponse> eventos
) {
    public static ContratoFirmanteResponse from(
            ContratoFirmante firmante,
            List<FirmaContratoResponse> eventos
    ) {
        return new ContratoFirmanteResponse(
                firmante.getId(),
                firmante.getPersonaId(),
                firmante.getTipoFirmante(),
                firmante.getOrdenFirma(),
                firmante.getEstado(),
                firmante.getFechaProgramada(),
                firmante.getFechaFirma(),
                firmante.getArchivoFirmaId(),
                firmante.getVersion(),
                eventos
        );
    }
}
