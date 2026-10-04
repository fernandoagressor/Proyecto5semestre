package com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.EstadoPagoReserva;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.EstadoReserva;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.Reserva;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReservaResponse(
        Long id,
        Long empresaId,
        Long proyectoId,
        String codigo,
        Long oportunidadId,
        Long simulacionId,
        Long clienteId,
        Long loteId,
        Long asesorId,
        LocalDateTime fechaReserva,
        LocalDateTime fechaVencimiento,
        BigDecimal valorReserva,
        String formaPago,
        EstadoPagoReserva estadoPago,
        EstadoReserva estado,
        String motivoCancelacion,
        Long comprobanteArchivoId,
        LocalDateTime pagoConfirmadoEn,
        Long pagoConfirmadoPor,
        Long version,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn,
        List<ReservaHistorialResponse> historial
) {
    public static ReservaResponse from(
            Reserva reserva,
            List<ReservaHistorialResponse> historial
    ) {
        return new ReservaResponse(
                reserva.getId(),
                reserva.getEmpresaId(),
                reserva.getProyectoId(),
                reserva.getCodigo(),
                reserva.getOportunidadId(),
                reserva.getSimulacionId(),
                reserva.getClienteId(),
                reserva.getLoteId(),
                reserva.getAsesorId(),
                reserva.getFechaReserva(),
                reserva.getFechaVencimiento(),
                reserva.getValorReserva(),
                reserva.getFormaPago(),
                reserva.getEstadoPago(),
                reserva.getEstado(),
                reserva.getMotivoCancelacion(),
                reserva.getComprobanteArchivoId(),
                reserva.getPagoConfirmadoEn(),
                reserva.getPagoConfirmadoPor(),
                reserva.getVersion(),
                reserva.getCreadoEn(),
                reserva.getActualizadoEn(),
                historial
        );
    }
}
