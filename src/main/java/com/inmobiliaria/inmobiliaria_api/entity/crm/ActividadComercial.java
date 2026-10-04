package com.inmobiliaria.inmobiliaria_api.entity.crm;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "actividades_comerciales")
public class ActividadComercial extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oportunidad_id", nullable = false)
    private Long oportunidadId;

    @Column(name = "asesor_id", nullable = false)
    private Long asesorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private TipoActividadComercial tipo;

    @Column(length = 120)
    private String resultado;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha_actividad", nullable = false)
    private LocalDateTime fechaActividad;

    @Column(name = "proxima_accion", length = 200)
    private String proximaAccion;

    @Column(name = "fecha_proxima_accion")
    private LocalDateTime fechaProximaAccion;

    protected ActividadComercial() {
    }

    public ActividadComercial(
            Long oportunidadId,
            Long asesorId,
            TipoActividadComercial tipo,
            String resultado,
            String descripcion,
            LocalDateTime fechaActividad,
            String proximaAccion,
            LocalDateTime fechaProximaAccion
    ) {
        this.oportunidadId = oportunidadId;
        this.asesorId = asesorId;
        this.tipo = tipo;
        this.resultado = resultado;
        this.descripcion = descripcion;
        this.fechaActividad = fechaActividad;
        this.proximaAccion = proximaAccion;
        this.fechaProximaAccion = fechaProximaAccion;
    }

    public Long getId() {
        return id;
    }

    public Long getOportunidadId() {
        return oportunidadId;
    }

    public Long getAsesorId() {
        return asesorId;
    }

    public TipoActividadComercial getTipo() {
        return tipo;
    }

    public String getResultado() {
        return resultado;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public LocalDateTime getFechaActividad() {
        return fechaActividad;
    }

    public String getProximaAccion() {
        return proximaAccion;
    }

    public LocalDateTime getFechaProximaAccion() {
        return fechaProximaAccion;
    }
}
