package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoLote;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.LoteHistorialEstado;
import java.time.LocalDateTime;

public record HistorialEstadoLoteResponse(
        Long id,
        EstadoLote estadoAnterior,
        EstadoLote estadoNuevo,
        String entidadOrigen,
        Long entidadOrigenId,
        String motivo,
        Long cambiadoPor,
        LocalDateTime creadoEn
) {
    public static HistorialEstadoLoteResponse from(LoteHistorialEstado item) {
        return new HistorialEstadoLoteResponse(
                item.getId(),
                item.getEstadoAnterior(),
                item.getEstadoNuevo(),
                item.getEntidadOrigen(),
                item.getEntidadOrigenId(),
                item.getMotivo(),
                item.getCambiadoPor(),
                item.getCreadoEn()
        );
    }
}
