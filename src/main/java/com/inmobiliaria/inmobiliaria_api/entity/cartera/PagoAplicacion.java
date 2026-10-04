package com.inmobiliaria.inmobiliaria_api.entity.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "pago_aplicaciones")
public class PagoAplicacion extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "pago_id", nullable = false)
    private Long pagoId;
    @Column(name = "cuota_id")
    private Long cuotaId;
    @Column(name = "factura_id")
    private Long facturaId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConceptoPago concepto;
    @Column(name = "valor_aplicado", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorAplicado;

    protected PagoAplicacion() {
    }

    public PagoAplicacion(Long pagoId, Long cuotaId, Long facturaId, ConceptoPago concepto, BigDecimal valorAplicado) {
        this.pagoId = pagoId;
        this.cuotaId = cuotaId;
        this.facturaId = facturaId;
        this.concepto = concepto;
        this.valorAplicado = valorAplicado;
    }

    public Long getId() { return id; }
    public Long getPagoId() { return pagoId; }
    public Long getCuotaId() { return cuotaId; }
    public Long getFacturaId() { return facturaId; }
    public ConceptoPago getConcepto() { return concepto; }
    public BigDecimal getValorAplicado() { return valorAplicado; }
}
