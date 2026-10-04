package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoRefinanciacion;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.Refinanciacion;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.RefinanciacionCuota;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RefinanciacionResponse(Long id, String codigo, Long contratoId, BigDecimal saldoRefinanciado, int numeroCuotas, BigDecimal tasaNueva, LocalDate fechaPrimeraCuota, String motivo, Long solicitadoPor, Long aprobadoPor, EstadoRefinanciacion estado, LocalDateTime fechaDecision, Long planPagoGeneradoId, List<CuotaPropuesta> cuotas) {
    public record CuotaPropuesta(int numero, LocalDate fecha, BigDecimal capital, BigDecimal interes, BigDecimal total) {
        public static CuotaPropuesta from(RefinanciacionCuota c) { return new CuotaPropuesta(c.getNumero(), c.getFechaVencimiento(), c.getCapital(), c.getInteres(), c.getValorTotal()); }
    }
    public static RefinanciacionResponse from(Refinanciacion r, List<RefinanciacionCuota> cuotas) {
        return new RefinanciacionResponse(r.getId(), r.getCodigo(), r.getContratoId(), r.getSaldoRefinanciado(), r.getNumeroCuotasNuevo(), r.getTasaNueva(), r.getFechaPrimeraCuota(), r.getMotivo(), r.getSolicitadoPor(), r.getAprobadoPor(), r.getEstado(), r.getFechaDecision(), r.getPlanPagoGeneradoId(), cuotas.stream().map(CuotaPropuesta::from).toList());
    }
}
