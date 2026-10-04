package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.Cuota;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoCuota;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record SeguimientoCuotaResponse(
        Long cuotaId,
        Long contratoId,
        Long planPagoId,
        int numeroCuota,
        LocalDate fechaVencimiento,
        EstadoCuota estado,
        int diasMora,
        int diasParaVencimiento,
        BigDecimal saldo,
        BigDecimal interesMora,
        LocalDate moraCalculadaHasta,
        BigDecimal totalPendiente
) {
    public static SeguimientoCuotaResponse from(Cuota cuota, LocalDate hoy) {
        int diasParaVencimiento = cuota.getFechaVencimiento().isAfter(hoy)
                ? (int) ChronoUnit.DAYS.between(hoy, cuota.getFechaVencimiento())
                : 0;
        return new SeguimientoCuotaResponse(
                cuota.getId(),
                cuota.getContratoId(),
                cuota.getPlanPagoId(),
                cuota.getNumero(),
                cuota.getFechaVencimiento(),
                cuota.getEstado(),
                cuota.getDiasMora(),
                diasParaVencimiento,
                cuota.getSaldo(),
                cuota.getInteresMora(),
                cuota.getMoraCalculadaHasta(),
                cuota.getSaldo().add(cuota.getInteresMora())
        );
    }
}
