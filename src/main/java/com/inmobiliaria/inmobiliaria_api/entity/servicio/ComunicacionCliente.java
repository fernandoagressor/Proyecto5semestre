package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/** Comunicación individual o masiva a clientes, guardada en MongoDB (rama 10). */
@Document(collection = ComunicacionCliente.COLECCION)
public class ComunicacionCliente {
    public static final String COLECCION = "comunicaciones_cliente";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private Long clienteId;
    private Long enviadoPor;
    private String canal;
    private String asunto;
    private String contenido;
    private boolean masiva;
    private String estado;
    private LocalDateTime programadaPara;
    private LocalDateTime enviadaEn;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    @Version
    private Long version;

    protected ComunicacionCliente() { }

    public ComunicacionCliente(Long empresaId, Long clienteId, Long enviadoPor, String canal,
                               String asunto, String contenido, boolean masiva,
                               LocalDateTime programadaPara) {
        if (masiva && clienteId != null) {
            throw new ReglaNegocioException("Una comunicación masiva no puede apuntar a un solo cliente");
        }
        if (!masiva && clienteId == null) {
            throw new ReglaNegocioException("La comunicación individual requiere cliente");
        }
        this.empresaId = empresaId;
        this.clienteId = clienteId;
        this.enviadoPor = enviadoPor;
        this.canal = canal;
        this.asunto = asunto;
        this.contenido = contenido;
        this.masiva = masiva;
        this.programadaPara = programadaPara;
        this.estado = programadaPara == null ? "BORRADOR" : "PROGRAMADA";
        this.creadoEn = LocalDateTime.now(BOGOTA);
        this.actualizadoEn = creadoEn;
    }

    public void programar(LocalDateTime fecha) {
        if (!"BORRADOR".equals(estado) || fecha == null) {
            throw new ReglaNegocioException("Solo un borrador puede programarse");
        }
        programadaPara = fecha;
        estado = "PROGRAMADA";
    }

    public void confirmarEnvio(LocalDateTime fecha) {
        if (!"PROGRAMADA".equals(estado) && !"BORRADOR".equals(estado)) {
            throw new ReglaNegocioException("La comunicación ya fue procesada");
        }
        enviadaEn = fecha;
        estado = "ENVIADA";
    }

    public void marcarFallida() {
        if ("ENVIADA".equals(estado)) {
            throw new ReglaNegocioException("Una comunicación enviada no puede marcarse fallida");
        }
        estado = "FALLIDA";
    }

    /** Asigna el identificador de un registro nuevo. Solo puede hacerse una vez. */
    public void identificar(long nuevoId) {
        if (id != null) {
            throw new IllegalStateException("La comunicación ya tiene identificador " + id);
        }
        id = nuevoId;
    }

    /** Marca la fecha de la última modificación; lo llama el repositorio antes de guardar. */
    public void tocar() {
        actualizadoEn = LocalDateTime.now(BOGOTA);
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getClienteId() { return clienteId; }
    public Long getEnviadoPor() { return enviadoPor; }
    public String getCanal() { return canal; }
    public String getAsunto() { return asunto; }
    public String getContenido() { return contenido; }
    public boolean isMasiva() { return masiva; }
    public String getEstado() { return estado; }
    public LocalDateTime getProgramadaPara() { return programadaPara; }
    public LocalDateTime getEnviadaEn() { return enviadaEn; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
}
