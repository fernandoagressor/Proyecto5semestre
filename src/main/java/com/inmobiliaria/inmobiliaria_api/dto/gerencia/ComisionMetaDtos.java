package com.inmobiliaria.inmobiliaria_api.dto.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.Comision;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoComision;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoMeta;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.MetaComercial;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.ReglaComision;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.TipoReglaComision;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class ComisionMetaDtos {
    private ComisionMetaDtos() { }

    public record ReglaRequest(Long proyectoId,
                               @NotBlank @Size(max = 140) String nombre,
                               @NotNull TipoReglaComision tipo,
                               @NotNull @DecimalMin("0") BigDecimal valor,
                               @NotBlank @Size(max = 40) String condicionCausacion,
                               @NotNull LocalDate vigenteDesde,
                               LocalDate vigenteHasta) { }

    public record ReglaResponse(Long id, Long proyectoId, String nombre,
                                TipoReglaComision tipo, BigDecimal valor,
                                String condicionCausacion, LocalDate vigenteDesde,
                                LocalDate vigenteHasta, boolean activo, Long version) {
        public static ReglaResponse from(ReglaComision r) {
            return new ReglaResponse(r.getId(), r.getProyectoId(), r.getNombre(), r.getTipo(),
                    r.getValor(), r.getCondicionCausacion(), r.getVigenteDesde(),
                    r.getVigenteHasta(), r.isActivo(), r.getVersion());
        }
    }

    public record ProyectarComisionRequest(@NotNull Long contratoId,
                                            @NotNull Long reglaComisionId,
                                            @DecimalMin("0") BigDecimal baseCalculo) { }

    public record FechaRequest(@NotNull LocalDate fecha) { }

    public record ComisionResponse(Long id, Long proyectoId, Long asesorId,
                                   Long contratoId, Long reglaComisionId,
                                   BigDecimal baseCalculo, BigDecimal porcentaje,
                                   BigDecimal valorComision, EstadoComision estado,
                                   LocalDate fechaCausacion, LocalDate fechaPago,
                                   Long aprobadoPor, Long version) {
        public static ComisionResponse from(Comision c) {
            return new ComisionResponse(c.getId(), c.getProyectoId(), c.getAsesorId(),
                    c.getContratoId(), c.getReglaComisionId(), c.getBaseCalculo(),
                    c.getPorcentaje(), c.getValorComision(), c.getEstado(),
                    c.getFechaCausacion(), c.getFechaPago(), c.getAprobadoPor(), c.getVersion());
        }
    }

    public record MetaRequest(Long proyectoId, Long asesorId,
                              @NotBlank @Pattern(regexp = "^[0-9]{4}-(0[1-9]|1[0-2])$") String periodo,
                              @NotNull @DecimalMin("0") BigDecimal valorMeta,
                              @Min(0) int cantidadMeta) { }

    public record MetaValoresRequest(@NotNull @DecimalMin("0") BigDecimal valorMeta,
                                     @Min(0) int cantidadMeta) { }

    public record MetaResponse(Long id, Long proyectoId, Long asesorId, String periodo,
                               BigDecimal valorMeta, int cantidadMeta, EstadoMeta estado,
                               Long aprobadoPor, Long version) {
        public static MetaResponse from(MetaComercial m) {
            return new MetaResponse(m.getId(), m.getProyectoId(), m.getAsesorId(), m.getPeriodo(),
                    m.getValorMeta(), m.getCantidadMeta(), m.getEstado(), m.getAprobadoPor(),
                    m.getVersion());
        }
    }

    public record CumplimientoMetaResponse(Long metaId, String periodo, Long proyectoId,
                                           Long asesorId, BigDecimal valorMeta,
                                           BigDecimal valorLogrado, BigDecimal porcentajeValor,
                                           int cantidadMeta, long cantidadLograda,
                                           BigDecimal porcentajeCantidad, EstadoMeta estado) { }
}
