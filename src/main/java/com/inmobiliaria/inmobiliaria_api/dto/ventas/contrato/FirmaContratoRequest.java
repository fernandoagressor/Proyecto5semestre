package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.EstadoValidacionFirma;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.MetodoFirma;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record FirmaContratoRequest(
        @NotNull MetodoFirma metodo,
        @Size(max = 100) String proveedor,
        @Size(max = 180) String referenciaExterna,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}") String hashDocumento,
        @Size(max = 64) String ipOrigen,
        LocalDateTime firmadaEn,
        @NotNull EstadoValidacionFirma estadoValidacion,
        Long archivoFirmaId
) {
}
