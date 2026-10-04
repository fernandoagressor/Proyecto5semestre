package com.inmobiliaria.inmobiliaria_api.entity.seguridad;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "sesiones_usuario")
public class SesionUsuario extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "empresa_contexto_id")
    private Long empresaContextoId;

    @Column(
            name = "token_hash",
            nullable = false,
            length = 64,
            columnDefinition = "CHAR(64)"
    )
    private String tokenHash;

    @Column(name = "ip_origen", length = 64)
    private String ipOrigen;

    @Column(name = "agente_usuario", length = 500)
    private String agenteUsuario;

    @Column(name = "iniciada_en", nullable = false)
    private LocalDateTime iniciadaEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "revocada_en")
    private LocalDateTime revocadaEn;

    @Column(name = "motivo_revocacion", length = 200)
    private String motivoRevocacion;

    protected SesionUsuario() {
    }

    public SesionUsuario(
            Long usuarioId,
            Long empresaContextoId,
            String tokenHash,
            String ipOrigen,
            String agenteUsuario,
            LocalDateTime iniciadaEn,
            LocalDateTime expiraEn
    ) {
        this.usuarioId = usuarioId;
        this.empresaContextoId = empresaContextoId;
        this.tokenHash = tokenHash;
        this.ipOrigen = ipOrigen;
        this.agenteUsuario = agenteUsuario;
        this.iniciadaEn = iniciadaEn;
        this.expiraEn = expiraEn;
    }

    public boolean estaVigente(LocalDateTime ahora) {
        return revocadaEn == null && expiraEn.isAfter(ahora);
    }

    public void rotar(String nuevoTokenHash, LocalDateTime nuevaExpiracion) {
        tokenHash = nuevoTokenHash;
        expiraEn = nuevaExpiracion;
    }

    public void revocar(LocalDateTime ahora, String motivo) {
        revocadaEn = ahora;
        motivoRevocacion = motivo;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getEmpresaContextoId() {
        return empresaContextoId;
    }

    public void cambiarEmpresaContexto(Long empresaId) {
        empresaContextoId = empresaId;
    }
}
