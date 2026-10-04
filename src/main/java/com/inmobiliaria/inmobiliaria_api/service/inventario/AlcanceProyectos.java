package com.inmobiliaria.inmobiliaria_api.service.inventario;

import java.util.Set;

public record AlcanceProyectos(
        boolean empresaCompleta,
        boolean registrosPropios,
        Set<Long> proyectos
) {
    public boolean permite(Long proyectoId, Long gerenteId, Long usuarioId) {
        return empresaCompleta
                || proyectos.contains(proyectoId)
                || (registrosPropios && usuarioId.equals(gerenteId));
    }
}
