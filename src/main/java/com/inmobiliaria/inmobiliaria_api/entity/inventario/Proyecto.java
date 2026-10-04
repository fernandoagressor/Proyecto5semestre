package com.inmobiliaria.inmobiliaria_api.entity.inventario;

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

@Entity
@Table(name = "proyectos")
public class Proyecto extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(nullable = false, length = 40)
    private String codigo;

    @Column(nullable = false, length = 180)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, length = 100)
    private String ciudad;

    @Column(nullable = false, length = 100)
    private String departamento;

    @Column(length = 240)
    private String direccion;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin_estimada")
    private LocalDate fechaFinEstimada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoProyecto estado;

    @Column(name = "porcentaje_avance", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeAvance;

    @Column(name = "max_lotes")
    private Integer maxLotes;

    @Column(name = "imagen_principal_url", length = 500)
    private String imagenPrincipalUrl;

    @Column(name = "gerente_id")
    private Long gerenteId;

    protected Proyecto() {
    }

    public Proyecto(
            Long empresaId,
            String codigo,
            String nombre,
            String descripcion,
            String ciudad,
            String departamento,
            String direccion,
            LocalDate fechaInicio,
            LocalDate fechaFinEstimada,
            Integer maxLotes,
            String imagenPrincipalUrl,
            Long gerenteId
    ) {
        this.empresaId = empresaId;
        this.estado = EstadoProyecto.PLANEACION;
        this.porcentajeAvance = BigDecimal.ZERO;
        actualizar(
                codigo,
                nombre,
                descripcion,
                ciudad,
                departamento,
                direccion,
                fechaInicio,
                fechaFinEstimada,
                maxLotes,
                imagenPrincipalUrl,
                gerenteId
        );
    }

    public void actualizar(
            String codigo,
            String nombre,
            String descripcion,
            String ciudad,
            String departamento,
            String direccion,
            LocalDate fechaInicio,
            LocalDate fechaFinEstimada,
            Integer maxLotes,
            String imagenPrincipalUrl,
            Long gerenteId
    ) {
        validarFechas(fechaInicio, fechaFinEstimada);
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.ciudad = ciudad;
        this.departamento = departamento;
        this.direccion = direccion;
        this.fechaInicio = fechaInicio;
        this.fechaFinEstimada = fechaFinEstimada;
        this.maxLotes = maxLotes;
        this.imagenPrincipalUrl = imagenPrincipalUrl;
        this.gerenteId = gerenteId;
    }

    public void cambiarEstado(EstadoProyecto estado) {
        if (estado == null) {
            throw new ReglaNegocioException("El estado del proyecto es obligatorio");
        }
        this.estado = estado;
    }

    private void validarFechas(LocalDate inicio, LocalDate fin) {
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            throw new ReglaNegocioException(
                    "La fecha final estimada no puede ser anterior a la fecha inicial"
            );
        }
    }

    public Long getId() {
        return id;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getCiudad() {
        return ciudad;
    }

    public String getDepartamento() {
        return departamento;
    }

    public String getDireccion() {
        return direccion;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFinEstimada() {
        return fechaFinEstimada;
    }

    public EstadoProyecto getEstado() {
        return estado;
    }

    public BigDecimal getPorcentajeAvance() {
        return porcentajeAvance;
    }

    public Integer getMaxLotes() {
        return maxLotes;
    }

    public String getImagenPrincipalUrl() {
        return imagenPrincipalUrl;
    }

    public Long getGerenteId() {
        return gerenteId;
    }
}
