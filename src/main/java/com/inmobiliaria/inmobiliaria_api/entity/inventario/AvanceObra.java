package com.inmobiliaria.inmobiliaria_api.entity.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "avances_obra")
public class AvanceObra extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;

    @Column(nullable = false, length = 1000)
    private String descripcion;

    @Column(name = "registrado_por", nullable = false)
    private Long registradoPor;

    protected AvanceObra() {
    }

    public AvanceObra(
            Proyecto proyecto,
            LocalDate fecha,
            BigDecimal porcentaje,
            String descripcion,
            Long registradoPor
    ) {
        if (descripcion == null || descripcion.isBlank()) {
            throw new ReglaNegocioException("La descripción del avance es obligatoria");
        }
        proyecto.registrarAvance(porcentaje);
        this.empresaId = proyecto.getEmpresaId();
        this.proyectoId = proyecto.getId();
        this.fecha = fecha;
        this.porcentaje = porcentaje;
        this.descripcion = descripcion.trim();
        this.registradoPor = registradoPor;
    }

    public Long getId() {
        return id;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public BigDecimal getPorcentaje() {
        return porcentaje;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Long getRegistradoPor() {
        return registradoPor;
    }
}
