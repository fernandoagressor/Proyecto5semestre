package com.inmobiliaria.inmobiliaria_api.controller.seguridad;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inmobiliaria.inmobiliaria_api.controller.SeguridadWebTest;
import com.inmobiliaria.inmobiliaria_api.dto.seguridad.LoginRequest;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.DemasiadosIntentosException;
import com.inmobiliaria.inmobiliaria_api.service.seguridad.AuthService;
import com.inmobiliaria.inmobiliaria_api.service.seguridad.CredencialesService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Reglas de acceso del AuthController: login público y validado, bloqueo por intentos (429),
 * /me protegido pero disponible con contraseña temporal, y cambio de empresa solo para SUPER_ADMIN.
 */
@WebMvcTest(AuthController.class)
class AuthControllerSeguridadTest extends SeguridadWebTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ME = "/api/v1/auth/me";
    private static final String LOGIN_VALIDO = """
            {"identificador":"gerente@nexora.test","password":"Clave-Segura-2026"}
            """;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private CredencialesService credencialesService;

    @Test
    void elLoginEsPublicoYUsaLaPrimeraIpDeXForwardedFor() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.5")
                        .content(LOGIN_VALIDO))
                .andExpect(status().isOk());

        verify(authService).login(any(LoginRequest.class), eq("203.0.113.5"), any());
    }

    @Test
    void elLoginConCamposVaciosResponde400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identificador":"","password":""}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void elLoginBloqueadoPorIntentosResponde429() throws Exception {
        when(authService.login(any(), any(), any()))
                .thenThrow(new DemasiadosIntentosException("Demasiados intentos"));

        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_VALIDO))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void meSinTokenResponde401() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meSigueDisponibleConContrasenaTemporal() throws Exception {
        mockMvc.perform(get(ME).with(usuarioConContrasenaTemporal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(7))
                .andExpect(jsonPath("$.requiereCambioPassword").value(true));
    }

    @Test
    void cambiarDeEmpresaSinSerSuperAdminResponde403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/contexto")
                        .with(usuarioCon("DASHBOARD_GERENCIAL_VER", "USUARIOS_VER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresaId":2,"refreshToken":"token-de-prueba"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }
}