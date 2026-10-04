package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoReversion;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.PagoReversion;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReversionResponse(Long id, Long pagoId, BigDecimal valor, String motivo, Long solicitadoPor, Long aprobadoPor, EstadoReversion estado, LocalDateTime ejecutadaEn) {
    public static ReversionResponse from(PagoReversion r) {
        return new ReversionResponse(r.getId(), r.getPagoId(), r.getValorRevertido(), r.getMotivo(), r.getSolicitadoPor(), r.getAprobadoPor(), r.getEstado(), r.getEjecutadaEn());
    }
}
