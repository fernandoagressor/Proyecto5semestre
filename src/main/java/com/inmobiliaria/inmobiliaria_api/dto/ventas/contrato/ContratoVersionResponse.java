package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import com.fasterxml.jackson.databind.JsonNode;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.ContratoVersion;
import java.time.LocalDateTime;

public record ContratoVersionResponse(
        Long id,
        int numeroVersion,
        Long archivoId,
        JsonNode datos,
        String motivo,
        Long creadoPor,
        boolean vigente,
        LocalDateTime creadoEn
) {
    public static ContratoVersionResponse from(ContratoVersion version) {
        return new ContratoVersionResponse(
                version.getId(),
                version.getNumeroVersion(),
                version.getArchivoId(),
                version.getDatosJson(),
                version.getMotivo(),
                version.getCreadoPor(),
                version.isVigente(),
                version.getCreadoEn()
        );
    }
}
