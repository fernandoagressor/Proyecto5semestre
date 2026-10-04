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

@Entity
@Table(name = "conciliacion_movimientos")
public class ConciliacionMovimiento extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "conciliacion_id", nullable = false)
    private Long conciliacionId;
    @Column(name = "fecha_movimiento", nullable = false)
    private LocalDate fechaMovimiento;
    @Column(nullable = false, length = 180)
    private String referencia;
    @Column(length = 500)
    private String descripcion;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;
    @Column(name = "pago_id")
    private Long pagoId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMovimientoConciliacion estado;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal diferencia;
    @Version @Column(nullable = false)
    private Long version;

    protected ConciliacionMovimiento() {
    }

    public ConciliacionMovimiento(Long conciliacionId, LocalDate fecha, String referencia, String descripcion, BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw new ReglaNegocioException("El movimiento bancario debe ser positivo");
        }
        this.conciliacionId = conciliacionId;
        this.fechaMovimiento = fecha;
        this.referencia = referencia;
        this.descripcion = descripcion;
        this.valor = valor;
        this.estado = EstadoMovimientoConciliacion.PENDIENTE;
        this.diferencia = BigDecimal.ZERO.setScale(2);
    }

    public void conciliar(Pago pago) {
        if (estado != EstadoMovimientoConciliacion.PENDIENTE) {
            throw new ReglaNegocioException("El movimiento ya fue gestionado");
        }
        pagoId = pago.getId();
        diferencia = valor.subtract(pago.getValor()).abs();
        estado = diferencia.signum() == 0
                ? EstadoMovimientoConciliacion.CONCILIADO
                : EstadoMovimientoConciliacion.DIFERENCIA;
    }

    public void ignorar() {
        if (estado != EstadoMovimientoConciliacion.PENDIENTE) {
            throw new ReglaNegocioException("El movimiento ya fue gestionado");
        }
        estado = EstadoMovimientoConciliacion.IGNORADO;
    }

    public Long getId() { return id; }
    public Long getConciliacionId() { return conciliacionId; }
    public LocalDate getFechaMovimiento() { return fechaMovimiento; }
    public String getReferencia() { return referencia; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getValor() { return valor; }
    public Long getPagoId() { return pagoId; }
    public EstadoMovimientoConciliacion getEstado() { return estado; }
    public BigDecimal getDiferencia() { return diferencia; }
}
