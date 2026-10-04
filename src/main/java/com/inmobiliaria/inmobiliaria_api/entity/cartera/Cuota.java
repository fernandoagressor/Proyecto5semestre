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
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(name = "cuotas")
public class Cuota extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "plan_pago_id", nullable = false)
    private Long planPagoId;
    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;
    @Column(nullable = false)
    private int numero;
    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal capital;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal interes;
    @Column(name = "otros_conceptos", nullable = false, precision = 19, scale = 2)
    private BigDecimal otrosConceptos;
    @Column(name = "valor_programado", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorProgramado;
    @Column(name = "valor_pagado", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorPagado;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldo;
    @Column(name = "dias_mora", nullable = false)
    private int diasMora;
    @Column(name = "interes_mora", nullable = false, precision = 19, scale = 2)
    private BigDecimal interesMora;
    @Column(name = "mora_calculada_hasta")
    private LocalDate moraCalculadaHasta;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuota estado;
    @Version
    @Column(nullable = false)
    private Long version;

    protected Cuota() {
    }

    public Cuota(
            Long empresaId,
            Long planPagoId,
            Long contratoId,
            int numero,
            LocalDate fechaVencimiento,
            BigDecimal capital,
            BigDecimal interes,
            BigDecimal otrosConceptos
    ) {
        this.empresaId = empresaId;
        this.planPagoId = planPagoId;
        this.contratoId = contratoId;
        this.numero = numero;
        this.fechaVencimiento = fechaVencimiento;
        this.capital = dinero(capital);
        this.interes = dinero(interes);
        this.otrosConceptos = dinero(otrosConceptos);
        this.valorProgramado = dinero(capital.add(interes).add(otrosConceptos));
        this.valorPagado = dinero(BigDecimal.ZERO);
        this.saldo = valorProgramado;
        this.diasMora = 0;
        this.interesMora = dinero(BigDecimal.ZERO);
        this.estado = EstadoCuota.PENDIENTE;
    }

    public void aplicar(BigDecimal valor, ConceptoPago concepto, LocalDate fecha) {
        BigDecimal aplicado = dinero(valor);
        if (aplicado.signum() <= 0) {
            throw new ReglaNegocioException("El valor aplicado debe ser positivo");
        }
        if (estado == EstadoCuota.CANCELADA || estado == EstadoCuota.CONDONADA) {
            throw new ReglaNegocioException("La cuota no admite pagos en su estado actual");
        }
        if (concepto == ConceptoPago.MORA) {
            if (aplicado.compareTo(interesMora) > 0) {
                throw new ReglaNegocioException("El pago de mora supera el interés causado");
            }
            interesMora = dinero(interesMora.subtract(aplicado));
        } else {
            if (aplicado.compareTo(saldo) > 0) {
                throw new ReglaNegocioException("La aplicación supera el saldo de la cuota");
            }
            saldo = dinero(saldo.subtract(aplicado));
            valorPagado = dinero(valorPagado.add(aplicado));
        }
        recalcularEstado(fecha);
    }

    public void revertir(BigDecimal valor, ConceptoPago concepto, LocalDate fecha) {
        BigDecimal revertido = dinero(valor);
        if (concepto == ConceptoPago.MORA) {
            interesMora = dinero(interesMora.add(revertido));
        } else {
            if (revertido.compareTo(valorPagado) > 0) {
                throw new ReglaNegocioException("La reversión supera lo pagado en la cuota");
            }
            saldo = dinero(saldo.add(revertido));
            valorPagado = dinero(valorPagado.subtract(revertido));
        }
        recalcularEstado(fecha);
    }

    public void actualizarMora(LocalDate fechaCorte, BigDecimal nuevaMora) {
        if (estado == EstadoCuota.CANCELADA || estado == EstadoCuota.CONDONADA) {
            return;
        }
        diasMora = fechaCorte.isAfter(fechaVencimiento)
                ? (int) java.time.temporal.ChronoUnit.DAYS.between(fechaVencimiento, fechaCorte)
                : 0;
        interesMora = dinero(nuevaMora.max(BigDecimal.ZERO));
        moraCalculadaHasta = fechaCorte;
        recalcularEstado(fechaCorte);
    }

    public void cancelarPendiente() {
        if (saldo.signum() > 0) {
            estado = EstadoCuota.CANCELADA;
            diasMora = 0;
            interesMora = dinero(BigDecimal.ZERO);
        }
    }

    private void recalcularEstado(LocalDate fecha) {
        if (saldo.signum() == 0 && interesMora.signum() == 0) {
            estado = EstadoCuota.PAGADA;
            diasMora = 0;
        } else if (fecha.isAfter(fechaVencimiento)) {
            estado = EstadoCuota.VENCIDA;
        } else if (valorPagado.signum() > 0) {
            estado = EstadoCuota.PARCIAL;
        } else {
            estado = EstadoCuota.PENDIENTE;
        }
    }

    private static BigDecimal dinero(BigDecimal valor) {
        if (valor == null) {
            throw new ReglaNegocioException("Los valores de la cuota son obligatorios");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getPlanPagoId() { return planPagoId; }
    public Long getContratoId() { return contratoId; }
    public int getNumero() { return numero; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public BigDecimal getCapital() { return capital; }
    public BigDecimal getInteres() { return interes; }
    public BigDecimal getOtrosConceptos() { return otrosConceptos; }
    public BigDecimal getValorProgramado() { return valorProgramado; }
    public BigDecimal getValorPagado() { return valorPagado; }
    public BigDecimal getSaldo() { return saldo; }
    public int getDiasMora() { return diasMora; }
    public BigDecimal getInteresMora() { return interesMora; }
    public LocalDate getMoraCalculadaHasta() { return moraCalculadaHasta; }
    public EstadoCuota getEstado() { return estado; }
    public Long getVersion() { return version; }
}
