package com.inmobiliaria.inmobiliaria_api.entity.simulacion;

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
import java.time.LocalDateTime;

@Entity
@Table(name = "simulaciones")
public class Simulacion extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(name = "oportunidad_id", nullable = false)
    private Long oportunidadId;

    @Column(name = "lote_id", nullable = false)
    private Long loteId;

    @Column(name = "creado_por", nullable = false)
    private Long creadoPor;

    @Column(name = "valor_lote", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorLote;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal descuento;

    @Column(name = "cuota_inicial", nullable = false, precision = 19, scale = 2)
    private BigDecimal cuotaInicial;

    @Column(name = "saldo_financiar", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoFinanciar;

    @Column(name = "numero_cuotas", nullable = false)
    private int numeroCuotas;

    @Column(name = "tasa_efectiva_anual", nullable = false, precision = 9, scale = 6)
    private BigDecimal tasaEfectivaAnual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Periodicidad periodicidad;

    @Column(name = "vigente_hasta", nullable = false)
    private LocalDateTime vigenteHasta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSimulacion estado;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Simulacion() {
    }

    public Simulacion(
            Long empresaId,
            Long proyectoId,
            String codigo,
            Long oportunidadId,
            Long loteId,
            Long creadoPor,
            BigDecimal valorLote,
            BigDecimal descuento,
            BigDecimal cuotaInicial,
            BigDecimal saldoFinanciar,
            int numeroCuotas,
            BigDecimal tasaEfectivaAnual,
            Periodicidad periodicidad,
            LocalDateTime vigenteHasta
    ) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.codigo = codigo;
        this.oportunidadId = oportunidadId;
        this.loteId = loteId;
        this.creadoPor = creadoPor;
        this.valorLote = valorLote;
        this.descuento = descuento;
        this.cuotaInicial = cuotaInicial;
        this.saldoFinanciar = saldoFinanciar;
        this.numeroCuotas = numeroCuotas;
        this.tasaEfectivaAnual = tasaEfectivaAnual;
        this.periodicidad = periodicidad;
        this.vigenteHasta = vigenteHasta;
        this.estado = EstadoSimulacion.BORRADOR;
    }

    public void enviar(LocalDateTime ahora) {
        if (estado != EstadoSimulacion.BORRADOR) {
            throw new ReglaNegocioException(
                    "Solo una simulación en borrador puede enviarse"
            );
        }
        if (!vigenteHasta.isAfter(ahora)) {
            throw new ReglaNegocioException(
                    "No se puede enviar una simulación vencida"
            );
        }
        estado = EstadoSimulacion.ENVIADA;
    }

    public void cancelar() {
        if (estado == EstadoSimulacion.ACEPTADA
                || estado == EstadoSimulacion.CANCELADA) {
            throw new ReglaNegocioException(
                    "La simulación no admite cancelación en su estado actual"
            );
        }
        estado = EstadoSimulacion.CANCELADA;
    }

    public void aceptar(LocalDateTime ahora) {
        if (estado != EstadoSimulacion.ENVIADA) {
            throw new ReglaNegocioException(
                    "Solo una simulación enviada puede aceptarse"
            );
        }
        if (!vigenteHasta.isAfter(ahora)) {
            throw new ReglaNegocioException(
                    "La simulación está vencida"
            );
        }
        estado = EstadoSimulacion.ACEPTADA;
    }

    public Long getId() {
        return id;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public String getCodigo() {
        return codigo;
    }

    public Long getOportunidadId() {
        return oportunidadId;
    }

    public Long getLoteId() {
        return loteId;
    }

    public Long getCreadoPor() {
        return creadoPor;
    }

    public BigDecimal getValorLote() {
        return valorLote;
    }

    public BigDecimal getDescuento() {
        return descuento;
    }

    public BigDecimal getCuotaInicial() {
        return cuotaInicial;
    }

    public BigDecimal getSaldoFinanciar() {
        return saldoFinanciar;
    }

    public int getNumeroCuotas() {
        return numeroCuotas;
    }

    public BigDecimal getTasaEfectivaAnual() {
        return tasaEfectivaAnual;
    }

    public Periodicidad getPeriodicidad() {
        return periodicidad;
    }

    public LocalDateTime getVigenteHasta() {
        return vigenteHasta;
    }

    public EstadoSimulacion getEstado() {
        return estado;
    }

    public Long getVersion() {
        return version;
    }
}
