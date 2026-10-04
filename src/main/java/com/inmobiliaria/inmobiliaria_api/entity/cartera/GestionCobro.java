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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "gestiones_cobro")
public class GestionCobro extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;
    @Column(name = "cuota_id")
    private Long cuotaId;
    @Column(name = "gestor_id", nullable = false)
    private Long gestorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanalCobro canal;
    @Column(nullable = false, length = 120)
    private String resultado;
    @Column(columnDefinition = "TEXT")
    private String detalle;
    @Column(name = "promesa_pago_fecha")
    private LocalDate promesaPagoFecha;
    @Column(name = "promesa_pago_valor", precision = 19, scale = 2)
    private BigDecimal promesaPagoValor;
    @Column(name = "fecha_gestion", nullable = false)
    private LocalDateTime fechaGestion;

    protected GestionCobro() {
    }

    public GestionCobro(Long empresaId, Long contratoId, Long cuotaId, Long gestorId, CanalCobro canal, String resultado, String detalle, LocalDate promesaFecha, BigDecimal promesaValor, LocalDateTime fechaGestion) {
        if ((promesaFecha == null) != (promesaValor == null)) {
            throw new ReglaNegocioException("La promesa requiere fecha y valor");
        }
        if (promesaValor != null && promesaValor.signum() <= 0) {
            throw new ReglaNegocioException("El valor prometido debe ser positivo");
        }
        this.empresaId = empresaId;
        this.contratoId = contratoId;
        this.cuotaId = cuotaId;
        this.gestorId = gestorId;
        this.canal = canal;
        this.resultado = resultado;
        this.detalle = detalle;
        this.promesaPagoFecha = promesaFecha;
        this.promesaPagoValor = promesaValor;
        this.fechaGestion = fechaGestion;
    }

    public Long getId() { return id; }
    public Long getContratoId() { return contratoId; }
    public Long getCuotaId() { return cuotaId; }
    public Long getGestorId() { return gestorId; }
    public CanalCobro getCanal() { return canal; }
    public String getResultado() { return resultado; }
    public String getDetalle() { return detalle; }
    public LocalDate getPromesaPagoFecha() { return promesaPagoFecha; }
    public BigDecimal getPromesaPagoValor() { return promesaPagoValor; }
    public LocalDateTime getFechaGestion() { return fechaGestion; }
}
