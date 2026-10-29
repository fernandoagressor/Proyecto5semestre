package com.inmobiliaria.inmobiliaria_api.service.seguridad;

import com.inmobiliaria.inmobiliaria_api.config.SecurityProperties;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.IntentoAcceso;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.IntentoAccesoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.DetectorAnomaliasService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAuditService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    static final String SECUENCIA_INTENTOS = "intentos_acceso";
    private static final Logger log = LoggerFactory.getLogger(LoginAuditService.class);

    private final UsuarioRepository usuarioRepository;
    private final IntentoAccesoRepository intentoAccesoRepository;
    private final SecurityProperties properties;
    private final SecuenciaService secuencias;
    private final DetectorAnomaliasService detector;

    public LoginAuditService(
            UsuarioRepository usuarioRepository,
            IntentoAccesoRepository intentoAccesoRepository,
            SecurityProperties properties,
            SecuenciaService secuencias,
            DetectorAnomaliasService detector
    ) {
        this.usuarioRepository = usuarioRepository;
        this.intentoAccesoRepository = intentoAccesoRepository;
        this.properties = properties;
        this.secuencias = secuencias;
        this.detector = detector;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarExito(
            String identificador,
            Long usuarioId,
            String ip,
            String agente
    ) {
        LocalDateTime ahora = LocalDateTime.now(BOGOTA);
        Optional<Usuario> usuario = usuarioRepository.findById(usuarioId);
        usuario.ifPresent(value -> value.registrarAccesoExitoso(ahora));
        guardarIntento(new IntentoAcceso(
                usuarioId,
                limitar(identificador, 180),
                "EXITOSO",
                null,
                limitar(ip, 64),
                limitar(agente, 500),
                ahora
        ), usuario.map(Usuario::getEmpresaId).orElse(null));
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

        guardarIntento(new IntentoAcceso(
                usuario.map(Usuario::getId).orElse(null),
                limitar(identificador, 180),
                bloqueado ? "BLOQUEADO" : "FALLIDO",
                bloqueado
                        ? "Máximo de intentos fallidos alcanzado"
                        : "Credenciales inválidas",
                limitar(ip, 64),
                limitar(agente, 500),
                ahora
        ), usuario.map(Usuario::getEmpresaId).orElse(null));
    }

    /**
     * Desde la rama 7 el historial vive en MongoDB, fuera de la transacción de MySQL. Un fallo al
     * guardarlo queda en el log y no impide el inicio de sesión ni el bloqueo de la cuenta.
     * Cada intento guardado pasa por el detector de anomalías.
     */
    private void guardarIntento(IntentoAcceso intento, Long empresaId) {
        try {
            intento.identificar(secuencias.siguiente(SECUENCIA_INTENTOS));
            intentoAccesoRepository.insertar(intento);
        } catch (RuntimeException e) {
            log.error("No se pudo registrar el intento de acceso {} de {}", intento.getResultado(),
                    intento.getIdentificador(), e);
            return;
        }
        detector.evaluarIntento(intento, empresaId);
    }

    private String limitar(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
