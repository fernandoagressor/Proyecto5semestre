package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.RolContratoCliente;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ContratoClienteRequest(
        @NotNull Long clienteId,
        @NotNull RolContratoCliente rol,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal porcentajeParticipacion
) {
}
