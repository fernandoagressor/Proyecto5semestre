package com.inmobiliaria.inmobiliaria_api.controller.administracion;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inmobiliaria.inmobiliaria_api.controller.SeguridadWebTest;
import com.inmobiliaria.inmobiliaria_api.service.administracion.AdministracionEmpresaService;
import com.inmobiliaria.inmobiliaria_api.service.administracion.AdministracionRolService;
import com.inmobiliaria.inmobiliaria_api.service.administracion.AdministracionUsuarioService;
import com.inmobiliaria.inmobiliaria_api.service.administracion.AnaliticaGlobalService;
import com.inmobiliaria.inmobiliaria_api.service.inventario.ProyectoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * La consola global (/api/v1/admin/**) solo es accesible para SUPER_ADMIN.
 * Un ADMINISTRADOR de empresa, aunque tenga muchos permisos, no debe entrar.
 */
@WebMvcTest(SuperAdministracionController.class)
class SuperAdministracionControllerSeguridadTest extends SeguridadWebTest {

    private static final String RESUMEN = "/api/v1/admin/resumen";

    @MockitoBean
    private AdministracionEmpresaService empresaService;

    @MockitoBean
    private AnaliticaGlobalService analiticaGlobalService;

    @MockitoBean
    private AdministracionUsuarioService usuarioService;

    @MockitoBean
    private AdministracionRolService rolService;

    @MockitoBean
    private ProyectoService proyectoService;

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get(RESUMEN))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(empresaService);
    }

    @Test
    void elAdministradorDeUnaEmpresaNoEntraALaConsolaGlobal() throws Exception {
        mockMvc.perform(get(RESUMEN).with(
                        usuarioCon("ROLE_ADMINISTRADOR", "USUARIOS_VER", "DASHBOARD_GERENCIAL_VER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(empresaService);
    }

    @Test
    void elSuperAdminSiEntraALaConsolaGlobal() throws Exception {
        mockMvc.perform(get(RESUMEN).with(usuarioCon("ROLE_SUPER_ADMIN")))
                .andExpect(status().isOk());

        verify(empresaService).resumen();
    }
}