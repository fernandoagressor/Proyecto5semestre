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
@Table(name = "estados_cuenta_generados")
public class EstadoCuentaGenerado extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;
    @Column(name = "fecha_corte", nullable = false)
    private LocalDate fechaCorte;
    @Column(name = "saldo_capital", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoCapital;
    @Column(name = "saldo_mora", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoMora;
    @Column(name = "saldo_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoTotal;
    @Column(name = "archivo_id", nullable = false)
    private Long archivoId;
    @Column(name = "generado_por")
    private Long generadoPor;

    protected EstadoCuentaGenerado() {
    }

    public EstadoCuentaGenerado(Long empresaId, Long contratoId, LocalDate fechaCorte, BigDecimal saldoCapital, BigDecimal saldoMora, Long archivoId, Long generadoPor) {
        this.empresaId = empresaId;
        this.contratoId = contratoId;
        this.fechaCorte = fechaCorte;
        this.saldoCapital = saldoCapital;
        this.saldoMora = saldoMora;
        this.saldoTotal = saldoCapital.add(saldoMora);
        this.archivoId = archivoId;
        this.generadoPor = generadoPor;
    }

    public Long getId() { return id; }
    public Long getContratoId() { return contratoId; }
    public LocalDate getFechaCorte() { return fechaCorte; }
    public BigDecimal getSaldoCapital() { return saldoCapital; }
    public BigDecimal getSaldoMora() { return saldoMora; }
    public BigDecimal getSaldoTotal() { return saldoTotal; }
    public Long getArchivoId() { return archivoId; }
}
