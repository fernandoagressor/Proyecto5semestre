package com.inmobiliaria.inmobiliaria_api.dto.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.EtapaOportunidad;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CambioEtapaOportunidadRequest(
        @NotNull EtapaOportunidad etapa,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal probabilidad,
        @Size(max = 500) String observacion
) {
}
