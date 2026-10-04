package com.inmobiliaria.inmobiliaria_api.service.seguridad;

import com.inmobiliaria.inmobiliaria_api.config.SecurityProperties;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.IntentoAcceso;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.IntentoAccesoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAuditService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final UsuarioRepository usuarioRepository;
    private final IntentoAccesoRepository intentoAccesoRepository;
    private final SecurityProperties properties;

    public LoginAuditService(
            UsuarioRepository usuarioRepository,
            IntentoAccesoRepository intentoAccesoRepository,
            SecurityProperties properties
    ) {
        this.usuarioRepository = usuarioRepository;
        this.intentoAccesoRepository = intentoAccesoRepository;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarExito(
            String identificador,
            Long usuarioId,
            String ip,
            String agente
    ) {
        LocalDateTime ahora = LocalDateTime.now(BOGOTA);
        usuarioRepository.findById(usuarioId).ifPresent(usuario ->
                usuario.registrarAccesoExitoso(ahora)
        );
        intentoAccesoRepository.save(new IntentoAcceso(
                usuarioId,
                limitar(identificador, 180),
                "EXITOSO",
                null,
                limitar(ip, 64),
                limitar(agente, 500),
                ahora
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFallo(
            String identificador,
            String ip,
            String agente
    ) {
        LocalDateTime ahora = LocalDateTime.now(BOGOTA);
        Optional<Usuario> usuario = usuarioRepository.findByIdentificador(
                identificador.trim()
        );
        boolean bloqueado = usuario.map(value -> {
            if (value.estaBloqueado(ahora)) {
                return true;
            }
            return value.registrarAccesoFallido(
                    properties.maxFailedAttempts(),
                    ahora,
                    ahora.plus(properties.lockDuration())
            );
        }).orElse(false);

        intentoAccesoRepository.save(new IntentoAcceso(
                usuario.map(Usuario::getId).orElse(null),
                limitar(identificador, 180),
                bloqueado ? "BLOQUEADO" : "FALLIDO",
                bloqueado
                        ? "Máximo de intentos fallidos alcanzado"
                        : "Credenciales inválidas",
                limitar(ip, 64),
                limitar(agente, 500),
                ahora
        ));
    }

    private String limitar(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
