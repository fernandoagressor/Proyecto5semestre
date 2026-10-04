package com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva;

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
@Table(name = "reservas")
public class Reserva extends EntidadAuditable {

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

    @Column(name = "simulacion_id")
    private Long simulacionId;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "lote_id", nullable = false)
    private Long loteId;

    @Column(name = "asesor_id")
    private Long asesorId;

    @Column(name = "fecha_reserva", nullable = false)
    private LocalDateTime fechaReserva;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDateTime fechaVencimiento;

    @Column(name = "valor_reserva", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorReserva;

    @Column(name = "forma_pago", length = 40)
    private String formaPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", nullable = false, length = 20)
    private EstadoPagoReserva estadoPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado;

    @Column(name = "motivo_cancelacion", length = 400)
    private String motivoCancelacion;

    @Column(name = "comprobante_archivo_id")
    private Long comprobanteArchivoId;

    @Column(name = "pago_confirmado_en")
    private LocalDateTime pagoConfirmadoEn;

    @Column(name = "pago_confirmado_por")
    private Long pagoConfirmadoPor;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Reserva() {
    }

    public Reserva(
            Long empresaId,
            Long proyectoId,
            String codigo,
            Long oportunidadId,
            Long simulacionId,
            Long clienteId,
            Long loteId,
            Long asesorId,
            LocalDateTime fechaReserva,
            LocalDateTime fechaVencimiento,
            BigDecimal valorReserva,
            String formaPago
    ) {
        if (fechaVencimiento == null
                || !fechaVencimiento.isAfter(fechaReserva)) {
            throw new ReglaNegocioException(
                    "El vencimiento debe ser posterior a la reserva"
            );
        }
        if (valorReserva == null || valorReserva.signum() <= 0) {
            throw new ReglaNegocioException(
                    "El valor de reserva debe ser mayor que cero"
            );
        }
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.codigo = codigo;
        this.oportunidadId = oportunidadId;
        this.simulacionId = simulacionId;
        this.clienteId = clienteId;
        this.loteId = loteId;
        this.asesorId = asesorId;
        this.fechaReserva = fechaReserva;
        this.fechaVencimiento = fechaVencimiento;
        this.valorReserva = valorReserva;
        this.formaPago = formaPago;
        this.estadoPago = EstadoPagoReserva.PENDIENTE;
        this.estado = EstadoReserva.PENDIENTE_PAGO;
    }

    public EstadoReserva confirmarPago(
            LocalDateTime ahora,
            Long usuarioId,
            Long archivoId
    ) {
        exigirNoVencida(ahora);
        if (estado != EstadoReserva.PENDIENTE_PAGO
                || estadoPago != EstadoPagoReserva.PENDIENTE) {
            throw new ReglaNegocioException(
                    "La reserva no tiene un pago pendiente de confirmación"
            );
        }
        EstadoReserva anterior = estado;
        estadoPago = EstadoPagoReserva.CONFIRMADO;
        estado = EstadoReserva.ACTIVA;
        comprobanteArchivoId = archivoId;
        pagoConfirmadoEn = ahora;
        pagoConfirmadoPor = usuarioId;
        return anterior;
    }

    public EstadoReserva rechazarPago(String motivo) {
        if (estado != EstadoReserva.PENDIENTE_PAGO
                || estadoPago != EstadoPagoReserva.PENDIENTE) {
            throw new ReglaNegocioException(
                    "La reserva no tiene un pago pendiente"
            );
        }
        EstadoReserva anterior = estado;
        estadoPago = EstadoPagoReserva.RECHAZADO;
        estado = EstadoReserva.CANCELADA;
        motivoCancelacion = requerido(motivo);
        return anterior;
    }

    public EstadoReserva reversarPagoConfirmado() {
        if (estado != EstadoReserva.ACTIVA
                || estadoPago != EstadoPagoReserva.CONFIRMADO) {
            throw new ReglaNegocioException(
                    "Solo puede reversarse el pago de una reserva activa"
            );
        }
        EstadoReserva anterior = estado;
        estado = EstadoReserva.PENDIENTE_PAGO;
        estadoPago = EstadoPagoReserva.PENDIENTE;
        comprobanteArchivoId = null;
        pagoConfirmadoEn = null;
        pagoConfirmadoPor = null;
        return anterior;
    }

    public EstadoReserva cancelar(String motivo) {
        if (estado != EstadoReserva.PENDIENTE_PAGO
                && estado != EstadoReserva.ACTIVA) {
            throw new ReglaNegocioException(
                    "La reserva no puede cancelarse en su estado actual"
            );
        }
        EstadoReserva anterior = estado;
        estado = EstadoReserva.CANCELADA;
        motivoCancelacion = requerido(motivo);
        return anterior;
    }

    public EstadoReserva vencer(LocalDateTime ahora) {
        if (estado != EstadoReserva.PENDIENTE_PAGO
                && estado != EstadoReserva.ACTIVA) {
            return null;
        }
        if (fechaVencimiento.isAfter(ahora)) {
            return null;
        }
        EstadoReserva anterior = estado;
        estado = EstadoReserva.VENCIDA;
        return anterior;
    }

    public void extender(LocalDateTime nuevaFecha, LocalDateTime ahora) {
        exigirNoVencida(ahora);
        if (estado != EstadoReserva.PENDIENTE_PAGO
                && estado != EstadoReserva.ACTIVA) {
            throw new ReglaNegocioException(
                    "La reserva no admite una nueva fecha de vencimiento"
            );
        }
        if (nuevaFecha == null || !nuevaFecha.isAfter(ahora)) {
            throw new ReglaNegocioException(
                    "El nuevo vencimiento debe estar en el futuro"
            );
        }
        fechaVencimiento = nuevaFecha;
    }

    public EstadoReserva convertir(LocalDateTime ahora) {
        exigirNoVencida(ahora);
        if (estado != EstadoReserva.ACTIVA
                || estadoPago != EstadoPagoReserva.CONFIRMADO) {
            throw new ReglaNegocioException(
                    "Solo una reserva activa y pagada puede convertirse"
            );
        }
        EstadoReserva anterior = estado;
        estado = EstadoReserva.CONVERTIDA;
        return anterior;
    }

    private void exigirNoVencida(LocalDateTime ahora) {
        if (!fechaVencimiento.isAfter(ahora)) {
            throw new ReglaNegocioException("La reserva está vencida");
        }
    }

    private static String requerido(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El motivo es obligatorio");
        }
        return valor.trim();
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public String getCodigo() { return codigo; }
    public Long getOportunidadId() { return oportunidadId; }
    public Long getSimulacionId() { return simulacionId; }
    public Long getClienteId() { return clienteId; }
    public Long getLoteId() { return loteId; }
    public Long getAsesorId() { return asesorId; }
    public LocalDateTime getFechaReserva() { return fechaReserva; }
    public LocalDateTime getFechaVencimiento() { return fechaVencimiento; }
    public BigDecimal getValorReserva() { return valorReserva; }
    public String getFormaPago() { return formaPago; }
    public EstadoPagoReserva getEstadoPago() { return estadoPago; }
    public EstadoReserva getEstado() { return estado; }
    public String getMotivoCancelacion() { return motivoCancelacion; }
    public Long getComprobanteArchivoId() { return comprobanteArchivoId; }
    public LocalDateTime getPagoConfirmadoEn() { return pagoConfirmadoEn; }
    public Long getPagoConfirmadoPor() { return pagoConfirmadoPor; }
    public Long getVersion() { return version; }
}
