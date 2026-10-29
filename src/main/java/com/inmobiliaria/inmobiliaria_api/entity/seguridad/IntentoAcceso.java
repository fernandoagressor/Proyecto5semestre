package com.inmobiliaria.inmobiliaria_api.entity.seguridad;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Intento de inicio de sesión (exitoso, fallido o bloqueado), guardado en MongoDB (rama 7).
 * El límite de intentos por IP sigue en Redis; aquí queda el historial.
 */
@Document(collection = IntentoAcceso.COLECCION)
public class IntentoAcceso {

    public static final String COLECCION = "intentos_acceso";

    @Id
    private Long id;
    private Long usuarioId;
    private String identificador;
    private String resultado;
    private String motivo;
    private String ipOrigen;
    private String agenteUsuario;
    private LocalDateTime fechaIntento;

    protected IntentoAcceso() {
    }

    public IntentoAcceso(
            Long usuarioId,
            String identificador,
            String resultado,
            String motivo,
            String ipOrigen,
            String agenteUsuario,
            LocalDateTime fechaIntento
    ) {
        this.usuarioId = usuarioId;
        this.identificador = identificador;
        this.resultado = resultado;
        this.motivo = motivo;
        this.ipOrigen = ipOrigen;
        this.agenteUsuario = agenteUsuario;
        this.fechaIntento = fechaIntento;
    }

    /** Asigna el identificador antes de insertar. Solo puede hacerse una vez. */
    public void identificar(long nuevoId) {
        if (id != null) {
            throw new IllegalStateException("El intento ya tiene identificador " + id);
        }
        id = nuevoId;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getIdentificador() { return identificador; }
    public String getResultado() { return resultado; }
    public String getMotivo() { return motivo; }
    public String getIpOrigen() { return ipOrigen; }
    public String getAgenteUsuario() { return agenteUsuario; }
    public LocalDateTime getFechaIntento() { return fechaIntento; }
}
