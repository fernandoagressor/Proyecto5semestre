package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CarteraContratoResponse(
        Long contratoId,
        String contratoCodigo,
        Long proyectoId,
        String proyectoNombre,
        Long clienteId,
        String clienteNombre,
        String clienteDocumento,
        Long loteId,
        String loteCodigo,
        BigDecimal valorContrato,
        BigDecimal cuotaInicial,
        BigDecimal capitalFinanciado,
        Long planPagoId,
        String estadoPlan,
        int numeroCuotas,
        BigDecimal totalProgramado,
        BigDecimal totalPagado,
        BigDecimal saldoPendiente,
        BigDecimal saldoVencido,
        LocalDate proximoVencimiento,
        long cuotasPendientes,
        long cuotasVencidas
) {
}
