package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.CanalCobro;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.GestionCobro;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GestionCobroResponse(Long id, Long contratoId, Long cuotaId, Long gestorId, CanalCobro canal, String resultado, String detalle, LocalDate promesaPagoFecha, BigDecimal promesaPagoValor, LocalDateTime fechaGestion) {
    public static GestionCobroResponse from(GestionCobro g) {
        return new GestionCobroResponse(g.getId(), g.getContratoId(), g.getCuotaId(), g.getGestorId(), g.getCanal(), g.getResultado(), g.getDetalle(), g.getPromesaPagoFecha(), g.getPromesaPagoValor(), g.getFechaGestion());
    }
}
