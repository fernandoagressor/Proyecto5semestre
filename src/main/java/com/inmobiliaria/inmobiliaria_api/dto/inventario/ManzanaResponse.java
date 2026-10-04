package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoEtapa;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Manzana;

public record ManzanaResponse(
        Long id,
        Long proyectoId,
        Long etapaId,
        String codigo,
        String nombre,
        Integer secuencia,
        EstadoEtapa estado
) {
    public static ManzanaResponse from(Manzana manzana) {
        return new ManzanaResponse(
                manzana.getId(),
                manzana.getProyectoId(),
                manzana.getEtapaId(),
                manzana.getCodigo(),
                manzana.getNombre(),
                manzana.getSecuencia(),
                manzana.getEstado()
        );
    }
}
