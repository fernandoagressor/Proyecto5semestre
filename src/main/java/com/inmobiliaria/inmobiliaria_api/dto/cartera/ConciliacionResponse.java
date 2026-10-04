package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConciliacionBancaria;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoConciliacion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ConciliacionResponse(Long id, String codigo, String cuentaBancaria, LocalDate fechaDesde, LocalDate fechaHasta, BigDecimal saldoExtracto, BigDecimal totalConciliado, EstadoConciliacion estado, Long responsableId, LocalDateTime cerradaEn, List<MovimientoResponse> movimientos) {
    public static ConciliacionResponse from(ConciliacionBancaria c, List<MovimientoResponse> movimientos) {
        return new ConciliacionResponse(c.getId(), c.getCodigo(), c.getCuentaBancaria(), c.getFechaDesde(), c.getFechaHasta(), c.getSaldoExtracto(), c.getTotalConciliado(), c.getEstado(), c.getResponsableId(), c.getCerradaEn(), movimientos);
    }
}
