package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.Cuota;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MoraResponse(
        Long cuotaId,
        Long contratoId,
        Long planPagoId,
        int numeroCuota,
        LocalDate fechaVencimiento,
        int diasMora,
        BigDecimal saldo,
        BigDecimal interesMora,
        LocalDate moraCalculadaHasta,
        BigDecimal totalVencido
) {
    public static MoraResponse from(Cuota cuota) {
        return new MoraResponse(
                cuota.getId(), cuota.getContratoId(), cuota.getPlanPagoId(),
                cuota.getNumero(), cuota.getFechaVencimiento(), cuota.getDiasMora(),
                cuota.getSaldo(), cuota.getInteresMora(), cuota.getMoraCalculadaHasta(),
                cuota.getSaldo().add(cuota.getInteresMora())
        );
    }
}
