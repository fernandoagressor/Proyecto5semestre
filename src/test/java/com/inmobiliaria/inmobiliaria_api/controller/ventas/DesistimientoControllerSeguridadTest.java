package com.inmobiliaria.inmobiliaria_api.controller.ventas;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inmobiliaria.inmobiliaria_api.controller.SeguridadWebTest;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.ventas.desistimiento.DesistimientoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Solicitar y aprobar un desistimiento son permisos distintos; todo usa la empresa del token. */
@WebMvcTest(DesistimientoController.class)
class DesistimientoControllerSeguridadTest extends SeguridadWebTest {

    private static final ContextoUsuario SESION = new ContextoUsuario(USUARIO_ID, EMPRESA_ID);
    private static final String SOLICITUD = """
            {"contratoId":5,"origen":"CLIENTE","motivo":"Cambio de ciudad","porcentajePenalidad":10}
            """;

    @MockitoBean
    private DesistimientoService service;

    @Test
    void sinPermisoNoSeSolicita() throws Exception {
        mockMvc.perform(post("/api/v1/desistimientos").with(usuarioCon("DESISTIMIENTOS_VER", "DESISTIMIENTOS_APROBAR"))
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mockMvc.perform(post("/api/v1/desistimientos").with(usuarioCon("DESISTIMIENTOS_SOLICITAR"))
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isCreated());
        verify(service).solicitar(eq(SESION), any());
    }

    @Test
    void aprobarExigeSuPermiso() throws Exception {
        mockMvc.perform(patch("/api/v1/desistimientos/3/aprobar").with(usuarioCon("DESISTIMIENTOS_SOLICITAR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/desistimientos/3/aprobar").with(usuarioCon("DESISTIMIENTOS_APROBAR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        verify(service).aprobar(eq(SESION), eq(3L), any());
    }

    @Test
    void validaLaSolicitud() throws Exception {
        mockMvc.perform(post("/api/v1/desistimientos").with(usuarioCon("DESISTIMIENTOS_SOLICITAR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SOLICITUD.replace("\"porcentajePenalidad\":10", "\"porcentajePenalidad\":150")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void elActaExigeVer() throws Exception {
        mockMvc.perform(get("/api/v1/desistimientos/3/acta")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/desistimientos/3/acta").with(usuarioCon("CONTRATOS_VER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
