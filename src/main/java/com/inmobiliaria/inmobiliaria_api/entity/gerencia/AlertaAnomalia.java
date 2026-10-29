package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/** Alerta de acceso o acción inusual, guardada en MongoDB (rama 7). */
@Document(collection = AlertaAnomalia.COLECCION)
public class AlertaAnomalia {

    public static final String COLECCION = "alertas_anomalia";
    public static final String ABIERTA = "ABIERTA";
    public static final String EN_INVESTIGACION = "EN_INVESTIGACION";
    public static final String RESUELTA = "RESUELTA";
    public static final String DESCARTADA = "DESCARTADA";

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private String tipo;
    private String severidad;
    private Long usuarioId;
    private Long eventoAuditoriaId;
    private String descripcion;
    private String estado;
    private Long asignadoA;
    private LocalDateTime resueltaEn;
    private String resolucion;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    @Version
    private Long version;

    protected AlertaAnomalia() { }

    public AlertaAnomalia(Long id, Long empresaId, String tipo, String severidad, Long usuarioId,
                          Long eventoAuditoriaId, String descripcion) {
        this.id = id;
        this.empresaId = empresaId;
        this.tipo = tipo;
        this.severidad = severidad;
        this.usuarioId = usuarioId;
        this.eventoAuditoriaId = eventoAuditoriaId;
        this.descripcion = descripcion;
        this.estado = ABIERTA;
        this.creadoEn = LocalDateTime.now(BOGOTA);
        this.actualizadoEn = creadoEn;
    }

    public boolean cerrada() {
        return RESUELTA.equals(estado) || DESCARTADA.equals(estado);
    }

    /** Asigna un investigador y pasa la alerta a investigación. @return el asignado anterior */
    public Long asignar(Long usuario) {
        exigirAbierta();
        Long anterior = asignadoA;
        asignadoA = usuario;
        estado = EN_INVESTIGACION;
        actualizadoEn = LocalDateTime.now(BOGOTA);
        return anterior;
    }

    /** Cierra la alerta como resuelta o descartada. @return el estado anterior */
    public String cerrar(String textoResolucion, boolean descartar, Long usuario, LocalDateTime fecha) {
        exigirAbierta();
        String anterior = estado;
        estado = descartar ? DESCARTADA : RESUELTA;
        resueltaEn = fecha;
        resolucion = textoResolucion;
        if (asignadoA == null) {
            asignadoA = usuario;
        }
        actualizadoEn = LocalDateTime.now(BOGOTA);
        return anterior;
    }

    private void exigirAbierta() {
        if (cerrada()) {
            throw new ReglaNegocioException("La alerta ya fue cerrada");
        }
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public String getTipo() { return tipo; }
    public String getSeveridad() { return severidad; }
    public Long getUsuarioId() { return usuarioId; }
    public Long getEventoAuditoriaId() { return eventoAuditoriaId; }
    public String getDescripcion() { return descripcion; }
    public String getEstado() { return estado; }
    public Long getAsignadoA() { return asignadoA; }
    public LocalDateTime getResueltaEn() { return resueltaEn; }
    public String getResolucion() { return resolucion; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
}
