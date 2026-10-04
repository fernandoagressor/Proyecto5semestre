package com.inmobiliaria.inmobiliaria_api.entity.cartera;

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
@Table(name = "refinanciacion_cuotas")
public class RefinanciacionCuota extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "refinanciacion_id", nullable = false)
    private Long refinanciacionId;
    @Column(nullable = false)
    private int numero;
    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal capital;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal interes;
    @Column(name = "valor_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorTotal;

    protected RefinanciacionCuota() {
    }

    public RefinanciacionCuota(Long refinanciacionId, int numero, LocalDate fecha, BigDecimal capital, BigDecimal interes) {
        this.refinanciacionId = refinanciacionId;
        this.numero = numero;
        this.fechaVencimiento = fecha;
        this.capital = capital;
        this.interes = interes;
        this.valorTotal = capital.add(interes);
    }

    public Long getId() { return id; }
    public Long getRefinanciacionId() { return refinanciacionId; }
    public int getNumero() { return numero; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public BigDecimal getCapital() { return capital; }
    public BigDecimal getInteres() { return interes; }
    public BigDecimal getValorTotal() { return valorTotal; }
}
