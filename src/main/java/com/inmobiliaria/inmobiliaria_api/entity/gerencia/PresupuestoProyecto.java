package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(name = "presupuestos_proyecto")
public class PresupuestoProyecto extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;
    @Column(name = "categoria_costo_id", nullable = false)
    private Long categoriaCostoId;
    @Column(name = "periodo_desde", nullable = false)
    private LocalDate periodoDesde;
    @Column(name = "periodo_hasta", nullable = false)
    private LocalDate periodoHasta;
    @Column(name = "valor_presupuestado", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorPresupuestado;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPresupuesto estado;
    @Column(name = "aprobado_por")
    private Long aprobadoPor;
    @Column(name = "creado_por")
    private Long creadoPor;
    @Version
    @Column(nullable = false)
    private Long version;

    protected PresupuestoProyecto() { }

    public PresupuestoProyecto(Long empresaId, Long proyectoId, Long categoriaCostoId,
                               LocalDate periodoDesde, LocalDate periodoHasta,
                               BigDecimal valorPresupuestado, Long creadoPor) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.categoriaCostoId = categoriaCostoId;
        this.periodoDesde = periodoDesde;
        this.periodoHasta = periodoHasta;
        this.valorPresupuestado = dinero(valorPresupuestado);
        this.creadoPor = creadoPor;
        this.estado = EstadoPresupuesto.BORRADOR;
        validarPeriodo();
    }

    public void actualizar(LocalDate desde, LocalDate hasta, BigDecimal valor) {
        if (estado != EstadoPresupuesto.BORRADOR) {
            throw new ReglaNegocioException("Solo un presupuesto en borrador puede editarse");
        }
        periodoDesde = desde;
        periodoHasta = hasta;
        valorPresupuestado = dinero(valor);
        validarPeriodo();
    }

    public void aprobar(Long usuarioId) {
        if (estado != EstadoPresupuesto.BORRADOR) {
            throw new ReglaNegocioException("Solo un presupuesto en borrador puede aprobarse");
        }
        if (usuarioId == null || usuarioId.equals(creadoPor)) {
            throw new ReglaNegocioException("Quien crea el presupuesto no puede aprobarlo");
        }
        aprobadoPor = usuarioId;
        estado = EstadoPresupuesto.APROBADO;
    }

    public void cerrar() {
        if (estado != EstadoPresupuesto.APROBADO) {
            throw new ReglaNegocioException("Solo un presupuesto aprobado puede cerrarse");
        }
        estado = EstadoPresupuesto.CERRADO;
    }

    public void anular() {
        if (estado == EstadoPresupuesto.CERRADO || estado == EstadoPresupuesto.ANULADO) {
            throw new ReglaNegocioException("El presupuesto cerrado o anulado no puede anularse");
        }
        estado = EstadoPresupuesto.ANULADO;
    }

    private void validarPeriodo() {
        if (periodoDesde == null || periodoHasta == null || periodoHasta.isBefore(periodoDesde)) {
            throw new ReglaNegocioException("El periodo del presupuesto no es válido");
        }
    }

    private static BigDecimal dinero(BigDecimal valor) {
        if (valor == null || valor.signum() < 0) {
            throw new ReglaNegocioException("El presupuesto no puede ser negativo");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public Long getCategoriaCostoId() { return categoriaCostoId; }
    public LocalDate getPeriodoDesde() { return periodoDesde; }
    public LocalDate getPeriodoHasta() { return periodoHasta; }
    public BigDecimal getValorPresupuestado() { return valorPresupuestado; }
    public EstadoPresupuesto getEstado() { return estado; }
    public Long getAprobadoPor() { return aprobadoPor; }
    public Long getCreadoPor() { return creadoPor; }
    public Long getVersion() { return version; }
}
