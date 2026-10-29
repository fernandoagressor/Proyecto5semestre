package com.inmobiliaria.inmobiliaria_api.entity.crm;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Cita comercial de una oportunidad, guardada en MongoDB (rama 8). Guarda copia de la empresa y
 * el proyecto de la oportunidad para poder filtrar la agenda por alcance sin cruzar colecciones.
 */
@Document(collection = CitaComercial.COLECCION)
public class CitaComercial {

    public static final String COLECCION = "citas_comerciales";

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private Long proyectoId;
    private Long oportunidadId;
    private Long asesorId;
    private String tipo;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String lugar;
    private EstadoCitaComercial estado;
    private String resultado;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    @Version
    private Long version;

    protected CitaComercial() {
    }

    public CitaComercial(
            Long empresaId,
            Long proyectoId,
            Long oportunidadId,
            Long asesorId,
            String tipo,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            String lugar
    ) {
        this(oportunidadId, asesorId, tipo, fechaInicio, fechaFin, lugar);
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
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
        this.creadoEn = LocalDateTime.now(BOGOTA);
        this.actualizadoEn = creadoEn;
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

    /** Asigna el identificador de una cita nueva. Solo puede hacerse una vez. */
    public void identificar(long nuevoId) {
        if (id != null) {
            throw new IllegalStateException("La cita ya tiene identificador " + id);
        }
        id = nuevoId;
    }

    /** Marca la fecha de la última modificación; lo llama el repositorio antes de guardar. */
    public void tocar() {
        actualizadoEn = LocalDateTime.now(BOGOTA);
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
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
