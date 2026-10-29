package com.inmobiliaria.inmobiliaria_api.entity.notificacion;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;

/**
 * Intento de entrega de una notificación por un canal externo; vive embebido en
 * {@link Notificacion}. {@code plantillaId} apunta a {@code plantillas_notificacion} en MySQL.
 */
public class EnvioNotificacion {
    private Long id;
    private Long plantillaId;
    private String canal;
    private String destinatario;
    private String estado;
    private String proveedor;
    private String referenciaExterna;
    private int intentos;
    private String ultimoError;
    private LocalDateTime enviadoEn;
    private LocalDateTime entregadoEn;
    private LocalDateTime creadoEn;

    protected EnvioNotificacion() { }

    public EnvioNotificacion(Long id, Long plantillaId, String canal, String destinatario, LocalDateTime creadoEn) {
        this.id = id;
        this.plantillaId = plantillaId;
        this.canal = canal;
        this.destinatario = destinatario;
        this.estado = "PENDIENTE";
        this.creadoEn = creadoEn;
    }

    public void registrarEnvio(String proveedor, String referencia, LocalDateTime fecha) {
        if (!"PENDIENTE".equals(estado)) {
            throw new ReglaNegocioException("El envío ya fue procesado");
        }
        intentos++;
        this.proveedor = proveedor;
        this.referenciaExterna = referencia;
        enviadoEn = fecha;
        estado = "ENVIADO";
        ultimoError = null;
    }

    public void registrarFallo(String error) {
        intentos++;
        ultimoError = error;
        estado = "FALLIDO";
    }

    public void confirmarEntrega(LocalDateTime fecha) {
        if (!"ENVIADO".equals(estado)) {
            throw new ReglaNegocioException("Solo un envío aceptado puede confirmarse entregado");
        }
        entregadoEn = fecha;
        estado = "ENTREGADO";
    }

    public Long getId() { return id; }
    public Long getPlantillaId() { return plantillaId; }
    public String getCanal() { return canal; }
    public String getDestinatario() { return destinatario; }
    public String getEstado() { return estado; }
    public String getProveedor() { return proveedor; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public int getIntentos() { return intentos; }
    public String getUltimoError() { return ultimoError; }
    public LocalDateTime getEnviadoEn() { return enviadoEn; }
    public LocalDateTime getEntregadoEn() { return entregadoEn; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
