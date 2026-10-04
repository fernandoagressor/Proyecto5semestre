package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

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
@Table(name = "costos_proyecto")
public class CostoProyecto extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;
    @Column(name = "categoria_costo_id", nullable = false)
    private Long categoriaCostoId;
    @Column(name = "fecha_costo", nullable = false)
    private LocalDate fechaCosto;
    @Column(nullable = false, length = 220)
    private String concepto;
    @Column(length = 180)
    private String proveedor;
    @Column(length = 100)
    private String referencia;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;
    @Column(name = "comprobante_id")
    private Long comprobanteId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCosto estado;
    @Column(name = "registrado_por")
    private Long registradoPor;
    @Column(name = "aprobado_por")
    private Long aprobadoPor;
    @Version
    @Column(nullable = false)
    private Long version;

    protected CostoProyecto() { }

    public CostoProyecto(Long empresaId, Long proyectoId, Long categoriaCostoId,
                         LocalDate fechaCosto, String concepto, String proveedor,
                         String referencia, BigDecimal valor, Long comprobanteId,
                         Long registradoPor) {
        if (fechaCosto == null || concepto == null || concepto.isBlank()
                || valor == null || valor.signum() <= 0) {
            throw new ReglaNegocioException("Fecha, concepto y valor positivo son obligatorios");
        }
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.categoriaCostoId = categoriaCostoId;
        this.fechaCosto = fechaCosto;
        this.concepto = concepto.trim();
        this.proveedor = normalizar(proveedor);
        this.referencia = normalizar(referencia);
        this.valor = valor.setScale(2, RoundingMode.HALF_UP);
        this.comprobanteId = comprobanteId;
        this.registradoPor = registradoPor;
        this.estado = EstadoCosto.REGISTRADO;
    }

    public void aprobar(Long usuarioId) {
        if (estado != EstadoCosto.REGISTRADO) {
            throw new ReglaNegocioException("Solo un costo registrado puede aprobarse");
        }
        if (usuarioId == null || usuarioId.equals(registradoPor)) {
            throw new ReglaNegocioException("Quien registra el costo no puede aprobarlo");
        }
        aprobadoPor = usuarioId;
        estado = EstadoCosto.APROBADO;
    }

    public void pagar() {
        if (estado != EstadoCosto.APROBADO) {
            throw new ReglaNegocioException("Solo un costo aprobado puede marcarse pagado");
        }
        estado = EstadoCosto.PAGADO;
    }

    public void anular() {
        if (estado == EstadoCosto.PAGADO || estado == EstadoCosto.ANULADO) {
            throw new ReglaNegocioException("Un costo pagado o anulado no puede anularse");
        }
        estado = EstadoCosto.ANULADO;
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public Long getCategoriaCostoId() { return categoriaCostoId; }
    public LocalDate getFechaCosto() { return fechaCosto; }
    public String getConcepto() { return concepto; }
    public String getProveedor() { return proveedor; }
    public String getReferencia() { return referencia; }
    public BigDecimal getValor() { return valor; }
    public Long getComprobanteId() { return comprobanteId; }
    public EstadoCosto getEstado() { return estado; }
    public Long getRegistradoPor() { return registradoPor; }
    public Long getAprobadoPor() { return aprobadoPor; }
    public Long getVersion() { return version; }
}
