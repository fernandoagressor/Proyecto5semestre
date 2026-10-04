package com.inmobiliaria.inmobiliaria_api.dto.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.EtapaOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.OportunidadEtapaHistorial;
import java.time.LocalDateTime;

public record HistorialEtapaResponse(
        Long id,
        EtapaOportunidad etapaAnterior,
        EtapaOportunidad etapaNueva,
        Long usuarioId,
        String observacion,
        LocalDateTime creadoEn
) {
    public static HistorialEtapaResponse from(
            OportunidadEtapaHistorial historial
    ) {
        return new HistorialEtapaResponse(
                historial.getId(),
                historial.getEtapaAnterior(),
                historial.getEtapaNueva(),
                historial.getUsuarioId(),
                historial.getObservacion(),
                historial.getCreadoEn()
        );
    }
}
