package com.inmobiliaria.inmobiliaria_api.entity.seguridad;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
public class Usuario extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id")
    private Long empresaId;

    @Column(name = "persona_id", nullable = false)
    private Long personaId;

    @Column(name = "nombre_usuario", nullable = false, length = 80)
    private String nombreUsuario;

    @Column(nullable = false, length = 180)
    private String correo;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false, length = 20)
    private TipoUsuario tipoUsuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoUsuario estado;

    @Column(name = "requiere_cambio_password", nullable = false)
    private boolean requiereCambioPassword;

    @Column(name = "token_version", nullable = false)
    private Long tokenVersion;

    @Column(name = "mfa_habilitado", nullable = false)
    private boolean mfaHabilitado;

    @Column(name = "ultimo_acceso_en")
    private LocalDateTime ultimoAccesoEn;

    @Column(name = "intentos_fallidos", nullable = false)
    private short intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Usuario() {
    }

    public Usuario(
            Long empresaId,
            Long personaId,
            String nombreUsuario,
            String correo,
            String passwordHash
    ) {
        this.empresaId = empresaId;
        this.personaId = personaId;
        this.nombreUsuario = nombreUsuario;
        this.correo = correo;
        this.passwordHash = passwordHash;
        this.tipoUsuario = TipoUsuario.INTERNO;
        this.estado = EstadoUsuario.ACTIVO;
        this.requiereCambioPassword = true;
        this.tokenVersion = 0L;
        this.mfaHabilitado = false;
        this.intentosFallidos = 0;
        this.version = 0L;
    }

    public void registrarAccesoExitoso(LocalDateTime ahora) {
        ultimoAccesoEn = ahora;
        intentosFallidos = 0;
        bloqueadoHasta = null;
    }

    public boolean registrarAccesoFallido(
            int maximoIntentos,
            LocalDateTime ahora,
            LocalDateTime bloquearHasta
    ) {
        if (this.bloqueadoHasta != null && !this.bloqueadoHasta.isAfter(ahora)) {
            intentosFallidos = 0;
            this.bloqueadoHasta = null;
        }
        intentosFallidos++;
        if (intentosFallidos >= maximoIntentos) {
            this.bloqueadoHasta = bloquearHasta;
            return true;
        }
        return false;
    }

    public boolean estaBloqueado(LocalDateTime ahora) {
        return estado == EstadoUsuario.BLOQUEADO
                || (bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora));
    }

    public Long getId() {
        return id;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public Long getPersonaId() {
        return personaId;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public String getCorreo() {
        return correo;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public TipoUsuario getTipoUsuario() {
        return tipoUsuario;
    }

    public EstadoUsuario getEstado() {
        return estado;
    }

    public boolean isRequiereCambioPassword() {
        return requiereCambioPassword;
    }

    public boolean isMfaHabilitado() {
        return mfaHabilitado;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    public LocalDateTime getUltimoAccesoEn() {
        return ultimoAccesoEn;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public LocalDateTime getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    public void actualizarCorreo(String nuevoCorreo) {
        if (nuevoCorreo == null || nuevoCorreo.isBlank()) {
            throw new IllegalArgumentException("El correo es obligatorio");
        }
        correo = nuevoCorreo.trim().toLowerCase();
    }

    public void cambiarPassword(String nuevoPasswordHash) {
        if (nuevoPasswordHash == null || nuevoPasswordHash.isBlank()) {
            throw new IllegalArgumentException("El hash de contraseña es obligatorio");
        }
        passwordHash = nuevoPasswordHash;
        requiereCambioPassword = false;
        intentosFallidos = 0;
        bloqueadoHasta = null;
        tokenVersion = tokenVersion == null ? 1L : tokenVersion + 1L;
    }

    public Long getVersion() {
        return version;
    }
}
