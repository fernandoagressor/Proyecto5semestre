package com.inmobiliaria.inmobiliaria_api.dto.ventas.desistimiento;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DesistimientoDtos {
    private DesistimientoDtos() { }

    /** Liquidación propuesta antes de registrar el desistimiento. */
    public record Liquidacion(Long contratoId, String contrato, String estadoContrato, String cliente, String proyecto,
                              String lote, BigDecimal valorContrato, BigDecimal totalPagado,
                              BigDecimal porcentajePenalidad, BigDecimal valorPenalidad, BigDecimal otrosDescuentos,
                              BigDecimal valorDevolver, long cuotasAbiertas, long comisionesPendientes,
                              long comisionesPagadas) { }

    public record SolicitudRequest(
            @NotNull Long contratoId,
            @NotBlank @Pattern(regexp = "CLIENTE|EMPRESA") String origen,
            @NotBlank @Size(max = 1000) String motivo,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal porcentajePenalidad,
            @DecimalMin("0") BigDecimal otrosDescuentos,
            LocalDate fechaDevolucion,
            @Size(max = 2000) String observaciones
    ) { }

    public record RevisionRequest(@Size(max = 1000) String comentario, @NotNull Long version) { }

    public record Response(Long id, String codigo, Long contratoId, String contrato, String cliente, String proyecto,
                           String lote, String origen, String motivo, BigDecimal totalPagado,
                           BigDecimal porcentajePenalidad, BigDecimal valorPenalidad, BigDecimal otrosDescuentos,
                           BigDecimal valorDevolver, LocalDate fechaDevolucion, String observaciones, String estado,
                           Long solicitadoPor, Long revisadoPor, LocalDateTime revisadoEn, String comentarioRevision,
                           int cuotasCanceladas, int comisionesAnuladas, long version, LocalDateTime creadoEn) { }
}
