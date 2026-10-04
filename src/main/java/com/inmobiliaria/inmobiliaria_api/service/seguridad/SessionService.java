package com.inmobiliaria.inmobiliaria_api.service.seguridad;

import com.inmobiliaria.inmobiliaria_api.config.SecurityProperties;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.SesionUsuario;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.SesionUsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.security.TokenOpacoService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.NoAutorizadoException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final SesionUsuarioRepository sessionRepository;
    private final TokenOpacoService tokenOpacoService;
    private final SecurityProperties properties;

    public SessionService(
            SesionUsuarioRepository sessionRepository,
            TokenOpacoService tokenOpacoService,
            SecurityProperties properties
    ) {
        this.sessionRepository = sessionRepository;
        this.tokenOpacoService = tokenOpacoService;
        this.properties = properties;
    }

    @Transactional
    public String crear(
            Long usuarioId,
            Long empresaContextoId,
            String ip,
            String agente
    ) {
        String token = tokenOpacoService.generar();
        LocalDateTime ahora = LocalDateTime.now(BOGOTA);
        sessionRepository.save(new SesionUsuario(
                usuarioId,
                empresaContextoId,
                tokenOpacoService.hash(token),
                limitar(ip, 64),
                limitar(agente, 500),
                ahora,
                ahora.plus(properties.refreshTokenTtl())
        ));
        return token;
    }

    @Transactional
    public RotacionSesion rotar(String refreshToken) {
        LocalDateTime ahora = LocalDateTime.now(BOGOTA);
        SesionUsuario sesion = buscar(refreshToken);
        if (!sesion.estaVigente(ahora)) {
            throw new NoAutorizadoException("La sesión expiró o fue revocada");
        }

        String nuevoToken = tokenOpacoService.generar();
        sesion.rotar(
                tokenOpacoService.hash(nuevoToken),
                ahora.plus(properties.refreshTokenTtl())
        );
        return new RotacionSesion(
                sesion.getUsuarioId(),
                sesion.getEmpresaContextoId(),
                nuevoToken
        );
    }

    @Transactional
    public RotacionSesion cambiarContexto(
            String refreshToken,
            Long usuarioIdEsperado,
            Long empresaId
    ) {
        LocalDateTime ahora = LocalDateTime.now(BOGOTA);
        SesionUsuario sesion = buscar(refreshToken);
        if (!sesion.getUsuarioId().equals(usuarioIdEsperado)
                || !sesion.estaVigente(ahora)) {
            throw new NoAutorizadoException("La sesión no pertenece al usuario");
        }
        String nuevoToken = tokenOpacoService.generar();
        sesion.cambiarEmpresaContexto(empresaId);
        sesion.rotar(
                tokenOpacoService.hash(nuevoToken),
                ahora.plus(properties.refreshTokenTtl())
        );
        return new RotacionSesion(usuarioIdEsperado, empresaId, nuevoToken);
    }

    @Transactional
    public void revocar(
            String refreshToken,
            Long usuarioIdEsperado,
            String motivo
    ) {
        SesionUsuario sesion = buscar(refreshToken);
        if (!sesion.getUsuarioId().equals(usuarioIdEsperado)) {
            throw new NoAutorizadoException("La sesión no pertenece al usuario");
        }
        sesion.revocar(LocalDateTime.now(BOGOTA), limitar(motivo, 200));
    }

    @Transactional
    public int revocarTodas(Long usuarioId, String motivo) {
        return sessionRepository.revokeAllActive(
                usuarioId,
                LocalDateTime.now(BOGOTA),
                limitar(motivo, 200)
        );
    }

    private SesionUsuario buscar(String refreshToken) {
        return sessionRepository.findActiveForUpdate(
                tokenOpacoService.hash(refreshToken)
        ).orElseThrow(() -> new NoAutorizadoException(
                "La sesión no existe"
        ));
    }

    private String limitar(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record RotacionSesion(
            Long usuarioId,
            Long empresaContextoId,
            String refreshToken
    ) {
    }
}
