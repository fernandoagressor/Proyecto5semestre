package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/** Cita de servicio (visita de entrega, técnica…), guardada en MongoDB (rama 10). La tabla espejo lectura_citas_servicio alimenta el dashboard de servicio. */
@Document(collection = CitaServicio.COLECCION)
public class CitaServicio {
    public static final String COLECCION = "citas_servicio";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private Long proyectoId;
    private Long clienteId;
    private Long solicitudId;
    private Long responsableId;
    private String tipo;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String lugar;
    private EstadoCitaServicio estado;
    private String resultado;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    @Version
    private Long version;

    protected CitaServicio() { }

    public CitaServicio(Long empresaId, Long proyectoId, Long clienteId, Long solicitudId,
                        Long responsableId, String tipo, LocalDateTime inicio,
                        LocalDateTime fin, String lugar) {
        validarFechas(inicio, fin);
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.clienteId = clienteId;
        this.solicitudId = solicitudId;
        this.responsableId = responsableId;
        this.tipo = tipo;
        this.fechaInicio = inicio;
        this.fechaFin = fin;
        this.lugar = lugar;
        this.estado = EstadoCitaServicio.PROGRAMADA;
        this.creadoEn = LocalDateTime.now(BOGOTA);
        this.actualizadoEn = creadoEn;
    }

    public void reprogramar(LocalDateTime inicio, LocalDateTime fin, String lugar) {
        if (estado == EstadoCitaServicio.REALIZADA || estado == EstadoCitaServicio.CANCELADA) {
            throw new ReglaNegocioException("La cita finalizada no puede reprogramarse");
        }
        validarFechas(inicio, fin);
        fechaInicio = inicio;
        fechaFin = fin;
        this.lugar = lugar;
        estado = EstadoCitaServicio.REPROGRAMADA;
    }

    public void confirmar() {
        if (estado != EstadoCitaServicio.PROGRAMADA && estado != EstadoCitaServicio.REPROGRAMADA) {
            throw new ReglaNegocioException("La cita no puede confirmarse en su estado actual");
        }
        estado = EstadoCitaServicio.CONFIRMADA;
    }

    public void completar(String resultado) {
        if (estado != EstadoCitaServicio.CONFIRMADA && estado != EstadoCitaServicio.PROGRAMADA) {
            throw new ReglaNegocioException("La cita no puede marcarse como realizada");
        }
        if (resultado == null || resultado.isBlank()) {
            throw new ReglaNegocioException("El resultado de la cita es obligatorio");
        }
        this.resultado = resultado.trim();
        estado = EstadoCitaServicio.REALIZADA;
    }

    public void cancelar(String motivo) {
        if (estado == EstadoCitaServicio.REALIZADA || estado == EstadoCitaServicio.CANCELADA) {
            throw new ReglaNegocioException("La cita no puede cancelarse en su estado actual");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("El motivo de cancelación es obligatorio");
        }
        resultado = motivo.trim();
        estado = EstadoCitaServicio.CANCELADA;
    }

    private static void validarFechas(LocalDateTime inicio, LocalDateTime fin) {
        if (inicio == null || fin == null || !fin.isAfter(inicio)) {
            throw new ReglaNegocioException("La fecha final debe ser posterior a la inicial");
        }
    }

    /** Asigna el identificador de un registro nuevo. Solo puede hacerse una vez. */
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

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public Long getClienteId() { return clienteId; }
    public Long getSolicitudId() { return solicitudId; }
    public Long getResponsableId() { return responsableId; }
    public String getTipo() { return tipo; }
    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public LocalDateTime getFechaFin() { return fechaFin; }
    public String getLugar() { return lugar; }
    public EstadoCitaServicio getEstado() { return estado; }
    public String getResultado() { return resultado; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
}
