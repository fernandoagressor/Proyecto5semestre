package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConciliacionMovimiento;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoMovimientoConciliacion;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimientoResponse(Long id, LocalDate fecha, String referencia, String descripcion, BigDecimal valor, Long pagoId, EstadoMovimientoConciliacion estado, BigDecimal diferencia) {
    public static MovimientoResponse from(ConciliacionMovimiento m) {
        return new MovimientoResponse(m.getId(), m.getFechaMovimiento(), m.getReferencia(), m.getDescripcion(), m.getValor(), m.getPagoId(), m.getEstado(), m.getDiferencia());
    }
}
