package com.inmobiliaria.inmobiliaria_api.dto.simulacion;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.EstadoSimulacion;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Simulacion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SimulacionResponse(
        Long id,
        Long empresaId,
        Long proyectoId,
        String codigo,
        Long oportunidadId,
        Long loteId,
        Long creadoPor,
        BigDecimal valorLote,
        BigDecimal descuento,
        BigDecimal cuotaInicial,
        BigDecimal saldoFinanciar,
        int numeroCuotas,
        BigDecimal tasaEfectivaAnual,
        Periodicidad periodicidad,
        LocalDateTime vigenteHasta,
        EstadoSimulacion estado,
        Long version,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn,
        List<SimulacionCuotaResponse> cuotas
) {
    public static SimulacionResponse from(
            Simulacion simulacion,
            List<SimulacionCuotaResponse> cuotas
    ) {
        return new SimulacionResponse(
                simulacion.getId(),
                simulacion.getEmpresaId(),
                simulacion.getProyectoId(),
                simulacion.getCodigo(),
                simulacion.getOportunidadId(),
                simulacion.getLoteId(),
                simulacion.getCreadoPor(),
                simulacion.getValorLote(),
                simulacion.getDescuento(),
                simulacion.getCuotaInicial(),
                simulacion.getSaldoFinanciar(),
                simulacion.getNumeroCuotas(),
                simulacion.getTasaEfectivaAnual(),
                simulacion.getPeriodicidad(),
                simulacion.getVigenteHasta(),
                simulacion.getEstado(),
                simulacion.getVersion(),
                simulacion.getCreadoEn(),
                simulacion.getActualizadoEn(),
                cuotas
        );
    }
}
