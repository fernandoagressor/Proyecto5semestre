package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoEtapa;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EtapaProyecto;
import java.time.LocalDate;

public record EtapaResponse(
        Long id,
        Long proyectoId,
        String codigo,
        String nombre,
        Integer secuencia,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        EstadoEtapa estado
) {
    public static EtapaResponse from(EtapaProyecto etapa) {
        return new EtapaResponse(
                etapa.getId(),
                etapa.getProyectoId(),
                etapa.getCodigo(),
                etapa.getNombre(),
                etapa.getSecuencia(),
                etapa.getFechaInicio(),
                etapa.getFechaFin(),
                etapa.getEstado()
        );
    }
}
