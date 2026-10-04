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
import java.time.LocalDateTime;

@Entity
@Table(name = "conciliaciones_bancarias")
public class ConciliacionBancaria extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(nullable = false, length = 50)
    private String codigo;
    @Column(name = "cuenta_bancaria", nullable = false, length = 100)
    private String cuentaBancaria;
    @Column(name = "fecha_desde", nullable = false)
    private LocalDate fechaDesde;
    @Column(name = "fecha_hasta", nullable = false)
    private LocalDate fechaHasta;
    @Column(name = "saldo_extracto", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoExtracto;
    @Column(name = "total_conciliado", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalConciliado;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoConciliacion estado;
    @Column(name = "responsable_id", nullable = false)
    private Long responsableId;
    @Column(name = "cerrada_en")
    private LocalDateTime cerradaEn;
    @Version @Column(nullable = false)
    private Long version;

    protected ConciliacionBancaria() {
    }

    public ConciliacionBancaria(Long empresaId, String codigo, String cuentaBancaria, LocalDate desde, LocalDate hasta, BigDecimal saldoExtracto, Long responsableId) {
        if (hasta.isBefore(desde) || saldoExtracto.signum() < 0) {
            throw new ReglaNegocioException("El periodo o saldo del extracto no es válido");
        }
        this.empresaId = empresaId;
        this.codigo = codigo;
        this.cuentaBancaria = cuentaBancaria;
        this.fechaDesde = desde;
        this.fechaHasta = hasta;
        this.saldoExtracto = saldoExtracto;
        this.totalConciliado = BigDecimal.ZERO.setScale(2);
        this.estado = EstadoConciliacion.BORRADOR;
        this.responsableId = responsableId;
    }

    public void iniciar() {
        if (estado != EstadoConciliacion.BORRADOR) {
            throw new ReglaNegocioException("La conciliación no puede iniciarse");
        }
        estado = EstadoConciliacion.EN_PROCESO;
    }

    public void cerrar(BigDecimal total, LocalDateTime fecha) {
        if (estado != EstadoConciliacion.EN_PROCESO && estado != EstadoConciliacion.BORRADOR) {
            throw new ReglaNegocioException("La conciliación no puede cerrarse");
        }
        totalConciliado = total;
        estado = EstadoConciliacion.CERRADA;
        cerradaEn = fecha;
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public String getCodigo() { return codigo; }
    public String getCuentaBancaria() { return cuentaBancaria; }
    public LocalDate getFechaDesde() { return fechaDesde; }
    public LocalDate getFechaHasta() { return fechaHasta; }
    public BigDecimal getSaldoExtracto() { return saldoExtracto; }
    public BigDecimal getTotalConciliado() { return totalConciliado; }
    public EstadoConciliacion getEstado() { return estado; }
    public Long getResponsableId() { return responsableId; }
    public LocalDateTime getCerradaEn() { return cerradaEn; }
}
