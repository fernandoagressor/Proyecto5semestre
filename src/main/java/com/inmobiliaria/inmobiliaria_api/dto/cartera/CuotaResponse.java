package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.Cuota;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoCuota;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CuotaResponse(
        Long id, int numero, LocalDate fechaVencimiento,
        BigDecimal capital, BigDecimal interes, BigDecimal otrosConceptos,
        BigDecimal valorProgramado, BigDecimal valorPagado,
        BigDecimal saldo, int diasMora, BigDecimal interesMora,
        LocalDate moraCalculadaHasta, EstadoCuota estado, Long version
) {
    public static CuotaResponse from(Cuota cuota) {
        return new CuotaResponse(
                cuota.getId(), cuota.getNumero(), cuota.getFechaVencimiento(),
                cuota.getCapital(), cuota.getInteres(), cuota.getOtrosConceptos(),
                cuota.getValorProgramado(), cuota.getValorPagado(),
                cuota.getSaldo(), cuota.getDiasMora(), cuota.getInteresMora(),
                cuota.getMoraCalculadaHasta(), cuota.getEstado(), cuota.getVersion()
        );
    }
}
