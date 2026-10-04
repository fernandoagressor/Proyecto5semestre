package com.inmobiliaria.inmobiliaria_api.dto.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.TipoActividadComercial;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ActividadComercialRequest(
        @NotNull TipoActividadComercial tipo,
        @Size(max = 120) String resultado,
        @NotBlank @Size(max = 5000) String descripcion,
        @NotNull LocalDateTime fechaActividad,
        @Size(max = 200) String proximaAccion,
        LocalDateTime fechaProximaAccion
) {
}
