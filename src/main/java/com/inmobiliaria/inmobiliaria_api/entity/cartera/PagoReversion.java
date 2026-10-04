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
import java.time.LocalDateTime;

@Entity
@Table(name = "pago_reversiones")
public class PagoReversion extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "pago_id", nullable = false)
    private Long pagoId;
    @Column(name = "valor_revertido", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorRevertido;
    @Column(nullable = false, length = 500)
    private String motivo;
    @Column(name = "evidencia_id")
    private Long evidenciaId;
    @Column(name = "solicitado_por", nullable = false)
    private Long solicitadoPor;
    @Column(name = "aprobado_por")
    private Long aprobadoPor;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReversion estado;
    @Column(name = "ejecutada_en")
    private LocalDateTime ejecutadaEn;
    @Version @Column(nullable = false)
    private Long version;

    protected PagoReversion() {
    }

    public PagoReversion(Long pagoId, BigDecimal valorRevertido, String motivo, Long evidenciaId, Long solicitadoPor) {
        this.pagoId = pagoId;
        this.valorRevertido = valorRevertido;
        this.motivo = motivo;
        this.evidenciaId = evidenciaId;
        this.solicitadoPor = solicitadoPor;
        this.estado = EstadoReversion.SOLICITADA;
    }

    public void aprobarYEjecutar(Long usuarioId, LocalDateTime fecha) {
        if (estado != EstadoReversion.SOLICITADA) {
            throw new ReglaNegocioException("La reversión ya fue decidida");
        }
        if (solicitadoPor.equals(usuarioId)) {
            throw new ReglaNegocioException("La reversión requiere aprobación de otro usuario");
        }
        aprobadoPor = usuarioId;
        estado = EstadoReversion.EJECUTADA;
        ejecutadaEn = fecha;
    }

    public void rechazar(Long usuarioId) {
        if (estado != EstadoReversion.SOLICITADA) {
            throw new ReglaNegocioException("La reversión ya fue decidida");
        }
        aprobadoPor = usuarioId;
        estado = EstadoReversion.RECHAZADA;
    }

    public Long getId() { return id; }
    public Long getPagoId() { return pagoId; }
    public BigDecimal getValorRevertido() { return valorRevertido; }
    public String getMotivo() { return motivo; }
    public Long getEvidenciaId() { return evidenciaId; }
    public Long getSolicitadoPor() { return solicitadoPor; }
    public Long getAprobadoPor() { return aprobadoPor; }
    public EstadoReversion getEstado() { return estado; }
    public LocalDateTime getEjecutadaEn() { return ejecutadaEn; }
}
