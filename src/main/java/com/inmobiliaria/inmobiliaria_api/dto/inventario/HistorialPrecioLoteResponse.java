package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.LotePrecioHistorial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HistorialPrecioLoteResponse(
        Long id,
        BigDecimal valorAnterior,
        BigDecimal valorNuevo,
        LocalDateTime vigenteDesde,
        LocalDateTime vigenteHasta,
        String motivo,
        Long cambiadoPor
) {
    public static HistorialPrecioLoteResponse from(LotePrecioHistorial item) {
        return new HistorialPrecioLoteResponse(
                item.getId(),
                item.getValorAnterior(),
                item.getValorNuevo(),
                item.getVigenteDesde(),
                item.getVigenteHasta(),
                item.getMotivo(),
                item.getCambiadoPor()
        );
    }
}
