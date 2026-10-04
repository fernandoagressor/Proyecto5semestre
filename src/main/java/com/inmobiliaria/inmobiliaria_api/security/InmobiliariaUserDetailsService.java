package com.inmobiliaria.inmobiliaria_api.security;

import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import com.inmobiliaria.inmobiliaria_api.repository.empresa.EmpresaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InmobiliariaUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;

    public InmobiliariaUserDetailsService(
            UsuarioRepository usuarioRepository,
            EmpresaRepository empresaRepository
    ) {
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioPrincipal loadUserByUsername(String identificador) {
        Usuario usuario = usuarioRepository.findByIdentificador(identificador.trim())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Credenciales inválidas"
                ));
        return crearPrincipal(usuario);
    }

    @Transactional(readOnly = true)
    public UsuarioPrincipal loadById(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "La cuenta asociada a la sesión ya no existe"
                ));
        return crearPrincipal(usuario);
    }

    private UsuarioPrincipal crearPrincipal(Usuario usuario) {
        Set<String> authorities = new LinkedHashSet<>();
        usuarioRepository.findRoleCodes(usuario.getId())
                .forEach(role -> authorities.add("ROLE_" + role));
        authorities.addAll(
                usuarioRepository.findPermissionCodes(usuario.getId())
        );
        boolean esSuperAdmin = authorities.contains("ROLE_SUPER_ADMIN");
        boolean empresaHabilitada = esSuperAdmin
                || usuario.getEmpresaId() == null
                || empresaRepository.findById(usuario.getEmpresaId())
                        .map(empresa -> "ACTIVA".equals(empresa.getEstado()))
                        .orElse(false);
        return UsuarioPrincipal.from(
                usuario,
                authorities,
                empresaHabilitada
        );
    }
}
