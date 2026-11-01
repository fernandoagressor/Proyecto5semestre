package com.inmobiliaria.inmobiliaria_api.controller.administracion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inmobiliaria.inmobiliaria_api.controller.SeguridadWebTest;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.administracion.SeguridadEmpresaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Usuarios y roles de empresa: cada acción exige su permiso y usa la empresa del token. */
@WebMvcTest(SeguridadEmpresaController.class)
class SeguridadEmpresaControllerSeguridadTest extends SeguridadWebTest {

    private static final String ROL = """
            {"codigo":"COORDINADOR","nombre":"Coordinador","descripcion":null,"activo":true,"permisoIds":[1,2]}
            """;

    @MockitoBean
    private SeguridadEmpresaService service;

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/v1/seguridad/usuarios")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void listarUsuariosExigeUsuariosVer() throws Exception {
        mockMvc.perform(get("/api/v1/seguridad/usuarios").with(usuarioCon("ROLES_VER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/seguridad/usuarios").with(usuarioCon("USUARIOS_VER")))
                .andExpect(status().isOk());

        verify(service).listarUsuarios(eq(new ContextoUsuario(USUARIO_ID, EMPRESA_ID)), eq(null), eq(null));
    }

    @Test
    void crearRolExigeRolesCrear() throws Exception {
        mockMvc.perform(post("/api/v1/seguridad/roles").with(usuarioCon("ROLES_VER", "ROLES_EDITAR"))
                        .contentType(MediaType.APPLICATION_JSON).content(ROL))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/seguridad/roles").with(usuarioCon("ROLES_CREAR"))
                        .contentType(MediaType.APPLICATION_JSON).content(ROL))
                .andExpect(status().isCreated());

        verify(service).crearRol(eq(new ContextoUsuario(USUARIO_ID, EMPRESA_ID)), any());
    }

    @Test
    void editarRolExigeRolesEditar() throws Exception {
        mockMvc.perform(put("/api/v1/seguridad/roles/9").with(usuarioCon("ROLES_CREAR"))
                        .contentType(MediaType.APPLICATION_JSON).content(ROL))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void conContrasenaTemporalPendienteResponde403() throws Exception {
        mockMvc.perform(get("/api/v1/seguridad/roles").with(usuarioConContrasenaTemporal("ROLES_VER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
