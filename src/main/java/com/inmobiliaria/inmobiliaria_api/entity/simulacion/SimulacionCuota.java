package com.inmobiliaria.inmobiliaria_api.entity.simulacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/** Cuota proyectada del cronograma; vive embebida en {@link Simulacion#getCuotas()}. */
public class SimulacionCuota {
    private Long simulacionId;
    private int numero;
    private LocalDate fechaVencimiento;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal capital;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal interes;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal otros;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal valorTotal;
    @Field(targetType = FieldType.DECIMAL128)
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
