package com.inmobiliaria.inmobiliaria_api.entity.portal;

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
@Table(name = "solicitudes_cambio_datos")
public class SolicitudCambioDatos extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;
    @Column(name = "solicitado_por", nullable = false)
    private Long solicitadoPor;
    @Column(nullable = false, length = 60)
    private String campo;
    @Column(name = "valor_anterior", length = 500)
    private String valorAnterior;
    @Column(name = "valor_solicitado", nullable = false, length = 500)
    private String valorSolicitado;
    @Column(nullable = false, length = 500)
    private String motivo;
    @Column(nullable = false, length = 20)
    private String estado;
    @Column(name = "revisado_por")
    private Long revisadoPor;
    @Column(name = "revisado_en")
    private LocalDateTime revisadoEn;
    @Column(name = "comentario_revision", length = 500)
    private String comentarioRevision;
    @Version
    @Column(nullable = false)
    private Long version;

    protected SolicitudCambioDatos() { }

    public SolicitudCambioDatos(Long empresaId, Long clienteId, Long solicitadoPor,
                                String campo, String valorAnterior,
                                String valorSolicitado, String motivo) {
        this.empresaId = empresaId;
        this.clienteId = clienteId;
        this.solicitadoPor = solicitadoPor;
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorSolicitado = valorSolicitado;
        this.motivo = motivo;
        this.estado = "PENDIENTE";
    }

    public void aprobar(Long usuarioId, String comentario, LocalDateTime fecha) {
        revisar(usuarioId, comentario, fecha, "APROBADA");
    }

    public void rechazar(Long usuarioId, String comentario, LocalDateTime fecha) {
        if (comentario == null || comentario.isBlank()) {
            throw new ReglaNegocioException("El motivo de rechazo es obligatorio");
        }
        revisar(usuarioId, comentario, fecha, "RECHAZADA");
    }

    public void cancelar(Long usuarioId) {
        if (!solicitadoPor.equals(usuarioId) || !"PENDIENTE".equals(estado)) {
            throw new ReglaNegocioException("La solicitud no puede cancelarse");
        }
        estado = "CANCELADA";
    }

    private void revisar(Long usuarioId, String comentario, LocalDateTime fecha, String nuevoEstado) {
        if (!"PENDIENTE".equals(estado)) {
            throw new ReglaNegocioException("La solicitud ya fue revisada");
        }
        if (solicitadoPor.equals(usuarioId)) {
            throw new ReglaNegocioException("Quien solicita el cambio no puede aprobarlo ni rechazarlo");
        }
        estado = nuevoEstado;
        revisadoPor = usuarioId;
        revisadoEn = fecha;
        comentarioRevision = comentario;
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getClienteId() { return clienteId; }
    public Long getSolicitadoPor() { return solicitadoPor; }
    public String getCampo() { return campo; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorSolicitado() { return valorSolicitado; }
    public String getMotivo() { return motivo; }
    public String getEstado() { return estado; }
    public Long getRevisadoPor() { return revisadoPor; }
    public LocalDateTime getRevisadoEn() { return revisadoEn; }
    public String getComentarioRevision() { return comentarioRevision; }
}
