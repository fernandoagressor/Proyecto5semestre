package com.inmobiliaria.inmobiliaria_api.controller.catalogo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inmobiliaria.inmobiliaria_api.controller.SeguridadWebTest;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.catalogo.CatalogoEmpresaService;
import com.inmobiliaria.inmobiliaria_api.service.catalogo.CatalogoPublicoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** El catálogo se lee y se contacta sin sesión; publicar y la galería exigen permisos. */
@WebMvcTest({CatalogoPublicoController.class, CatalogoEmpresaController.class})
class CatalogoControllersSeguridadTest extends SeguridadWebTest {

    private static final String CONTACTO = """
            {"proyectoId":1,"nombres":"Ana","telefono":"3001234567","correo":"ana@ejemplo.com",
             "mensaje":"Hola","aceptaTratamiento":true,"sitioWeb":""}
            """;

    @MockitoBean
    private CatalogoPublicoService publico;

    @MockitoBean
    private CatalogoEmpresaService empresa;

    @Test
    void elCatalogoSeLeeSinSesion() throws Exception {
        mockMvc.perform(get("/api/v1/publico/catalogo/EMP001")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/publico/catalogo/EMP001/proyectos/3")).andExpect(status().isOk());
        verify(publico).empresa("EMP001");
        verify(publico).proyecto("EMP001", 3L);
    }

    @Test
    void elContactoSeEnviaSinSesion() throws Exception {
        mockMvc.perform(post("/api/v1/publico/catalogo/EMP001/contacto")
                        .contentType(MediaType.APPLICATION_JSON).content(CONTACTO))
                .andExpect(status().isAccepted());
        verify(publico).contactar(eq("EMP001"), any(), anyString());
    }

    @Test
    void elContactoExigeAutorizacionDeDatosYRechazaRobots() throws Exception {
        mockMvc.perform(post("/api/v1/publico/catalogo/EMP001/contacto").contentType(MediaType.APPLICATION_JSON)
                        .content(CONTACTO.replace("\"aceptaTratamiento\":true", "\"aceptaTratamiento\":false")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/publico/catalogo/EMP001/contacto").contentType(MediaType.APPLICATION_JSON)
                        .content(CONTACTO.replace("\"sitioWeb\":\"\"", "\"sitioWeb\":\"http://spam\"")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(publico);
    }

    @Test
    void publicarElCatalogoExigeConfigurarLaEmpresa() throws Exception {
        mockMvc.perform(put("/api/v1/empresas/actual/catalogo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publico\":true}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/empresas/actual/catalogo").with(usuarioCon("PROYECTOS_EDITAR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"publico\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/empresas/actual/catalogo").with(usuarioCon("CONFIGURACION_EMPRESA_EDITAR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"publico\":true}"))
                .andExpect(status().isOk());
        verify(empresa).cambiar(new ContextoUsuario(USUARIO_ID, EMPRESA_ID), true);
    }

    @Test
    void laGaleriaExigeEditarProyectos() throws Exception {
        String foto = "{\"url\":\"https://ejemplo.com/a.jpg\",\"titulo\":\"Entrada\"}";
        mockMvc.perform(post("/api/v1/proyectos/3/imagenes").with(usuarioCon("PROYECTOS_VER"))
                        .contentType(MediaType.APPLICATION_JSON).content(foto))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/proyectos/3/imagenes").with(usuarioCon("PROYECTOS_EDITAR"))
                        .contentType(MediaType.APPLICATION_JSON).content(foto.replace("https", "http")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/proyectos/3/imagenes").with(usuarioCon("PROYECTOS_EDITAR"))
                        .contentType(MediaType.APPLICATION_JSON).content(foto))
                .andExpect(status().isCreated());
    }
}
