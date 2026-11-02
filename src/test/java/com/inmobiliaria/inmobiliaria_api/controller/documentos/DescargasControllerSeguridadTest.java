package com.inmobiliaria.inmobiliaria_api.controller.documentos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inmobiliaria.inmobiliaria_api.controller.SeguridadWebTest;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.documentos.DocumentosContratoService;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaExportacionService;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.ReporteExportacionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Cada descarga exige su permiso, usa la empresa del token y sale como adjunto sin caché. */
@WebMvcTest(DescargasController.class)
class DescargasControllerSeguridadTest extends SeguridadWebTest {

    private static final ContextoUsuario SESION = new ContextoUsuario(USUARIO_ID, EMPRESA_ID);

    @MockitoBean
    private ReporteExportacionService reportes;
    @MockitoBean
    private AuditoriaExportacionService auditoria;
    @MockitoBean
    private DocumentosContratoService documentos;

    @Test
    void reporteExigeExportarYSaleComoAdjunto() throws Exception {
        mockMvc.perform(get("/api/v1/exportaciones/reportes/ventas").with(usuarioCon("REPORTES_VER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportes);
        when(reportes.exportar(eq(SESION), eq("ventas"), eq("XLSX"), isNull(), isNull(), isNull()))
                .thenReturn(new ReporteExportacionService.Archivo("ventas.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1}));
        mockMvc.perform(get("/api/v1/exportaciones/reportes/ventas?formato=XLSX").with(usuarioCon("REPORTES_EXPORTAR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void documentosDelContratoExigenSuPermiso() throws Exception {
        mockMvc.perform(get("/api/v1/contratos/5/documentos/paz-y-salvo").with(usuarioCon("ESTADOS_CUENTA_GENERAR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/contratos/5/documentos/cobro-mora").with(usuarioCon("MORA_VER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/exportaciones/auditoria").with(usuarioCon("AUDITORIA_VER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(documentos, auditoria);
        when(documentos.estadoCuenta(SESION, 5L)).thenReturn(new DocumentosContratoService.Pdf("e.pdf", new byte[] {1}));
        mockMvc.perform(get("/api/v1/contratos/5/documentos/estado-cuenta").with(usuarioCon("ESTADOS_CUENTA_GENERAR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void sinSesionResponde401() throws Exception {
        mockMvc.perform(get("/api/v1/portal/contratos/5/estado-cuenta")).andExpect(status().isUnauthorized());
        verifyNoInteractions(documentos);
    }
}
