package com.inmobiliaria.inmobiliaria_api.dto.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.OportunidadLote;

public record OportunidadLoteResponse(
        Long id,
        Long oportunidadId,
        Long loteId,
        int prioridad,
        boolean seleccionado
) {
    public static OportunidadLoteResponse from(OportunidadLote relacion) {
        return new OportunidadLoteResponse(
                relacion.getId(),
                relacion.getOportunidadId(),
                relacion.getLoteId(),
                relacion.getPrioridad(),
                relacion.isSeleccionado()
        );
    }
}
