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

@Entity
@Table(name = "configuraciones_cartera")
public class ConfiguracionCartera extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, unique = true)
    private Long empresaId;

    @Column(name = "tasa_efectiva_anual", nullable = false, precision = 9, scale = 6)
    private BigDecimal tasaEfectivaAnual;

    @Column(name = "tasa_mora_efectiva_anual", nullable = false, precision = 9, scale = 6)
    private BigDecimal tasaMoraEfectivaAnual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Periodicidad periodicidad;

    @Column(name = "dias_primera_cuota", nullable = false)
    private int diasPrimeraCuota;

    @Column(name = "auto_generar_plan", nullable = false)
    private boolean autoGenerarPlan;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ConfiguracionCartera() {
    }

    public ConfiguracionCartera(Long empresaId) {
        this.empresaId = empresaId;
        this.tasaEfectivaAnual = BigDecimal.ZERO.setScale(6);
        this.tasaMoraEfectivaAnual = BigDecimal.ZERO.setScale(6);
        this.periodicidad = Periodicidad.MENSUAL;
        this.diasPrimeraCuota = 30;
        this.autoGenerarPlan = true;
    }

    public void actualizar(
            BigDecimal tasaEfectivaAnual,
            BigDecimal tasaMoraEfectivaAnual,
            Periodicidad periodicidad,
            int diasPrimeraCuota,
            boolean autoGenerarPlan
    ) {
        validarTasa(tasaEfectivaAnual, "La tasa efectiva anual");
        validarTasa(tasaMoraEfectivaAnual, "La tasa de mora");
        if (periodicidad == null) {
            throw new ReglaNegocioException("La periodicidad es obligatoria");
        }
        if (diasPrimeraCuota < 0 || diasPrimeraCuota > 365) {
            throw new ReglaNegocioException("Los dias para la primera cuota deben estar entre 0 y 365");
        }
        this.tasaEfectivaAnual = tasaEfectivaAnual;
        this.tasaMoraEfectivaAnual = tasaMoraEfectivaAnual;
        this.periodicidad = periodicidad;
        this.diasPrimeraCuota = diasPrimeraCuota;
        this.autoGenerarPlan = autoGenerarPlan;
    }

    private static void validarTasa(BigDecimal tasa, String nombre) {
        if (tasa == null || tasa.signum() < 0 || tasa.compareTo(BigDecimal.ONE) > 0) {
            throw new ReglaNegocioException(nombre + " debe estar entre 0 y 1");
        }
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public BigDecimal getTasaEfectivaAnual() { return tasaEfectivaAnual; }
    public BigDecimal getTasaMoraEfectivaAnual() { return tasaMoraEfectivaAnual; }
    public Periodicidad getPeriodicidad() { return periodicidad; }
    public int getDiasPrimeraCuota() { return diasPrimeraCuota; }
    public boolean isAutoGenerarPlan() { return autoGenerarPlan; }
    public Long getVersion() { return version; }
}
