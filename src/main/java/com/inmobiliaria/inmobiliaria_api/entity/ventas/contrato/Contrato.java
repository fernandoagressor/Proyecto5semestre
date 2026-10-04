package com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato;

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
@Table(name = "contratos")
public class Contrato extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(name = "oportunidad_id")
    private Long oportunidadId;

    @Column(name = "reserva_id")
    private Long reservaId;

    @Column(name = "cliente_principal_id", nullable = false)
    private Long clientePrincipalId;

    @Column(name = "lote_id", nullable = false)
    private Long loteId;

    @Column(name = "asesor_id")
    private Long asesorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_contrato", nullable = false, length = 40)
    private TipoContrato tipoContrato;

    @Column(name = "valor_lote", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorLote;

    @Column(name = "otros_conceptos", nullable = false, precision = 19, scale = 2)
    private BigDecimal otrosConceptos;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal descuento;

    @Column(name = "valor_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "cuota_inicial", nullable = false, precision = 19, scale = 2)
    private BigDecimal cuotaInicial;

    @Column(name = "saldo_financiado", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoFinanciado;

    @Column(name = "numero_cuotas", nullable = false)
    private int numeroCuotas;

    @Column(name = "fecha_firma")
    private LocalDate fechaFirma;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_finalizacion")
    private LocalDate fechaFinalizacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoContrato estado;

    @Column(name = "motivo_anulacion", length = 400)
    private String motivoAnulacion;

    @Column(name = "anulado_en")
    private LocalDateTime anuladoEn;

    @Column(name = "anulado_por")
    private Long anuladoPor;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Contrato() {
    }

    public Contrato(
            Long empresaId,
            Long proyectoId,
            String codigo,
            Long oportunidadId,
            Long reservaId,
            Long clientePrincipalId,
            Long loteId,
            Long asesorId,
            TipoContrato tipoContrato,
            BigDecimal valorLote,
            BigDecimal otrosConceptos,
            BigDecimal descuento,
            BigDecimal cuotaInicial,
            int numeroCuotas,
            LocalDate fechaInicio,
            LocalDate fechaFinalizacion
    ) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.codigo = codigo;
        this.oportunidadId = oportunidadId;
        this.reservaId = reservaId;
        this.clientePrincipalId = clientePrincipalId;
        this.loteId = loteId;
        this.asesorId = asesorId;
        this.estado = EstadoContrato.BORRADOR;
        actualizarCondiciones(
                tipoContrato,
                valorLote,
                otrosConceptos,
                descuento,
                cuotaInicial,
                numeroCuotas,
                fechaInicio,
                fechaFinalizacion
        );
    }

    public void actualizarCondiciones(
            TipoContrato nuevoTipo,
            BigDecimal nuevoValorLote,
            BigDecimal nuevosConceptos,
            BigDecimal nuevoDescuento,
            BigDecimal nuevaCuotaInicial,
            int nuevoNumeroCuotas,
            LocalDate nuevaFechaInicio,
            LocalDate nuevaFechaFinalizacion
    ) {
        if (estado != EstadoContrato.BORRADOR
                && estado != EstadoContrato.NEGOCIACION) {
            throw new ReglaNegocioException(
                    "Solo un contrato en borrador o negociación puede editarse"
            );
        }
        if (nuevoTipo == null || nuevaFechaInicio == null) {
            throw new ReglaNegocioException(
                    "El tipo de contrato y la fecha de inicio son obligatorios"
            );
        }
        if (nuevoValorLote.signum() < 0
                || nuevosConceptos.signum() < 0
                || nuevoDescuento.signum() < 0
                || nuevaCuotaInicial.signum() < 0) {
            throw new ReglaNegocioException(
                    "Los valores económicos no pueden ser negativos"
            );
        }
        BigDecimal nuevoTotal = nuevoValorLote
                .add(nuevosConceptos)
                .subtract(nuevoDescuento);
        if (nuevoTotal.signum() < 0
                || nuevaCuotaInicial.compareTo(nuevoTotal) > 0) {
            throw new ReglaNegocioException(
                    "El descuento o la cuota inicial superan el valor total"
            );
        }
        if (nuevoNumeroCuotas <= 0) {
            throw new ReglaNegocioException(
                    "El número de cuotas debe ser mayor que cero"
            );
        }
        if (nuevaFechaFinalizacion != null
                && nuevaFechaFinalizacion.isBefore(nuevaFechaInicio)) {
            throw new ReglaNegocioException(
                    "La finalización no puede ser anterior al inicio"
            );
        }
        tipoContrato = nuevoTipo;
        valorLote = nuevoValorLote;
        otrosConceptos = nuevosConceptos;
        descuento = nuevoDescuento;
        valorTotal = nuevoTotal;
        cuotaInicial = nuevaCuotaInicial;
        saldoFinanciado = nuevoTotal.subtract(nuevaCuotaInicial);
        numeroCuotas = nuevoNumeroCuotas;
        fechaInicio = nuevaFechaInicio;
        fechaFinalizacion = nuevaFechaFinalizacion;
    }

    public void iniciarNegociacion() {
        if (estado != EstadoContrato.BORRADOR) {
            throw new ReglaNegocioException(
                    "Solo un borrador puede pasar a negociación"
            );
        }
        estado = EstadoContrato.NEGOCIACION;
    }

    public void enviarAFirma() {
        if (estado != EstadoContrato.BORRADOR
                && estado != EstadoContrato.NEGOCIACION) {
            throw new ReglaNegocioException(
                    "El contrato no puede enviarse a firma en su estado actual"
            );
        }
        estado = EstadoContrato.PENDIENTE_FIRMA;
    }

    public void activar(LocalDate fecha) {
        if (estado != EstadoContrato.PENDIENTE_FIRMA) {
            throw new ReglaNegocioException(
                    "Solo un contrato pendiente de firma puede activarse"
            );
        }
        estado = EstadoContrato.VIGENTE;
        fechaFirma = fecha;
    }

    public EstadoContrato anular(
            String motivo,
            LocalDateTime fecha,
            Long usuarioId
    ) {
        if (estado == EstadoContrato.ANULADO
                || estado == EstadoContrato.FINALIZADO) {
            throw new ReglaNegocioException(
                    "El contrato no puede anularse en su estado actual"
            );
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException(
                    "El motivo de anulación es obligatorio"
            );
        }
        EstadoContrato anterior = estado;
        estado = EstadoContrato.ANULADO;
        motivoAnulacion = motivo.trim();
        anuladoEn = fecha;
        anuladoPor = usuarioId;
        return anterior;
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public String getCodigo() { return codigo; }
    public Long getOportunidadId() { return oportunidadId; }
    public Long getReservaId() { return reservaId; }
    public Long getClientePrincipalId() { return clientePrincipalId; }
    public Long getLoteId() { return loteId; }
    public Long getAsesorId() { return asesorId; }
    public TipoContrato getTipoContrato() { return tipoContrato; }
    public BigDecimal getValorLote() { return valorLote; }
    public BigDecimal getOtrosConceptos() { return otrosConceptos; }
    public BigDecimal getDescuento() { return descuento; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public BigDecimal getCuotaInicial() { return cuotaInicial; }
    public BigDecimal getSaldoFinanciado() { return saldoFinanciado; }
    public int getNumeroCuotas() { return numeroCuotas; }
    public LocalDate getFechaFirma() { return fechaFirma; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFinalizacion() { return fechaFinalizacion; }
    public EstadoContrato getEstado() { return estado; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public LocalDateTime getAnuladoEn() { return anuladoEn; }
    public Long getAnuladoPor() { return anuladoPor; }
    public Long getVersion() { return version; }
}
