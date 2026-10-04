package com.inmobiliaria.inmobiliaria_api.entity.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
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

@Entity
@Table(name = "planes_pago")
public class PlanPago extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;

    @Column(name = "version_plan", nullable = false)
    private int versionPlan;

    @Column(name = "capital_financiado", nullable = false, precision = 19, scale = 2)
    private BigDecimal capitalFinanciado;

    @Column(name = "tasa_efectiva_anual", nullable = false, precision = 9, scale = 6)
    private BigDecimal tasaEfectivaAnual;

    @Column(name = "tasa_mora_efectiva_anual", nullable = false, precision = 9, scale = 6)
    private BigDecimal tasaMoraEfectivaAnual;

    @Column(name = "numero_cuotas", nullable = false)
    private int numeroCuotas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Periodicidad periodicidad;

    @Column(name = "fecha_primera_cuota", nullable = false)
    private LocalDate fechaPrimeraCuota;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPlanPago estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private OrigenPlanPago origen;

    @Version
    @Column(nullable = false)
    private Long version;

    protected PlanPago() {
    }

    public PlanPago(
            Long empresaId,
            Long contratoId,
            int versionPlan,
            BigDecimal capitalFinanciado,
            BigDecimal tasaEfectivaAnual,
            BigDecimal tasaMoraEfectivaAnual,
            int numeroCuotas,
            Periodicidad periodicidad,
            LocalDate fechaPrimeraCuota,
            OrigenPlanPago origen
    ) {
        if (capitalFinanciado == null || capitalFinanciado.signum() < 0) {
            throw new ReglaNegocioException("El capital financiado no es válido");
        }
        if (numeroCuotas <= 0 || periodicidad == null || fechaPrimeraCuota == null) {
            throw new ReglaNegocioException("El cronograma del plan no es válido");
        }
        validarTasa(tasaEfectivaAnual, "La tasa efectiva anual");
        validarTasa(tasaMoraEfectivaAnual, "La tasa de mora");
        this.empresaId = empresaId;
        this.contratoId = contratoId;
        this.versionPlan = versionPlan;
        this.capitalFinanciado = capitalFinanciado;
        this.tasaEfectivaAnual = tasaEfectivaAnual;
        this.tasaMoraEfectivaAnual = tasaMoraEfectivaAnual;
        this.numeroCuotas = numeroCuotas;
        this.periodicidad = periodicidad;
        this.fechaPrimeraCuota = fechaPrimeraCuota;
        this.origen = origen;
        this.estado = EstadoPlanPago.BORRADOR;
    }

    public void activar() {
        if (estado != EstadoPlanPago.BORRADOR) {
            throw new ReglaNegocioException("Solo un plan en borrador puede activarse");
        }
        estado = EstadoPlanPago.ACTIVO;
    }

    public void reemplazar() {
        if (estado != EstadoPlanPago.ACTIVO) {
            throw new ReglaNegocioException("Solo un plan activo puede reemplazarse");
        }
        estado = EstadoPlanPago.REEMPLAZADO;
    }

    public void cancelar() {
        if (estado == EstadoPlanPago.REEMPLAZADO) {
            throw new ReglaNegocioException("Un plan reemplazado conserva su historial");
        }
        estado = EstadoPlanPago.CANCELADO;
    }

    private static void validarTasa(BigDecimal tasa, String nombre) {
        if (tasa == null || tasa.signum() < 0 || tasa.compareTo(BigDecimal.ONE) > 0) {
            throw new ReglaNegocioException(nombre + " debe estar entre 0 y 1");
        }
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getContratoId() { return contratoId; }
    public int getVersionPlan() { return versionPlan; }
    public BigDecimal getCapitalFinanciado() { return capitalFinanciado; }
    public BigDecimal getTasaEfectivaAnual() { return tasaEfectivaAnual; }
    public BigDecimal getTasaMoraEfectivaAnual() { return tasaMoraEfectivaAnual; }
    public int getNumeroCuotas() { return numeroCuotas; }
    public Periodicidad getPeriodicidad() { return periodicidad; }
    public LocalDate getFechaPrimeraCuota() { return fechaPrimeraCuota; }
    public EstadoPlanPago getEstado() { return estado; }
    public OrigenPlanPago getOrigen() { return origen; }
    public Long getVersion() { return version; }
}
