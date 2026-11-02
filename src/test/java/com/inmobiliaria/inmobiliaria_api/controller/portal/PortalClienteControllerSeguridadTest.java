package com.inmobiliaria.inmobiliaria_api.controller.portal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inmobiliaria.inmobiliaria_api.controller.SeguridadWebTest;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.portal.PortalClienteService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Portal del cliente: contratos, cuotas y pagos exigen su permiso y usan la sesión del token. */
@WebMvcTest(PortalClienteController.class)
class PortalClienteControllerSeguridadTest extends SeguridadWebTest {

    private static final ContextoUsuario SESION = new ContextoUsuario(USUARIO_ID, EMPRESA_ID);

    @MockitoBean
    private PortalClienteService service;

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/v1/portal/contratos")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void contratosExigeContratosVer() throws Exception {
        mockMvc.perform(get("/api/v1/portal/contratos").with(usuarioCon("CUOTAS_VER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/portal/contratos").with(usuarioCon("CONTRATOS_VER")))
                .andExpect(status().isOk());
        verify(service).contratos(eq(SESION));
    }

    @Test
    void cuotasExigeCuotasVerYPasaElContrato() throws Exception {
        mockMvc.perform(get("/api/v1/portal/contratos/5/cuotas").with(usuarioCon("CONTRATOS_VER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/portal/contratos/5/cuotas").with(usuarioCon("CUOTAS_VER")))
                .andExpect(status().isOk());
        verify(service).cuotas(eq(SESION), eq(5L));
    }

    @Test
    void pagosExigePagosVer() throws Exception {
        mockMvc.perform(get("/api/v1/portal/pagos").with(usuarioCon("PAGOS_VER")))
                .andExpect(status().isOk());
        verify(service).pagos(eq(SESION));
    }
}
