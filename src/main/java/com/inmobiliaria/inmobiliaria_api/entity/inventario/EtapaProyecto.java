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
import java.time.LocalDate;

@Entity
@Table(name = "etapas_proyecto")
public class EtapaProyecto extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false)
    private Integer secuencia;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEtapa estado;

    protected EtapaProyecto() {
    }

    public EtapaProyecto(
            Long empresaId,
            Long proyectoId,
            String codigo,
            String nombre,
            Integer secuencia,
            LocalDate fechaInicio,
            LocalDate fechaFin
    ) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.estado = EstadoEtapa.ACTIVA;
        actualizar(codigo, nombre, secuencia, fechaInicio, fechaFin, estado);
    }

    public void actualizar(
            String codigo,
            String nombre,
            Integer secuencia,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            EstadoEtapa estado
    ) {
        if (fechaInicio != null && fechaFin != null && fechaFin.isBefore(fechaInicio)) {
            throw new ReglaNegocioException(
                    "La fecha final de la etapa no puede ser anterior a la fecha inicial"
            );
        }
        this.codigo = codigo;
        this.nombre = nombre;
        this.secuencia = secuencia;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.estado = estado;
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

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public Integer getSecuencia() {
        return secuencia;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public EstadoEtapa getEstado() {
        return estado;
    }
}
