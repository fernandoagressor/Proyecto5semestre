package com.inmobiliaria.inmobiliaria_api.entity.simulacion;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "simulacion_cuotas")
public class SimulacionCuota extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "simulacion_id", nullable = false)
    private Long simulacionId;

    @Column(nullable = false)
    private int numero;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal capital;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal interes;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal otros;

    @Column(name = "valor_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "saldo_proyectado", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoProyectado;

    protected SimulacionCuota() {
    }

    public SimulacionCuota(
            Long simulacionId,
            int numero,
            LocalDate fechaVencimiento,
            BigDecimal capital,
            BigDecimal interes,
            BigDecimal otros,
            BigDecimal valorTotal,
            BigDecimal saldoProyectado
    ) {
        this.simulacionId = simulacionId;
        this.numero = numero;
        this.fechaVencimiento = fechaVencimiento;
        this.capital = capital;
        this.interes = interes;
        this.otros = otros;
        this.valorTotal = valorTotal;
        this.saldoProyectado = saldoProyectado;
    }

    public Long getId() {
        return id;
    }

    public Long getSimulacionId() {
        return simulacionId;
    }

    public int getNumero() {
        return numero;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public BigDecimal getCapital() {
        return capital;
    }

    public BigDecimal getInteres() {
        return interes;
    }

    public BigDecimal getOtros() {
        return otros;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public BigDecimal getSaldoProyectado() {
        return saldoProyectado;
    }
}
