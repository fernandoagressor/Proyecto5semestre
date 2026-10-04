package com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.EstadoReserva;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.ReservaHistorial;
import java.time.LocalDateTime;

public record ReservaHistorialResponse(
        Long id,
        EstadoReserva estadoAnterior,
        EstadoReserva estadoNuevo,
        String motivo,
        Long usuarioId,
        LocalDateTime creadoEn
) {
    public static ReservaHistorialResponse from(ReservaHistorial historial) {
        return new ReservaHistorialResponse(
                historial.getId(),
                historial.getEstadoAnterior(),
                historial.getEstadoNuevo(),
                historial.getMotivo(),
                historial.getUsuarioId(),
                historial.getCreadoEn()
        );
    }
}
