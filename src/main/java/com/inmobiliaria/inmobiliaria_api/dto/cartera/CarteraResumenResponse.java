package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import java.math.BigDecimal;

public record CarteraResumenResponse(
        long contratosVigentes,
        long planesActivos,
        long contratosSinPlan,
        BigDecimal carteraTotal,
        BigDecimal totalRecaudado,
        BigDecimal saldoPendiente,
        BigDecimal carteraVencida,
        BigDecimal porcentajeMora,
        long cuotasProximas,
        long pagosPendientes
) {
}
