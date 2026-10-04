package com.inmobiliaria.inmobiliaria_api.dto.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.ActividadComercial;
import com.inmobiliaria.inmobiliaria_api.entity.crm.TipoActividadComercial;
import java.time.LocalDateTime;

public record ActividadComercialResponse(
        Long id,
        Long oportunidadId,
        Long asesorId,
        TipoActividadComercial tipo,
        String resultado,
        String descripcion,
        LocalDateTime fechaActividad,
        String proximaAccion,
        LocalDateTime fechaProximaAccion,
        LocalDateTime creadoEn
) {
    public static ActividadComercialResponse from(
            ActividadComercial actividad
    ) {
        return new ActividadComercialResponse(
                actividad.getId(),
                actividad.getOportunidadId(),
                actividad.getAsesorId(),
                actividad.getTipo(),
                actividad.getResultado(),
                actividad.getDescripcion(),
                actividad.getFechaActividad(),
                actividad.getProximaAccion(),
                actividad.getFechaProximaAccion(),
                actividad.getCreadoEn()
        );
    }
}
