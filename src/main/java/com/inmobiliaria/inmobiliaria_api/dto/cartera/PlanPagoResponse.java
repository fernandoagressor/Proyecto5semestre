package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoPlanPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.OrigenPlanPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.PlanPago;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PlanPagoResponse(
        Long id, Long contratoId, int versionPlan,
        BigDecimal capitalFinanciado, BigDecimal tasaEfectivaAnual,
        BigDecimal tasaMoraEfectivaAnual, int numeroCuotas,
        Periodicidad periodicidad, LocalDate fechaPrimeraCuota,
        EstadoPlanPago estado, OrigenPlanPago origen, Long version,
        List<CuotaResponse> cuotas
) {
    public static PlanPagoResponse from(PlanPago plan, List<CuotaResponse> cuotas) {
        return new PlanPagoResponse(
                plan.getId(), plan.getContratoId(), plan.getVersionPlan(),
                plan.getCapitalFinanciado(), plan.getTasaEfectivaAnual(),
                plan.getTasaMoraEfectivaAnual(), plan.getNumeroCuotas(),
                plan.getPeriodicidad(), plan.getFechaPrimeraCuota(),
                plan.getEstado(), plan.getOrigen(), plan.getVersion(), cuotas
        );
    }
}
