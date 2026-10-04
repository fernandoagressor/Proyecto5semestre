package com.inmobiliaria.inmobiliaria_api.dto.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.FuenteProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.TemperaturaProspecto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProspectoRequest(
        Long proyectoId,
        Long asesorId,
        @NotBlank @Size(max = 160) String nombres,
        @Size(max = 40) String telefono,
        @Email @Size(max = 180) String correo,
        @NotNull FuenteProspecto fuente,
        @DecimalMin("0.00") BigDecimal presupuesto,
        @NotNull TemperaturaProspecto temperatura,
        @Size(max = 5000) String observaciones
) {
}
