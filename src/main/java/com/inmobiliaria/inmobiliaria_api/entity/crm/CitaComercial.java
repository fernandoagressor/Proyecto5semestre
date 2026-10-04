package com.inmobiliaria.inmobiliaria_api.entity.crm;

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
import java.time.LocalDateTime;

@Entity
@Table(name = "citas_comerciales")
public class CitaComercial extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oportunidad_id", nullable = false)
    private Long oportunidadId;

    @Column(name = "asesor_id", nullable = false)
    private Long asesorId;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDateTime fechaFin;

    @Column(length = 300)
    private String lugar;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCitaComercial estado;

    @Column(columnDefinition = "TEXT")
    private String resultado;

    @Version
    @Column(nullable = false)
    private Long version;

    protected CitaComercial() {
    }

    public CitaComercial(
            Long oportunidadId,
            Long asesorId,
            String tipo,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            String lugar
    ) {
        this.oportunidadId = oportunidadId;
        this.asesorId = asesorId;
        this.tipo = tipo;
        this.estado = EstadoCitaComercial.PROGRAMADA;
        reprogramar(fechaInicio, fechaFin, lugar);
        this.estado = EstadoCitaComercial.PROGRAMADA;
    }

    public void reprogramar(
            LocalDateTime nuevaFechaInicio,
            LocalDateTime nuevaFechaFin,
            String nuevoLugar
    ) {
        if (estado == EstadoCitaComercial.REALIZADA
                || estado == EstadoCitaComercial.CANCELADA
                || estado == EstadoCitaComercial.NO_ASISTIO) {
            throw new ReglaNegocioException(
                    "La cita finalizada no puede reprogramarse"
            );
        }
        if (nuevaFechaInicio == null
                || nuevaFechaFin == null
                || !nuevaFechaFin.isAfter(nuevaFechaInicio)) {
            throw new ReglaNegocioException(
                    "La fecha final debe ser posterior a la fecha inicial"
            );
        }
        fechaInicio = nuevaFechaInicio;
        fechaFin = nuevaFechaFin;
        lugar = nuevoLugar;
        if (estado != null) {
            estado = EstadoCitaComercial.REPROGRAMADA;
        }
    }

    public void cambiarEstado(
            EstadoCitaComercial nuevoEstado,
            String resultado
    ) {
        if (estado == EstadoCitaComercial.CANCELADA
                || estado == EstadoCitaComercial.REALIZADA
                || estado == EstadoCitaComercial.NO_ASISTIO) {
            throw new ReglaNegocioException(
                    "Una cita finalizada no admite cambios de estado"
            );
        }
        if (nuevoEstado == EstadoCitaComercial.REPROGRAMADA) {
            throw new ReglaNegocioException(
                    "Use la operación de reprogramación para cambiar fechas"
            );
        }
        if ((nuevoEstado == EstadoCitaComercial.REALIZADA
                || nuevoEstado == EstadoCitaComercial.NO_ASISTIO)
                && (resultado == null || resultado.isBlank())) {
            throw new ReglaNegocioException(
                    "Debe registrar el resultado de la cita"
            );
        }
        estado = nuevoEstado;
        this.resultado = resultado;
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

    public String getTipo() {
        return tipo;
    }

    public LocalDateTime getFechaInicio() {
        return fechaInicio;
    }

    public LocalDateTime getFechaFin() {
        return fechaFin;
    }

    public String getLugar() {
        return lugar;
    }

    public EstadoCitaComercial getEstado() {
        return estado;
    }

    public String getResultado() {
        return resultado;
    }

    public Long getVersion() {
        return version;
    }
}
