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
@Table(name = "comisiones")
public class Comision extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;
    @Column(name = "asesor_id", nullable = false)
    private Long asesorId;
    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;
    @Column(name = "regla_comision_id", nullable = false)
    private Long reglaComisionId;
    @Column(name = "base_calculo", nullable = false, precision = 19, scale = 2)
    private BigDecimal baseCalculo;
    @Column(precision = 9, scale = 6)
    private BigDecimal porcentaje;
    @Column(name = "valor_comision", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorComision;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoComision estado;
    @Column(name = "fecha_causacion")
    private LocalDate fechaCausacion;
    @Column(name = "fecha_pago")
    private LocalDate fechaPago;
    @Column(name = "aprobado_por")
    private Long aprobadoPor;
    @Version
    @Column(nullable = false)
    private Long version;

    protected Comision() { }

    public Comision(Long empresaId, Long proyectoId, Long asesorId,
                    Long contratoId, ReglaComision regla, BigDecimal baseCalculo) {
        if (regla == null) throw new ReglaNegocioException("La regla de comisión es obligatoria");
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.asesorId = asesorId;
        this.contratoId = contratoId;
        this.reglaComisionId = regla.getId();
        this.baseCalculo = dinero(baseCalculo);
        this.porcentaje = regla.getTipo() == TipoReglaComision.PORCENTAJE
                ? regla.getValor() : null;
        this.valorComision = regla.calcular(baseCalculo);
        this.estado = EstadoComision.PROYECTADA;
    }

    public void causar(LocalDate fecha) {
        exigirEstado(EstadoComision.PROYECTADA);
        if (fecha == null) throw new ReglaNegocioException("La fecha de causación es obligatoria");
        fechaCausacion = fecha;
        estado = EstadoComision.CAUSADA;
    }

    public void aprobar(Long usuarioId) {
        exigirEstado(EstadoComision.CAUSADA);
        if (usuarioId == null || usuarioId.equals(asesorId)) {
            throw new ReglaNegocioException("El asesor beneficiario no puede aprobar su propia comisión");
        }
        aprobadoPor = usuarioId;
        estado = EstadoComision.APROBADA;
    }

    public void pagar(LocalDate fecha) {
        exigirEstado(EstadoComision.APROBADA);
        if (fecha == null || fechaCausacion == null || fecha.isBefore(fechaCausacion)) {
            throw new ReglaNegocioException("La fecha de pago no puede ser anterior a la causación");
        }
        fechaPago = fecha;
        estado = EstadoComision.PAGADA;
    }

    public void anular() {
        if (estado == EstadoComision.PAGADA || estado == EstadoComision.ANULADA) {
            throw new ReglaNegocioException("Una comisión pagada o anulada no puede anularse");
        }
        estado = EstadoComision.ANULADA;
    }

    private void exigirEstado(EstadoComision esperado) {
        if (estado != esperado) {
            throw new ReglaNegocioException("La comisión debe estar en estado " + esperado);
        }
    }

    private static BigDecimal dinero(BigDecimal valor) {
        if (valor == null || valor.signum() < 0) {
            throw new ReglaNegocioException("La base de comisión no puede ser negativa");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public Long getAsesorId() { return asesorId; }
    public Long getContratoId() { return contratoId; }
    public Long getReglaComisionId() { return reglaComisionId; }
    public BigDecimal getBaseCalculo() { return baseCalculo; }
    public BigDecimal getPorcentaje() { return porcentaje; }
    public BigDecimal getValorComision() { return valorComision; }
    public EstadoComision getEstado() { return estado; }
    public LocalDate getFechaCausacion() { return fechaCausacion; }
    public LocalDate getFechaPago() { return fechaPago; }
    public Long getAprobadoPor() { return aprobadoPor; }
    public Long getVersion() { return version; }
}
