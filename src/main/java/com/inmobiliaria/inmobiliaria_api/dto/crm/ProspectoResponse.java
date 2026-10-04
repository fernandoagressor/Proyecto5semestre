package com.inmobiliaria.inmobiliaria_api.dto.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.FuenteProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Prospecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.TemperaturaProspecto;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProspectoResponse(
        Long id,
        Long empresaId,
        Long proyectoId,
        Long asesorId,
        String nombres,
        String telefono,
        String correo,
        FuenteProspecto fuente,
        BigDecimal presupuesto,
        TemperaturaProspecto temperatura,
        EstadoProspecto estado,
        String observaciones,
        Long version,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn
) {
    public static ProspectoResponse from(Prospecto prospecto) {
        return new ProspectoResponse(
                prospecto.getId(),
                prospecto.getEmpresaId(),
                prospecto.getProyectoId(),
                prospecto.getAsesorId(),
                prospecto.getNombres(),
                prospecto.getTelefono(),
                prospecto.getCorreo(),
                prospecto.getFuente(),
                prospecto.getPresupuesto(),
                prospecto.getTemperatura(),
                prospecto.getEstado(),
                prospecto.getObservaciones(),
                prospecto.getVersion(),
                prospecto.getCreadoEn(),
                prospecto.getActualizadoEn()
        );
    }
}
