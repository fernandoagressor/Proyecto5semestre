package com.inmobiliaria.inmobiliaria_api.service.seguridad;

import com.inmobiliaria.inmobiliaria_api.dto.seguridad.LoginRequest;
import com.inmobiliaria.inmobiliaria_api.dto.seguridad.TokenResponse;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.empresa.Empresa;
import com.inmobiliaria.inmobiliaria_api.repository.empresa.EmpresaRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.security.InmobiliariaUserDetailsService;
import com.inmobiliaria.inmobiliaria_api.security.JwtService;
import com.inmobiliaria.inmobiliaria_api.security.UsuarioPrincipal;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.NoAutorizadoException;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaRegistroService;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final InmobiliariaUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final LoginAuditService loginAuditService;
    private final EmpresaRepository empresaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditoriaRegistroService auditoria;
    private final LimiteIntentosLoginService limiteIntentos;

    public AuthService(
            AuthenticationManager authenticationManager,
            InmobiliariaUserDetailsService userDetailsService,
            JwtService jwtService,
            SessionService sessionService,
            LoginAuditService loginAuditService,
            EmpresaRepository empresaRepository,
            JdbcTemplate jdbcTemplate,
            AuditoriaRegistroService auditoria,
            LimiteIntentosLoginService limiteIntentos
    ) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.loginAuditService = loginAuditService;
        this.empresaRepository = empresaRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditoria = auditoria;
        this.limiteIntentos = limiteIntentos;
    }

    public TokenResponse login(
            LoginRequest request,
            String ip,
            String agente
    ) {
        String identificador = request.identificador().trim();
        limiteIntentos.verificar(ip);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            identificador,
                            request.password()
                    )
            );
            UsuarioPrincipal principal =
                    (UsuarioPrincipal) authentication.getPrincipal();

            loginAuditService.registrarExito(
                    identificador,
                    principal.usuarioId(),
                    ip,
                    agente
            );
            String refreshToken = sessionService.crear(
                    principal.usuarioId(),
                    principal.empresaId(),
                    ip,
                    agente
            );
            return crearRespuesta(principal, refreshToken);
        } catch (AuthenticationException exception) {
            loginAuditService.registrarFallo(identificador, ip, agente);
            limiteIntentos.registrarFallo(ip);
            throw new NoAutorizadoException("Credenciales inválidas");
        }
    }

    public TokenResponse refresh(String refreshToken) {
        SessionService.RotacionSesion rotacion =
                sessionService.rotar(refreshToken);
        UsuarioPrincipal principal = userDetailsService.loadById(
                rotacion.usuarioId()
        ).conEmpresaContexto(rotacion.empresaContextoId());
        if (!principal.isEnabled() || !principal.isAccountNonLocked()) {
            throw new NoAutorizadoException("La cuenta no está habilitada");
        }
        return crearRespuesta(principal, rotacion.refreshToken());
    }

    public TokenResponse cambiarContexto(
            String refreshToken,
            Long usuarioId,
            Long empresaId
    ) {
        UsuarioPrincipal principal = userDetailsService.loadById(usuarioId);
        boolean esSuperAdmin = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_SUPER_ADMIN"::equals);
        if (!esSuperAdmin) {
            throw new NoAutorizadoException(
                    "Solo SUPER_ADMIN puede cambiar el espacio de trabajo"
            );
        }
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ReglaNegocioException(
                        "La empresa seleccionada no existe"
                ));
        if (!"ACTIVA".equals(empresa.getEstado())) {
            throw new ReglaNegocioException(
                    "Solo se puede entrar a una empresa activa"
            );
        }
        jdbcTemplate.update("""
                INSERT INTO usuario_alcances
                    (usuario_id, empresa_id, proyecto_id, tipo_alcance,
                     asignado_por, vigente_desde, creado_en, actualizado_en)
                SELECT ?, ?, NULL, 'EMPRESA', ?, CURRENT_TIMESTAMP(6),
                       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                WHERE NOT EXISTS (
                    SELECT 1 FROM usuario_alcances
                    WHERE usuario_id = ? AND empresa_id = ?
                      AND proyecto_id IS NULL AND tipo_alcance = 'EMPRESA'
                )
                """, usuarioId, empresaId, usuarioId, usuarioId, empresaId);
        SessionService.RotacionSesion rotacion = sessionService.cambiarContexto(
                refreshToken,
                usuarioId,
                empresaId
        );
        TokenResponse response = crearRespuesta(
                principal.conEmpresaContexto(empresaId),
                rotacion.refreshToken()
        );
        auditoria.registrar(
                new ContextoUsuario(usuarioId, empresaId),
                null,
                "SUPERADMINISTRACION",
                "CONTEXTO_EMPRESA_CAMBIADO",
                "EMPRESA",
                empresaId,
                "SUPER_ADMIN ingresó al espacio de trabajo de la empresa",
                principal.empresaId(),
                empresaId
        );
        return response;
    }

    public void logout(
            String refreshToken,
            Long usuarioId
    ) {
        sessionService.revocar(
                refreshToken,
                usuarioId,
                "Cierre de sesión solicitado por el usuario"
        );
    }

    private TokenResponse crearRespuesta(
            UsuarioPrincipal principal,
            String refreshToken
    ) {
        JwtService.AccessToken accessToken =
                jwtService.crearAccessToken(principal);
        List<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        List<String> roles = authorities.stream()
                .filter(value -> value.startsWith("ROLE_"))
                .map(value -> value.substring("ROLE_".length()))
                .toList();
        List<String> permisos = authorities.stream()
                .filter(value -> !value.startsWith("ROLE_"))
                .toList();

        return new TokenResponse(
                "Bearer",
                accessToken.valor(),
                refreshToken,
                accessToken.expiraEnSegundos(),
                new TokenResponse.UsuarioAutenticadoResponse(
                        principal.usuarioId(),
                        principal.empresaId(),
                        principal.nombreUsuario(),
                        principal.correo(),
                        roles,
                        permisos,
                        principal.requiereCambioPassword(),
                        principal.mfaHabilitado()
                )
        );
    }
}
