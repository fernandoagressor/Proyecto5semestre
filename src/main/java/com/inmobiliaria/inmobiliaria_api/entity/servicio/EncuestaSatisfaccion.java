package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "encuestas_satisfaccion")
public class EncuestaSatisfaccion extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;
    @Column(name = "solicitud_id")
    private Long solicitudId;
    @Column(nullable = false)
    private short puntuacion;
    @Column(columnDefinition = "TEXT")
    private String comentario;
    @Column(name = "respondida_en", nullable = false)
    private LocalDateTime respondidaEn;

    protected EncuestaSatisfaccion() { }

    public EncuestaSatisfaccion(Long empresaId, Long clienteId, Long solicitudId,
                                short puntuacion, String comentario, LocalDateTime respondidaEn) {
        if (puntuacion < 1 || puntuacion > 5) {
            throw new ReglaNegocioException("La puntuación debe estar entre 1 y 5");
        }
        this.empresaId = empresaId;
        this.clienteId = clienteId;
        this.solicitudId = solicitudId;
        this.puntuacion = puntuacion;
        this.comentario = comentario;
        this.respondidaEn = respondidaEn;
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getClienteId() { return clienteId; }
    public Long getSolicitudId() { return solicitudId; }
    public short getPuntuacion() { return puntuacion; }
    public String getComentario() { return comentario; }
    public LocalDateTime getRespondidaEn() { return respondidaEn; }
}
