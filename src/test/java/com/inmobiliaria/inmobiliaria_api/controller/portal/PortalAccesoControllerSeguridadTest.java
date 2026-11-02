package com.inmobiliaria.inmobiliaria_api.controller.portal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inmobiliaria.inmobiliaria_api.controller.SeguridadWebTest;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.portal.PortalAccesoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Habilitar y reenviar el acceso al portal exigen sus permisos y usan la empresa del token. */
@WebMvcTest(PortalAccesoController.class)
class PortalAccesoControllerSeguridadTest extends SeguridadWebTest {

    @MockitoBean
    private PortalAccesoService service;

    @Test
    void habilitarExigePermiso() throws Exception {
        mockMvc.perform(post("/api/v1/clientes/4/portal").with(usuarioCon("CLIENTES_EDITAR")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mockMvc.perform(post("/api/v1/clientes/4/portal").with(usuarioCon("PORTAL_CLIENTE_HABILITAR")))
                .andExpect(status().isCreated());
        verify(service).habilitar(eq(new ContextoUsuario(USUARIO_ID, EMPRESA_ID)), eq(4L));
    }

    @Test
    void reenviarExigePermiso() throws Exception {
        mockMvc.perform(post("/api/v1/clientes/4/portal/reenviar").with(usuarioCon("PORTAL_CLIENTE_HABILITAR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/clientes/4/portal/reenviar").with(usuarioCon("PORTAL_CLIENTE_REENVIAR_ACCESO")))
                .andExpect(status().isOk());
        verify(service).reenviar(eq(new ContextoUsuario(USUARIO_ID, EMPRESA_ID)), eq(4L));
    }
}
