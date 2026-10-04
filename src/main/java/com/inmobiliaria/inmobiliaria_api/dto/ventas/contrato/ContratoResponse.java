package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.Contrato;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.EstadoContrato;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.TipoContrato;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ContratoResponse(
        Long id,
        Long empresaId,
        Long proyectoId,
        String codigo,
        Long oportunidadId,
        Long reservaId,
        Long clientePrincipalId,
        Long loteId,
        Long asesorId,
        TipoContrato tipoContrato,
        BigDecimal valorLote,
        BigDecimal otrosConceptos,
        BigDecimal descuento,
        BigDecimal valorTotal,
        BigDecimal cuotaInicial,
        BigDecimal saldoFinanciado,
        int numeroCuotas,
        LocalDate fechaFirma,
        LocalDate fechaInicio,
        LocalDate fechaFinalizacion,
        EstadoContrato estado,
        String motivoAnulacion,
        LocalDateTime anuladoEn,
        Long anuladoPor,
        Long version,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn,
        List<ContratoClienteResponse> clientes,
        List<ContratoVersionResponse> versiones,
        List<ContratoFirmanteResponse> firmantes
) {
    public static ContratoResponse from(
            Contrato contrato,
            List<ContratoClienteResponse> clientes,
            List<ContratoVersionResponse> versiones,
            List<ContratoFirmanteResponse> firmantes
    ) {
        return new ContratoResponse(
                contrato.getId(),
                contrato.getEmpresaId(),
                contrato.getProyectoId(),
                contrato.getCodigo(),
                contrato.getOportunidadId(),
                contrato.getReservaId(),
                contrato.getClientePrincipalId(),
                contrato.getLoteId(),
                contrato.getAsesorId(),
                contrato.getTipoContrato(),
                contrato.getValorLote(),
                contrato.getOtrosConceptos(),
                contrato.getDescuento(),
                contrato.getValorTotal(),
                contrato.getCuotaInicial(),
                contrato.getSaldoFinanciado(),
                contrato.getNumeroCuotas(),
                contrato.getFechaFirma(),
                contrato.getFechaInicio(),
                contrato.getFechaFinalizacion(),
                contrato.getEstado(),
                contrato.getMotivoAnulacion(),
                contrato.getAnuladoEn(),
                contrato.getAnuladoPor(),
                contrato.getVersion(),
                contrato.getCreadoEn(),
                contrato.getActualizadoEn(),
                clientes,
                versiones,
                firmantes
        );
    }
}
