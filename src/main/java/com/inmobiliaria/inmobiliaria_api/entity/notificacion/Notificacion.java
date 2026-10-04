package com.inmobiliaria.inmobiliaria_api.entity.notificacion;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificaciones")
public class Notificacion extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id")
    private Long empresaId;
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
    @Column(nullable = false, length = 40)
    private String tipo;
    @Column(nullable = false, length = 180)
    private String titulo;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensaje;
    @Column(name = "entidad_tipo", length = 50)
    private String entidadTipo;
    @Column(name = "entidad_id")
    private Long entidadId;
    @Column(nullable = false, length = 15)
    private String prioridad;
    @Column(name = "leida_en")
    private LocalDateTime leidaEn;
    @Column(name = "expira_en")
    private LocalDateTime expiraEn;
    @Version
    @Column(nullable = false)
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
    }

    public void marcarLeida(LocalDateTime fecha) {
        if (leidaEn == null) { leidaEn = fecha; }
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
}
