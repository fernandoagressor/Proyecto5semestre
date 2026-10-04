package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.ContratoCliente;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.RolContratoCliente;
import java.math.BigDecimal;

public record ContratoClienteResponse(
        Long id,
        Long clienteId,
        RolContratoCliente rol,
        BigDecimal porcentajeParticipacion
) {
    public static ContratoClienteResponse from(ContratoCliente cliente) {
        return new ContratoClienteResponse(
                cliente.getId(),
                cliente.getClienteId(),
                cliente.getRolEnContrato(),
                cliente.getPorcentajeParticipacion()
        );
    }
}
