package com.inmobiliaria.inmobiliaria_api.entity.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lote_precios_historial")
public class LotePrecioHistorial extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lote_id", nullable = false)
    private Long loteId;

    @Column(name = "valor_anterior", precision = 19, scale = 2)
    private BigDecimal valorAnterior;

    @Column(name = "valor_nuevo", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorNuevo;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDateTime vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDateTime vigenteHasta;

    @Column(nullable = false, length = 300)
    private String motivo;

    @Column(name = "cambiado_por")
    private Long cambiadoPor;

    protected LotePrecioHistorial() {
    }

    public LotePrecioHistorial(
            Long loteId,
            BigDecimal valorAnterior,
            BigDecimal valorNuevo,
            LocalDateTime vigenteDesde,
            String motivo,
            Long cambiadoPor
    ) {
        this.loteId = loteId;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
        this.vigenteDesde = vigenteDesde;
        this.motivo = motivo;
        this.cambiadoPor = cambiadoPor;
    }

    public void cerrar(LocalDateTime fecha) {
        vigenteHasta = fecha;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getValorAnterior() {
        return valorAnterior;
    }

    public BigDecimal getValorNuevo() {
        return valorNuevo;
    }

    public LocalDateTime getVigenteDesde() {
        return vigenteDesde;
    }

    public LocalDateTime getVigenteHasta() {
        return vigenteHasta;
    }

    public String getMotivo() {
        return motivo;
    }

    public Long getCambiadoPor() {
        return cambiadoPor;
    }
}
