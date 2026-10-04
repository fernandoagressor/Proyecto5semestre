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
@Table(name = "paz_salvos")
public class PazSalvo extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(nullable = false, length = 50)
    private String codigo;
    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;
    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;
    @Column(name = "saldo_verificado", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoVerificado;
    @Column(name = "archivo_id", nullable = false)
    private Long archivoId;
    @Column(name = "generado_por")
    private Long generadoPor;
    @Column(nullable = false, length = 20)
    private String estado;
    @Column(name = "motivo_anulacion", length = 400)
    private String motivoAnulacion;

    protected PazSalvo() {
    }

    public PazSalvo(Long empresaId, String codigo, Long contratoId, LocalDate fecha, Long archivoId, Long generadoPor) {
        this.empresaId = empresaId;
        this.codigo = codigo;
        this.contratoId = contratoId;
        this.fechaEmision = fecha;
        this.saldoVerificado = BigDecimal.ZERO.setScale(2);
        this.archivoId = archivoId;
        this.generadoPor = generadoPor;
        this.estado = "VIGENTE";
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public Long getContratoId() { return contratoId; }
    public LocalDate getFechaEmision() { return fechaEmision; }
    public Long getArchivoId() { return archivoId; }
    public String getEstado() { return estado; }
}
