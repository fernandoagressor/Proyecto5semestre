package com.inmobiliaria.inmobiliaria_api.dto.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EtapaOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Oportunidad;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OportunidadResponse(
        Long id,
        Long empresaId,
        Long proyectoId,
        String codigo,
        Long prospectoId,
        Long clienteId,
        Long asesorId,
        EtapaOportunidad etapaActual,
        EstadoOportunidad estado,
        BigDecimal probabilidadCierre,
        BigDecimal valorEstimado,
        LocalDateTime fechaUltimaActividad,
        LocalDateTime fechaProximaAccion,
        String motivoPerdida,
        LocalDateTime fechaCierre,
        Long version,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn
) {
    public static OportunidadResponse from(Oportunidad oportunidad) {
        return new OportunidadResponse(
                oportunidad.getId(),
                oportunidad.getEmpresaId(),
                oportunidad.getProyectoId(),
                oportunidad.getCodigo(),
                oportunidad.getProspectoId(),
                oportunidad.getClienteId(),
                oportunidad.getAsesorId(),
                oportunidad.getEtapaActual(),
                oportunidad.getEstado(),
                oportunidad.getProbabilidadCierre(),
                oportunidad.getValorEstimado(),
                oportunidad.getFechaUltimaActividad(),
                oportunidad.getFechaProximaAccion(),
                oportunidad.getMotivoPerdida(),
                oportunidad.getFechaCierre(),
                oportunidad.getVersion(),
                oportunidad.getCreadoEn(),
                oportunidad.getActualizadoEn()
        );
    }
}
