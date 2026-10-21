package com.inmobiliaria.inmobiliaria_api.controller.gerencia;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.inmobiliaria.inmobiliaria_api.config.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.security.InmobiliariaUserDetailsService;
import com.inmobiliaria.inmobiliaria_api.security.JwtTokenVersionValidator;
import com.inmobiliaria.inmobiliaria_api.security.SecurityConfig;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.DashboardService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Verifica las reglas de acceso del DashboardController sin levantar bases de datos:
 * sin token → 401, sin permiso → 403, con permiso → 200 y la empresa sale del token.
 */
@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "app.security.jwt-secret=0123456789abcdef0123456789abcdef")
class DashboardControllerSeguridadTest {

    private static final String GERENCIAL = "/api/v1/dashboards/gerencial";
    private static final long USUARIO_ID = 7L;
    private static final long EMPRESA_ID = 3L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private InmobiliariaUserDetailsService userDetailsService;

    @MockitoBean
    private JwtTokenVersionValidator tokenVersionValidator;

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get(GERENCIAL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(dashboardService);
    }

    @Test
    void conTokenPeroSinElPermisoRespondeA403() throws Exception {
        mockMvc.perform(get(GERENCIAL).with(usuarioCon("DASHBOARD_COMERCIAL_VER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService);
    }

    @Test
    void conElPermisoResponde200YUsaLaEmpresaDelToken() throws Exception {
        mockMvc.perform(get(GERENCIAL).with(usuarioCon("DASHBOARD_GERENCIAL_VER")))
                .andExpect(status().isOk());

        verify(dashboardService).gerencial(
                eq(new ContextoUsuario(USUARIO_ID, EMPRESA_ID)), isNull(), isNull(), isNull()
        );
    }

    @Test
    void conContrasenaTemporalPendienteResponde403AunqueTengaElPermiso() throws Exception {
        mockMvc.perform(get(GERENCIAL).with(
                        jwt().jwt(token -> token
                                        .subject(String.valueOf(USUARIO_ID))
                                        .claim("empresa_id", EMPRESA_ID)
                                        .claim("requiere_cambio_password", true))
                                .authorities(new SimpleGrantedAuthority("DASHBOARD_GERENCIAL_VER"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService);
    }

    private static RequestPostProcessor usuarioCon(String... permisos) {
        GrantedAuthority[] autoridades = Arrays.stream(permisos)
                .map(SimpleGrantedAuthority::new)
                .toArray(GrantedAuthority[]::new);
        return jwt()
                .jwt(token -> token
                        .subject(String.valueOf(USUARIO_ID))
                        .claim("empresa_id", EMPRESA_ID))
                .authorities(autoridades);
    }
}