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
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos")
public class Pago extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(nullable = false, length = 50)
    private String codigo;
    @Column(name = "contrato_id")
    private Long contratoId;
    @Column(name = "reserva_id")
    private Long reservaId;
    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;
    @Column(name = "metodo_pago_id", nullable = false)
    private Long metodoPagoId;
    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;
    @Column(length = 180)
    private String referencia;
    @Column(name = "comprobante_id")
    private Long comprobanteId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenPago origen;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPago estado;
    @Column(name = "registrado_por")
    private Long registradoPor;
    @Column(name = "validado_por")
    private Long validadoPor;
    @Column(name = "validado_en")
    private LocalDateTime validadoEn;
    @Column(columnDefinition = "TEXT")
    private String observaciones;
    @Version @Column(nullable = false)
    private Long version;

    protected Pago() {
    }

    public Pago(
            Long empresaId,
            String codigo,
            Long contratoId,
            Long reservaId,
            Long clienteId,
            Long metodoPagoId,
            LocalDateTime fechaPago,
            BigDecimal valor,
            String referencia,
            Long comprobanteId,
            OrigenPago origen,
            Long registradoPor,
            String observaciones
    ) {
        if (contratoId == null && reservaId == null) {
            throw new ReglaNegocioException("El pago requiere contrato o reserva");
        }
        if (valor == null || valor.signum() <= 0) {
            throw new ReglaNegocioException("El valor del pago debe ser positivo");
        }
        this.empresaId = empresaId;
        this.codigo = codigo;
        this.contratoId = contratoId;
        this.reservaId = reservaId;
        this.clienteId = clienteId;
        this.metodoPagoId = metodoPagoId;
        this.fechaPago = fechaPago;
        this.valor = valor;
        this.referencia = referencia;
        this.comprobanteId = comprobanteId;
        this.origen = origen;
        this.registradoPor = registradoPor;
        this.observaciones = observaciones;
        this.estado = EstadoPago.PENDIENTE;
    }

    public EstadoPago enviarRevision() {
        if (estado != EstadoPago.PENDIENTE) {
            throw new ReglaNegocioException("Solo un pago pendiente puede enviarse a revisión");
        }
        EstadoPago anterior = estado;
        estado = EstadoPago.EN_REVISION;
        return anterior;
    }

    public EstadoPago confirmar(Long usuarioId, LocalDateTime fecha) {
        if (estado != EstadoPago.PENDIENTE && estado != EstadoPago.EN_REVISION) {
            throw new ReglaNegocioException("El pago no puede confirmarse en su estado actual");
        }
        EstadoPago anterior = estado;
        estado = EstadoPago.CONFIRMADO;
        validadoPor = usuarioId;
        validadoEn = fecha;
        return anterior;
    }

    public EstadoPago rechazar(Long usuarioId, LocalDateTime fecha, String motivo) {
        if (estado != EstadoPago.PENDIENTE && estado != EstadoPago.EN_REVISION) {
            throw new ReglaNegocioException("El pago no puede rechazarse en su estado actual");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("El motivo de rechazo es obligatorio");
        }
        EstadoPago anterior = estado;
        estado = EstadoPago.RECHAZADO;
        validadoPor = usuarioId;
        validadoEn = fecha;
        observaciones = motivo.trim();
        return anterior;
    }

    public EstadoPago reversar() {
        if (estado != EstadoPago.CONFIRMADO) {
            throw new ReglaNegocioException("Solo un pago confirmado puede reversarse");
        }
        EstadoPago anterior = estado;
        estado = EstadoPago.REVERSADO;
        return anterior;
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public String getCodigo() { return codigo; }
    public Long getContratoId() { return contratoId; }
    public Long getReservaId() { return reservaId; }
    public Long getClienteId() { return clienteId; }
    public Long getMetodoPagoId() { return metodoPagoId; }
    public LocalDateTime getFechaPago() { return fechaPago; }
    public BigDecimal getValor() { return valor; }
    public String getReferencia() { return referencia; }
    public Long getComprobanteId() { return comprobanteId; }
    public OrigenPago getOrigen() { return origen; }
    public EstadoPago getEstado() { return estado; }
    public Long getRegistradoPor() { return registradoPor; }
    public Long getValidadoPor() { return validadoPor; }
    public LocalDateTime getValidadoEn() { return validadoEn; }
    public String getObservaciones() { return observaciones; }
    public Long getVersion() { return version; }
}
