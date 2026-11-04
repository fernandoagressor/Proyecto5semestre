package com.inmobiliaria.inmobiliaria_api.controller.inventario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inmobiliaria.inmobiliaria_api.controller.SeguridadWebTest;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AvanceObraService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Ver y registrar avances son permisos distintos; todo usa la empresa del token. */
@WebMvcTest(AvanceObraController.class)
class AvanceObraControllerSeguridadTest extends SeguridadWebTest {

    private static final String URL = "/api/v1/proyectos/77/avances";
    private static final String AVANCE = """
            {"fecha":"2026-11-03","porcentaje":20,"descripcion":"Movimiento de tierras"}
            """;

    @MockitoBean
    private AvanceObraService service;

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void verNoAlcanzaParaRegistrar() throws Exception {
        mockMvc.perform(post(URL).with(usuarioCon("AVANCES_OBRA_VER"))
                        .contentType(MediaType.APPLICATION_JSON).content(AVANCE))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void registraConSuPermisoYLaEmpresaDelToken() throws Exception {
        mockMvc.perform(post(URL).with(usuarioCon("AVANCES_OBRA_REGISTRAR"))
                        .contentType(MediaType.APPLICATION_JSON).content(AVANCE))
                .andExpect(status().isCreated());
        verify(service).registrar(eq(new ContextoUsuario(USUARIO_ID, EMPRESA_ID)), eq(77L), any());
    }

    @Test
    void rechazaUnPorcentajeMayorACien() throws Exception {
        mockMvc.perform(post(URL).with(usuarioCon("AVANCES_OBRA_REGISTRAR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AVANCE.replace("\"porcentaje\":20", "\"porcentaje\":150")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
