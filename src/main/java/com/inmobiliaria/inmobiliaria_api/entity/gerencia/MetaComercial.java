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
import java.time.YearMonth;

@Entity
@Table(name = "metas_comerciales")
public class MetaComercial extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "proyecto_id")
    private Long proyectoId;
    @Column(name = "asesor_id")
    private Long asesorId;
    @Column(nullable = false, length = 7)
    private String periodo;
    @Column(name = "valor_meta", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorMeta;
    @Column(name = "cantidad_meta", nullable = false)
    private int cantidadMeta;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMeta estado;
    @Column(name = "aprobado_por")
    private Long aprobadoPor;
    @Version
    @Column(nullable = false)
    private Long version;

    protected MetaComercial() { }

    public MetaComercial(Long empresaId, Long proyectoId, Long asesorId,
                         String periodo, BigDecimal valorMeta, int cantidadMeta) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.asesorId = asesorId;
        this.periodo = validarPeriodo(periodo);
        actualizarValores(valorMeta, cantidadMeta);
        this.estado = EstadoMeta.BORRADOR;
    }

    public void actualizar(BigDecimal valorMeta, int cantidadMeta) {
        if (estado != EstadoMeta.BORRADOR) {
            throw new ReglaNegocioException("Solo una meta en borrador puede editarse");
        }
        actualizarValores(valorMeta, cantidadMeta);
    }

    public void aprobar(Long usuarioId) {
        if (estado != EstadoMeta.BORRADOR) {
            throw new ReglaNegocioException("Solo una meta en borrador puede aprobarse");
        }
        if (usuarioId == null || usuarioId.equals(asesorId)) {
            throw new ReglaNegocioException("El asesor no puede aprobar su propia meta");
        }
        aprobadoPor = usuarioId;
        estado = EstadoMeta.APROBADA;
    }

    public void cerrar(YearMonth periodoActual) {
        if (estado != EstadoMeta.APROBADA) {
            throw new ReglaNegocioException("Solo una meta aprobada puede cerrarse");
        }
        if (!YearMonth.parse(periodo).isBefore(periodoActual)) {
            throw new ReglaNegocioException("Solo puede cerrarse un periodo ya finalizado");
        }
        estado = EstadoMeta.CERRADA;
    }

    private void actualizarValores(BigDecimal valor, int cantidad) {
        if (valor == null || valor.signum() < 0 || cantidad < 0) {
            throw new ReglaNegocioException("Los valores de la meta no pueden ser negativos");
        }
        if (valor.signum() == 0 && cantidad == 0) {
            throw new ReglaNegocioException("La meta debe definir un valor o una cantidad");
        }
        valorMeta = valor.setScale(2, RoundingMode.HALF_UP);
        cantidadMeta = cantidad;
    }

    private static String validarPeriodo(String periodo) {
        try {
            return YearMonth.parse(periodo).toString();
        } catch (RuntimeException ex) {
            throw new ReglaNegocioException("El periodo debe tener formato AAAA-MM");
        }
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public Long getAsesorId() { return asesorId; }
    public String getPeriodo() { return periodo; }
    public BigDecimal getValorMeta() { return valorMeta; }
    public int getCantidadMeta() { return cantidadMeta; }
    public EstadoMeta getEstado() { return estado; }
    public Long getAprobadoPor() { return aprobadoPor; }
    public Long getVersion() { return version; }
}
