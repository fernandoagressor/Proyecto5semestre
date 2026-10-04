package com.inmobiliaria.inmobiliaria_api.entity.cartera;

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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "refinanciaciones")
public class Refinanciacion extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(nullable = false, length = 50)
    private String codigo;
    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;
    @Column(name = "saldo_refinanciado", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoRefinanciado;
    @Column(name = "numero_cuotas_nuevo", nullable = false)
    private int numeroCuotasNuevo;
    @Column(name = "tasa_nueva", nullable = false, precision = 9, scale = 6)
    private BigDecimal tasaNueva;
    @Column(name = "fecha_primera_cuota", nullable = false)
    private LocalDate fechaPrimeraCuota;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String motivo;
    @Column(name = "solicitado_por", nullable = false)
    private Long solicitadoPor;
    @Column(name = "aprobado_por")
    private Long aprobadoPor;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoRefinanciacion estado;
    @Column(name = "fecha_decision")
    private LocalDateTime fechaDecision;
    @Column(name = "plan_pago_generado_id")
    private Long planPagoGeneradoId;
    @Version @Column(nullable = false)
    private Long version;

    protected Refinanciacion() {
    }

    public Refinanciacion(Long empresaId, String codigo, Long contratoId, BigDecimal saldo, int cuotas, BigDecimal tasa, LocalDate primeraFecha, String motivo, Long solicitadoPor) {
        if (saldo == null || saldo.signum() <= 0 || cuotas <= 0) {
            throw new ReglaNegocioException("Las condiciones de refinanciación no son válidas");
        }
        if (tasa == null || tasa.signum() < 0 || tasa.compareTo(BigDecimal.ONE) > 0) {
            throw new ReglaNegocioException("La tasa de refinanciación debe estar entre 0 y 1");
        }
        this.empresaId = empresaId;
        this.codigo = codigo;
        this.contratoId = contratoId;
        this.saldoRefinanciado = saldo;
        this.numeroCuotasNuevo = cuotas;
        this.tasaNueva = tasa;
        this.fechaPrimeraCuota = primeraFecha;
        this.motivo = motivo;
        this.solicitadoPor = solicitadoPor;
        this.estado = EstadoRefinanciacion.SOLICITADA;
    }

    public void aprobar(Long usuarioId, LocalDateTime fecha) {
        if (estado != EstadoRefinanciacion.SOLICITADA && estado != EstadoRefinanciacion.EN_ESTUDIO) {
            throw new ReglaNegocioException("La refinanciación ya fue decidida");
        }
        if (solicitadoPor.equals(usuarioId)) {
            throw new ReglaNegocioException("La refinanciación requiere aprobación independiente");
        }
        aprobadoPor = usuarioId;
        fechaDecision = fecha;
        estado = EstadoRefinanciacion.APROBADA;
    }

    public void rechazar(Long usuarioId, LocalDateTime fecha) {
        if (estado != EstadoRefinanciacion.SOLICITADA && estado != EstadoRefinanciacion.EN_ESTUDIO) {
            throw new ReglaNegocioException("La refinanciación ya fue decidida");
        }
        aprobadoPor = usuarioId;
        fechaDecision = fecha;
        estado = EstadoRefinanciacion.RECHAZADA;
    }

    public void aplicar(Long planPagoId) {
        if (estado != EstadoRefinanciacion.APROBADA) {
            throw new ReglaNegocioException("Solo una refinanciación aprobada puede aplicarse");
        }
        planPagoGeneradoId = planPagoId;
        estado = EstadoRefinanciacion.APLICADA;
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public String getCodigo() { return codigo; }
    public Long getContratoId() { return contratoId; }
    public BigDecimal getSaldoRefinanciado() { return saldoRefinanciado; }
    public int getNumeroCuotasNuevo() { return numeroCuotasNuevo; }
    public BigDecimal getTasaNueva() { return tasaNueva; }
    public LocalDate getFechaPrimeraCuota() { return fechaPrimeraCuota; }
    public String getMotivo() { return motivo; }
    public Long getSolicitadoPor() { return solicitadoPor; }
    public Long getAprobadoPor() { return aprobadoPor; }
    public EstadoRefinanciacion getEstado() { return estado; }
    public LocalDateTime getFechaDecision() { return fechaDecision; }
    public Long getPlanPagoGeneradoId() { return planPagoGeneradoId; }
}
