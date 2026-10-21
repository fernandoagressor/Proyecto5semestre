package com.inmobiliaria.inmobiliaria_api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.inmobiliaria.inmobiliaria_api.config.SecurityProperties;
import com.inmobiliaria.inmobiliaria_api.security.InmobiliariaUserDetailsService;
import com.inmobiliaria.inmobiliaria_api.security.JwtTokenVersionValidator;
import com.inmobiliaria.inmobiliaria_api.security.SecurityConfig;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Base para pruebas @WebMvcTest que verifican las reglas de acceso de los controladores
 * con la SecurityConfig real del proyecto. Cada subclase declara su controlador con
 * @WebMvcTest y simula sus servicios con @MockitoBean.
 */
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "app.security.jwt-secret=0123456789abcdef0123456789abcdef")
public abstract class SeguridadWebTest {

    protected static final long USUARIO_ID = 7L;
    protected static final long EMPRESA_ID = 3L;

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private InmobiliariaUserDetailsService userDetailsService;

    @MockitoBean
    private JwtTokenVersionValidator tokenVersionValidator;

    /** Usuario autenticado (id 7, empresa 3) con los permisos indicados. */
    protected static RequestPostProcessor usuarioCon(String... permisos) {
        return jwt()
                .jwt(token -> token
                        .subject(String.valueOf(USUARIO_ID))
                        .claim("empresa_id", EMPRESA_ID))
                .authorities(autoridades(permisos));
    }

    /** Igual que usuarioCon, pero con la contraseña temporal pendiente de cambio. */
    protected static RequestPostProcessor usuarioConContrasenaTemporal(String... permisos) {
        return jwt()
                .jwt(token -> token
                        .subject(String.valueOf(USUARIO_ID))
                        .claim("empresa_id", EMPRESA_ID)
                        .claim("requiere_cambio_password", true))
                .authorities(autoridades(permisos));
    }

    private static GrantedAuthority[] autoridades(String... permisos) {
        return Arrays.stream(permisos)
                .map(SimpleGrantedAuthority::new)
                .toArray(GrantedAuthority[]::new);
    }
}