package com.inmobiliaria.inmobiliaria_api.dto.gerencia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;

public final class ReporteDtos {
    private ReporteDtos() { }

    public record SolicitarRequest(Long proyectoId,
                                   @NotBlank @Size(max = 60) String tipoReporte,
                                   @NotBlank String formato,
                                   Map<String, Object> filtros) { }

    public record CompletarRequest(Long archivoId,
                                   @Size(max = 700) String detalleError) { }

    public record Response(Long id, Long proyectoId, Long solicitadoPor,
                           String tipoReporte, String filtrosJson, String formato,
                           Long archivoId, String estado, LocalDateTime generadoEn,
                           LocalDateTime expiraEn, String detalleError,
                           LocalDateTime creadoEn, long version) { }
}
