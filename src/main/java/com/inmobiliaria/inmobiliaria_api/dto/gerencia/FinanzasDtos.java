package com.inmobiliaria.inmobiliaria_api.dto.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.CategoriaCosto;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.CostoProyecto;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoCosto;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoPresupuesto;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.PresupuestoProyecto;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.TipoCategoriaCosto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class FinanzasDtos {
    private FinanzasDtos() { }

    public record CategoriaRequest(@NotBlank @Size(max = 40) String codigo,
                                   @NotBlank @Size(max = 140) String nombre,
                                   @NotNull TipoCategoriaCosto tipo) { }

    public record CategoriaResponse(Long id, String codigo, String nombre,
                                    TipoCategoriaCosto tipo, boolean activo) {
        public static CategoriaResponse from(CategoriaCosto c) {
            return new CategoriaResponse(c.getId(), c.getCodigo(), c.getNombre(), c.getTipo(), c.isActivo());
        }
    }

    public record PresupuestoRequest(@NotNull Long proyectoId, @NotNull Long categoriaCostoId,
                                     @NotNull LocalDate periodoDesde, @NotNull LocalDate periodoHasta,
                                     @NotNull @DecimalMin("0") BigDecimal valorPresupuestado) { }

    public record PresupuestoResponse(Long id, Long proyectoId, Long categoriaCostoId,
                                      LocalDate periodoDesde, LocalDate periodoHasta,
                                      BigDecimal valorPresupuestado, EstadoPresupuesto estado,
                                      Long aprobadoPor, Long creadoPor, Long version) {
        public static PresupuestoResponse from(PresupuestoProyecto p) {
            return new PresupuestoResponse(p.getId(), p.getProyectoId(), p.getCategoriaCostoId(),
                    p.getPeriodoDesde(), p.getPeriodoHasta(), p.getValorPresupuestado(),
                    p.getEstado(), p.getAprobadoPor(), p.getCreadoPor(), p.getVersion());
        }
    }

    public record CostoRequest(@NotNull Long proyectoId, @NotNull Long categoriaCostoId,
                               @NotNull LocalDate fechaCosto,
                               @NotBlank @Size(max = 220) String concepto,
                               @Size(max = 180) String proveedor,
                               @Size(max = 100) String referencia,
                               @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal valor,
                               Long comprobanteId) { }

    public record CostoResponse(Long id, Long proyectoId, Long categoriaCostoId,
                                LocalDate fechaCosto, String concepto, String proveedor,
                                String referencia, BigDecimal valor, Long comprobanteId,
                                EstadoCosto estado, Long registradoPor, Long aprobadoPor,
                                Long version) {
        public static CostoResponse from(CostoProyecto c) {
            return new CostoResponse(c.getId(), c.getProyectoId(), c.getCategoriaCostoId(),
                    c.getFechaCosto(), c.getConcepto(), c.getProveedor(), c.getReferencia(),
                    c.getValor(), c.getComprobanteId(), c.getEstado(), c.getRegistradoPor(),
                    c.getAprobadoPor(), c.getVersion());
        }
    }

    public record RentabilidadResponse(Long proyectoId, LocalDate desde, LocalDate hasta,
                                       BigDecimal ventas, BigDecimal recaudo,
                                       BigDecimal presupuestoAprobado, BigDecimal costosAprobados,
                                       BigDecimal desviacionPresupuesto, BigDecimal utilidad,
                                       BigDecimal margen) { }
}
