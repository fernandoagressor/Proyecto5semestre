package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.OrigenPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.Pago;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PagoResponse(
        Long id, String codigo, Long contratoId, Long reservaId, Long clienteId,
        Long metodoPagoId, LocalDateTime fechaPago, BigDecimal valor,
        String referencia, Long comprobanteId, OrigenPago origen, EstadoPago estado,
        Long registradoPor, Long validadoPor, LocalDateTime validadoEn,
        String observaciones, Long version, List<PagoAplicacionResponse> aplicaciones
) {
    public static PagoResponse from(Pago pago, List<PagoAplicacionResponse> aplicaciones) {
        return new PagoResponse(
                pago.getId(), pago.getCodigo(), pago.getContratoId(), pago.getReservaId(), pago.getClienteId(),
                pago.getMetodoPagoId(), pago.getFechaPago(), pago.getValor(), pago.getReferencia(),
                pago.getComprobanteId(), pago.getOrigen(), pago.getEstado(), pago.getRegistradoPor(),
                pago.getValidadoPor(), pago.getValidadoEn(), pago.getObservaciones(), pago.getVersion(), aplicaciones
        );
    }
}
