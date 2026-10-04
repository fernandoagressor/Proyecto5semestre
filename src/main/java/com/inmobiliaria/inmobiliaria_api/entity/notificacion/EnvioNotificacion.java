package com.inmobiliaria.inmobiliaria_api.entity.notificacion;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity
@Table(name = "envios_notificacion")
public class EnvioNotificacion extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "notificacion_id")
    private Long notificacionId;
    @Column(name = "plantilla_id")
    private Long plantillaId;
    @Column(nullable = false, length = 20)
    private String canal;
    @Column(nullable = false, length = 200)
    private String destinatario;
    @Column(nullable = false, length = 20)
    private String estado;
    @Column(length = 100)
    private String proveedor;
    @Column(name = "referencia_externa", length = 180)
    private String referenciaExterna;
    @Column(nullable = false)
    private short intentos;
    @Column(name = "ultimo_error", length = 700)
    private String ultimoError;
    @Column(name = "enviado_en")
    private LocalDateTime enviadoEn;
    @Column(name = "entregado_en")
    private LocalDateTime entregadoEn;
    @Version
    @Column(nullable = false)
    private Long version;

    protected EnvioNotificacion() { }

    public EnvioNotificacion(Long notificacionId, Long plantillaId, String canal, String destinatario) {
        this.notificacionId = notificacionId;
        this.plantillaId = plantillaId;
        this.canal = canal;
        this.destinatario = destinatario;
        this.estado = "PENDIENTE";
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
    public Long getNotificacionId() { return notificacionId; }
    public Long getPlantillaId() { return plantillaId; }
    public String getCanal() { return canal; }
    public String getDestinatario() { return destinatario; }
    public String getEstado() { return estado; }
    public String getProveedor() { return proveedor; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public short getIntentos() { return intentos; }
    public String getUltimoError() { return ultimoError; }
    public LocalDateTime getEnviadoEn() { return enviadoEn; }
    public LocalDateTime getEntregadoEn() { return entregadoEn; }
}
