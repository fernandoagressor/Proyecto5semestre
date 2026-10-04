package com.inmobiliaria.inmobiliaria_api.entity.servicio;

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
@Table(name = "comunicaciones_cliente")
public class ComunicacionCliente extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "cliente_id")
    private Long clienteId;
    @Column(name = "enviado_por")
    private Long enviadoPor;
    @Column(nullable = false, length = 20)
    private String canal;
    @Column(length = 200)
    private String asunto;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;
    @Column(name = "es_masiva", nullable = false)
    private boolean masiva;
    @Column(nullable = false, length = 20)
    private String estado;
    @Column(name = "programada_para")
    private LocalDateTime programadaPara;
    @Column(name = "enviada_en")
    private LocalDateTime enviadaEn;
    @Version
    @Column(nullable = false)
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
}
