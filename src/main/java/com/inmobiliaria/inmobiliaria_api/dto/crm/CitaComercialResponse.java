package com.inmobiliaria.inmobiliaria_api.dto.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.CitaComercial;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoCitaComercial;
import java.time.LocalDateTime;

public record CitaComercialResponse(
        Long id,
        Long oportunidadId,
        Long asesorId,
        String tipo,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        String lugar,
        EstadoCitaComercial estado,
        String resultado,
        Long version,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn
) {
    public static CitaComercialResponse from(CitaComercial cita) {
        return new CitaComercialResponse(
                cita.getId(),
                cita.getOportunidadId(),
                cita.getAsesorId(),
                cita.getTipo(),
                cita.getFechaInicio(),
                cita.getFechaFin(),
                cita.getLugar(),
                cita.getEstado(),
                cita.getResultado(),
                cita.getVersion(),
                cita.getCreadoEn(),
                cita.getActualizadoEn()
        );
    }
}
