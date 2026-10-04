package com.inmobiliaria.inmobiliaria_api.entity.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(name = "facturas")
public class Factura extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;
    @Column(name = "cuota_id")
    private Long cuotaId;
    @Column(nullable = false, length = 50)
    private String numero;
    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;
    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal impuestos;
    @Column(name = "valor_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorTotal;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldo;
    @Column(nullable = false, length = 20)
    private String estado;
    @Column(name = "archivo_id")
    private Long archivoId;
    @Version @Column(nullable = false)
    private Long version;

    protected Factura() {
    }

    public void aplicar(BigDecimal valor, LocalDate fecha) {
        BigDecimal aplicado = dinero(valor);
        if (aplicado.signum() <= 0 || aplicado.compareTo(saldo) > 0) {
            throw new ReglaNegocioException("La aplicación no es válida para la factura");
        }
        saldo = dinero(saldo.subtract(aplicado));
        actualizarEstado(fecha);
    }

    public void revertir(BigDecimal valor, LocalDate fecha) {
        BigDecimal nuevoSaldo = dinero(saldo.add(valor));
        if (nuevoSaldo.compareTo(valorTotal) > 0) {
            throw new ReglaNegocioException("La reversión supera el total de la factura");
        }
        saldo = nuevoSaldo;
        actualizarEstado(fecha);
    }

    private void actualizarEstado(LocalDate fecha) {
        if (saldo.signum() == 0) estado = "PAGADA";
        else if (fecha.isAfter(fechaVencimiento)) estado = "VENCIDA";
        else if (saldo.compareTo(valorTotal) < 0) estado = "PARCIAL";
        else estado = "EMITIDA";
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getContratoId() { return contratoId; }
    public Long getCuotaId() { return cuotaId; }
    public String getNumero() { return numero; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public BigDecimal getSaldo() { return saldo; }
    public String getEstado() { return estado; }
}
