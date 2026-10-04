package com.inmobiliaria.inmobiliaria_api.security;

import com.inmobiliaria.inmobiliaria_api.entity.seguridad.EstadoUsuario;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class UsuarioPrincipal implements UserDetails {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final Long usuarioId;
    private final Long empresaId;
    private final String nombreUsuario;
    private final String correo;
    private final String passwordHash;
    private final boolean habilitado;
    private final boolean noBloqueado;
    private final boolean requiereCambioPassword;
    private final boolean mfaHabilitado;
    private final Long tokenVersion;
    private final List<GrantedAuthority> authorities;

    private UsuarioPrincipal(
            Usuario usuario,
            Collection<String> authorityCodes,
            Long empresaContextoId,
            boolean empresaHabilitada
    ) {
        usuarioId = usuario.getId();
        empresaId = empresaContextoId;
        nombreUsuario = usuario.getNombreUsuario();
        correo = usuario.getCorreo();
        passwordHash = usuario.getPasswordHash();
        habilitado = usuario.getEstado() == EstadoUsuario.ACTIVO
                && empresaHabilitada;
        noBloqueado = !usuario.estaBloqueado(LocalDateTime.now(BOGOTA));
        requiereCambioPassword = usuario.isRequiereCambioPassword();
        mfaHabilitado = usuario.isMfaHabilitado();
        tokenVersion = usuario.getTokenVersion();
        authorities = authorityCodes.stream()
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public static UsuarioPrincipal from(
            Usuario usuario,
            Collection<String> authorityCodes
    ) {
        return from(usuario, authorityCodes, true);
    }

    public static UsuarioPrincipal from(
            Usuario usuario,
            Collection<String> authorityCodes,
            boolean empresaHabilitada
    ) {
        return new UsuarioPrincipal(
                usuario,
                authorityCodes,
                usuario.getEmpresaId(),
                empresaHabilitada
        );
    }

    public UsuarioPrincipal conEmpresaContexto(Long empresaContextoId) {
        return new UsuarioPrincipal(
                usuarioId,
                empresaContextoId,
                nombreUsuario,
                correo,
                passwordHash,
                habilitado,
                noBloqueado,
                requiereCambioPassword,
                mfaHabilitado,
                tokenVersion,
                authorities
        );
    }

    private UsuarioPrincipal(
            Long usuarioId,
            Long empresaId,
            String nombreUsuario,
            String correo,
            String passwordHash,
            boolean habilitado,
            boolean noBloqueado,
            boolean requiereCambioPassword,
            boolean mfaHabilitado,
            Long tokenVersion,
            List<GrantedAuthority> authorities
    ) {
        this.usuarioId = usuarioId;
        this.empresaId = empresaId;
        this.nombreUsuario = nombreUsuario;
        this.correo = correo;
        this.passwordHash = passwordHash;
        this.habilitado = habilitado;
        this.noBloqueado = noBloqueado;
        this.requiereCambioPassword = requiereCambioPassword;
        this.mfaHabilitado = mfaHabilitado;
        this.tokenVersion = tokenVersion;
        this.authorities = List.copyOf(authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return correo;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return noBloqueado;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return habilitado;
    }

    public Long usuarioId() {
        return usuarioId;
    }

    public Long empresaId() {
        return empresaId;
    }

    public String nombreUsuario() {
        return nombreUsuario;
    }

    public String correo() {
        return correo;
    }

    public boolean requiereCambioPassword() {
        return requiereCambioPassword;
    }

    public boolean mfaHabilitado() {
        return mfaHabilitado;
    }

    public Long tokenVersion() {
        return tokenVersion;
    }
}
