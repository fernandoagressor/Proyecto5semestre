package com.inmobiliaria.inmobiliaria_api.entity.notificacion;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Notificación dentro de la aplicación, guardada en MongoDB (rama 6).
 *
 * <p>Los intentos de entrega por canales externos (correo, SMS, WhatsApp, push) viven embebidos
 * en {@code envios}. Ids numéricos desde {@code contadores}; las migradas conservan el id de MySQL.</p>
 */
@Document(collection = Notificacion.COLECCION)
public class Notificacion {

    public static final String COLECCION = "notificaciones";

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private Long usuarioId;
    private String tipo;
    private String titulo;
    private String mensaje;
    private String entidadTipo;
    private Long entidadId;
    private String prioridad;
    private LocalDateTime leidaEn;
    private LocalDateTime expiraEn;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private List<EnvioNotificacion> envios = new ArrayList<>();

    @Version
    private Long version;

    protected Notificacion() { }

    public Notificacion(Long empresaId, Long usuarioId, String tipo, String titulo,
                        String mensaje, String entidadTipo, Long entidadId,
                        String prioridad, LocalDateTime expiraEn) {
        this.empresaId = empresaId;
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.entidadTipo = entidadTipo;
        this.entidadId = entidadId;
        this.prioridad = prioridad;
        this.expiraEn = expiraEn;
        this.creadoEn = ahora();
        this.actualizadoEn = creadoEn;
    }

    /** Asigna el identificador de una notificación nueva. Solo puede hacerse una vez. */
    public void identificar(long nuevoId) {
        if (id != null) {
            throw new IllegalStateException("La notificación ya tiene identificador " + id);
        }
        id = nuevoId;
    }

    public void marcarLeida(LocalDateTime fecha) {
        if (leidaEn == null) {
            leidaEn = fecha;
            actualizadoEn = ahora();
        }
    }

    /** Vigente si no tiene vencimiento o aún no llega. */
    public boolean vigente(LocalDateTime ahora) {
        return expiraEn == null || expiraEn.isAfter(ahora);
    }

    public EnvioNotificacion agregarEnvio(long envioId, Long plantillaId, String canal, String destinatario) {
        EnvioNotificacion envio = new EnvioNotificacion(envioId, plantillaId, canal, destinatario, ahora());
        envios.add(envio);
        return envio;
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getUsuarioId() { return usuarioId; }
    public String getTipo() { return tipo; }
    public String getTitulo() { return titulo; }
    public String getMensaje() { return mensaje; }
    public String getEntidadTipo() { return entidadTipo; }
    public Long getEntidadId() { return entidadId; }
    public String getPrioridad() { return prioridad; }
    public LocalDateTime getLeidaEn() { return leidaEn; }
    public LocalDateTime getExpiraEn() { return expiraEn; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public List<EnvioNotificacion> getEnvios() { return List.copyOf(envios); }
    public Long getVersion() { return version; }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(BOGOTA);
    }
}
