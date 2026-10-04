package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoProyecto;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Proyecto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProyectoResponse(
        Long id,
        String codigo,
        String nombre,
        String descripcion,
        String ciudad,
        String departamento,
        String direccion,
        LocalDate fechaInicio,
        LocalDate fechaFinEstimada,
        EstadoProyecto estado,
        BigDecimal porcentajeAvance,
        Integer maxLotes,
        String imagenPrincipalUrl,
        Long gerenteId,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn
) {
    public static ProyectoResponse from(Proyecto proyecto) {
        return new ProyectoResponse(
                proyecto.getId(),
                proyecto.getCodigo(),
                proyecto.getNombre(),
                proyecto.getDescripcion(),
                proyecto.getCiudad(),
                proyecto.getDepartamento(),
                proyecto.getDireccion(),
                proyecto.getFechaInicio(),
                proyecto.getFechaFinEstimada(),
                proyecto.getEstado(),
                proyecto.getPorcentajeAvance(),
                proyecto.getMaxLotes(),
                proyecto.getImagenPrincipalUrl(),
                proyecto.getGerenteId(),
                proyecto.getCreadoEn(),
                proyecto.getActualizadoEn()
        );
    }
}
