package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Evento de la bitácora de auditoría, guardado en MongoDB (rama 7).
 *
 * <p>Es inmutable: no tiene métodos que lo modifiquen y su repositorio solo permite insertar
 * y consultar (en MySQL lo garantizaban dos triggers). Los datos anteriores y nuevos se guardan
 * como el texto JSON exacto que se registró, para que la evidencia no cambie de forma.</p>
 */
@Document(collection = EventoAuditoria.COLECCION)
public class EventoAuditoria {

    public static final String COLECCION = "auditoria_eventos";

    @Id
    private Long id;
    private Long empresaId;
    private Long proyectoId;
    private Long actorId;
    private String modulo;
    private String accion;
    private String entidadTipo;
    private Long entidadId;
    private String descripcion;
    private String datosAnterioresJson;
    private String datosNuevosJson;
    private String ipOrigen;
    private String agenteUsuario;
    private String correlationId;
    private LocalDateTime ocurridoEn;

    protected EventoAuditoria() { }

    public EventoAuditoria(Long id, Long empresaId, Long proyectoId, Long actorId, String modulo,
                           String accion, String entidadTipo, Long entidadId, String descripcion,
                           String datosAnterioresJson, String datosNuevosJson, String ipOrigen,
                           String agenteUsuario, String correlationId, LocalDateTime ocurridoEn) {
        this.id = id;
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.actorId = actorId;
        this.modulo = modulo;
        this.accion = accion;
        this.entidadTipo = entidadTipo;
        this.entidadId = entidadId;
        this.descripcion = descripcion;
        this.datosAnterioresJson = datosAnterioresJson;
        this.datosNuevosJson = datosNuevosJson;
        this.ipOrigen = ipOrigen;
        this.agenteUsuario = agenteUsuario;
        this.correlationId = correlationId;
        this.ocurridoEn = ocurridoEn;
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public Long getActorId() { return actorId; }
    public String getModulo() { return modulo; }
    public String getAccion() { return accion; }
    public String getEntidadTipo() { return entidadTipo; }
    public Long getEntidadId() { return entidadId; }
    public String getDescripcion() { return descripcion; }
    public String getDatosAnterioresJson() { return datosAnterioresJson; }
    public String getDatosNuevosJson() { return datosNuevosJson; }
    public String getIpOrigen() { return ipOrigen; }
    public String getAgenteUsuario() { return agenteUsuario; }
    public String getCorrelationId() { return correlationId; }
    public LocalDateTime getOcurridoEn() { return ocurridoEn; }
}
