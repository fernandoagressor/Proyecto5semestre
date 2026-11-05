package com.inmobiliaria.inmobiliaria_api.controller.inventario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inmobiliaria.inmobiliaria_api.controller.SeguridadWebTest;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.inventario.InventarioService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Generar manzanas y lotes exige su permiso; todo usa la empresa del token. */
@WebMvcTest(InventarioController.class)
class InventarioControllerSeguridadTest extends SeguridadWebTest {

    private static final ContextoUsuario SESION = new ContextoUsuario(USUARIO_ID, EMPRESA_ID);

    @MockitoBean
    private InventarioService service;

    @Test
    void crearEtapaConManzanasExigeTambienGestionarManzanas() throws Exception {
        String etapa = "{\"nombre\":\"Etapa 1\",\"cantidadManzanas\":10}";
        mockMvc.perform(post("/api/v1/proyectos/7/etapas").with(usuarioCon("ETAPAS_GESTIONAR"))
                        .contentType(MediaType.APPLICATION_JSON).content(etapa))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);

        mockMvc.perform(post("/api/v1/proyectos/7/etapas").with(usuarioCon("ETAPAS_GESTIONAR", "MANZANAS_GESTIONAR"))
                        .contentType(MediaType.APPLICATION_JSON).content(etapa))
                .andExpect(status().isCreated());
        verify(service).crearEtapa(eq(SESION), eq(7L), any());
    }

    @Test
    void generarLotesExigeCrearLotes() throws Exception {
        String url = "/api/v1/proyectos/7/etapas/11/manzanas/21/lotes/generar";
        mockMvc.perform(post(url).with(usuarioCon("LOTES_VER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":20}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(usuarioCon("LOTES_CREAR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":20}"))
                .andExpect(status().isCreated());
        verify(service).generarLotes(eq(SESION), eq(7L), eq(11L), eq(21L), any());
    }

    @Test
    void rechazaCantidadesFueraDeRango() throws Exception {
        String url = "/api/v1/proyectos/7/etapas/11/manzanas/21/lotes/generar";
        mockMvc.perform(post(url).with(usuarioCon("LOTES_CREAR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(url).with(usuarioCon("LOTES_CREAR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":501}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
