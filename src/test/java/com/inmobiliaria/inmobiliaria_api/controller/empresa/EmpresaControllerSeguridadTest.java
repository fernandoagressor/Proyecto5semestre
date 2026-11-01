package com.inmobiliaria.inmobiliaria_api.controller.empresa;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inmobiliaria.inmobiliaria_api.controller.SeguridadWebTest;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.empresa.DirectorioUsuariosService;
import com.inmobiliaria.inmobiliaria_api.service.empresa.EmpresaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** El directorio de usuarios exige sesión y siempre usa la empresa del token. */
@WebMvcTest(EmpresaController.class)
class EmpresaControllerSeguridadTest extends SeguridadWebTest {

    private static final String DIRECTORIO = "/api/v1/empresas/actual/usuarios";

    @MockitoBean
    private EmpresaService empresaService;

    @MockitoBean
    private DirectorioUsuariosService directorioUsuarios;

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get(DIRECTORIO)).andExpect(status().isUnauthorized());

        verifyNoInteractions(directorioUsuarios);
    }

    @Test
    void conSesionRespondeConElEquipoDeSuEmpresa() throws Exception {
        mockMvc.perform(get(DIRECTORIO).with(usuarioCon("DASHBOARD_VER")))
                .andExpect(status().isOk());

        verify(directorioUsuarios).listar(eq(new ContextoUsuario(USUARIO_ID, EMPRESA_ID)));
    }

    @Test
    void conContrasenaTemporalPendienteResponde403() throws Exception {
        mockMvc.perform(get(DIRECTORIO).with(usuarioConContrasenaTemporal("DASHBOARD_VER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(directorioUsuarios);
    }
}
