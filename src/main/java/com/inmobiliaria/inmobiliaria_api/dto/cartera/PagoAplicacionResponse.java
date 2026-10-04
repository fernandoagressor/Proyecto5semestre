package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConceptoPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.PagoAplicacion;
import java.math.BigDecimal;

public record PagoAplicacionResponse(Long id, Long cuotaId, Long facturaId, ConceptoPago concepto, BigDecimal valor) {
    public static PagoAplicacionResponse from(PagoAplicacion item) {
        return new PagoAplicacionResponse(item.getId(), item.getCuotaId(), item.getFacturaId(), item.getConcepto(), item.getValorAplicado());
    }
}
