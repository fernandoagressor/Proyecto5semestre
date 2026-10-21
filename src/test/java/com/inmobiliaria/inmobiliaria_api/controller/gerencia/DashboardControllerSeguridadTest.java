package com.inmobiliaria.inmobiliaria_api.controller.gerencia;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inmobiliaria.inmobiliaria_api.controller.SeguridadWebTest;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifica las reglas de acceso del DashboardController sin levantar bases de datos:
 * sin token → 401, sin permiso → 403, con permiso → 200 y la empresa sale del token.
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerSeguridadTest extends SeguridadWebTest {

    private static final String GERENCIAL = "/api/v1/dashboards/gerencial";

    @MockitoBean
    private DashboardService dashboardService;

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
        mockMvc.perform(get(GERENCIAL).with(usuarioConContrasenaTemporal("DASHBOARD_GERENCIAL_VER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService);
    }
}