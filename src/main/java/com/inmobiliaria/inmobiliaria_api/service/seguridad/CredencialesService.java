package com.inmobiliaria.inmobiliaria_api.service.seguridad;

import com.inmobiliaria.inmobiliaria_api.dto.seguridad.CambioPasswordRequest;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.security.TokenVersionCache;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaRegistroService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CredencialesService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenVersionCache tokenVersionCache;
    private final SessionService sessionService;
    private final AuditoriaRegistroService auditoria;

    public CredencialesService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            SessionService sessionService,
            AuditoriaRegistroService auditoria,
            TokenVersionCache tokenVersionCache
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.auditoria = auditoria;
        this.tokenVersionCache = tokenVersionCache;
    }

    @Transactional
    public void cambiarPassword(Long usuarioId, CambioPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La cuenta autenticada ya no existe"
                ));

        if (!passwordEncoder.matches(request.passwordActual(), usuario.getPasswordHash())) {
            throw new ReglaNegocioException("La contraseña actual no es correcta");
        }
        if (!request.passwordNueva().equals(request.confirmacionPassword())) {
            throw new ReglaNegocioException("La nueva contraseña y su confirmación no coinciden");
        }
        if (passwordEncoder.matches(request.passwordNueva(), usuario.getPasswordHash())) {
            throw new ReglaNegocioException("La nueva contraseña debe ser diferente de la actual");
        }

        usuario.cambiarPassword(passwordEncoder.encode(request.passwordNueva()));
        usuarioRepository.saveAndFlush(usuario);
        tokenVersionCache.invalidar(usuarioId);
        sessionService.revocarTodas(
                usuarioId,
                "Cambio de contraseña realizado por el usuario"
        );
        auditoria.registrar(
                new ContextoUsuario(usuarioId, usuario.getEmpresaId()),
                null,
                "SEGURIDAD",
                "PASSWORD_CAMBIADO",
                "USUARIO",
                usuarioId,
                "El usuario actualizó su contraseña y se revocaron sus sesiones",
                null,
                "Credenciales actualizadas"
        );
    }
}
