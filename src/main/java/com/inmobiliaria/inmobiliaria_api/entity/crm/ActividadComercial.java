package com.inmobiliaria.inmobiliaria_api.entity.crm;

import java.time.LocalDateTime;

/** Actividad de seguimiento comercial; vive embebida en {@link Oportunidad#getActividades()}. */
public class ActividadComercial {
    private Long id;
    private Long oportunidadId;
    private Long asesorId;
    private TipoActividadComercial tipo;
    private String resultado;
    private String descripcion;
    private LocalDateTime fechaActividad;
    private String proximaAccion;
    private LocalDateTime fechaProximaAccion;
    private LocalDateTime creadoEn;

    protected ActividadComercial() {
    }

    public ActividadComercial(Long id, Long oportunidadId, Long asesorId, TipoActividadComercial tipo,
                              String resultado, String descripcion, LocalDateTime fechaActividad,
                              String proximaAccion, LocalDateTime fechaProximaAccion, LocalDateTime creadoEn) {
        this.id = id;
        this.oportunidadId = oportunidadId;
        this.asesorId = asesorId;
        this.tipo = tipo;
        this.resultado = resultado;
        this.descripcion = descripcion;
        this.fechaActividad = fechaActividad;
        this.proximaAccion = proximaAccion;
        this.fechaProximaAccion = fechaProximaAccion;
        this.creadoEn = creadoEn;
    }

    public Long getId() { return id; }
    public Long getOportunidadId() { return oportunidadId; }
    public Long getAsesorId() { return asesorId; }
    public TipoActividadComercial getTipo() { return tipo; }
    public String getResultado() { return resultado; }
    public String getDescripcion() { return descripcion; }
    public LocalDateTime getFechaActividad() { return fechaActividad; }
    public String getProximaAccion() { return proximaAccion; }
    public LocalDateTime getFechaProximaAccion() { return fechaProximaAccion; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
