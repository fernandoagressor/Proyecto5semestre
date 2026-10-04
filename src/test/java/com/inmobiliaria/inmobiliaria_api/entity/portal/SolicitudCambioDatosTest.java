package com.inmobiliaria.inmobiliaria_api.entity.portal;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SolicitudCambioDatosTest {
    @Test
    void exigeUnRevisorIndependiente() {
        SolicitudCambioDatos solicitud = nueva();
        assertThrows(ReglaNegocioException.class,
                () -> solicitud.aprobar(8L, null, LocalDateTime.now()));
        solicitud.aprobar(9L, "Validado", LocalDateTime.now());
        assertEquals("APROBADA", solicitud.getEstado());
    }

    @Test
    void unaSolicitudRevisadaNoPuedeRevisarseDeNuevo() {
        SolicitudCambioDatos solicitud = nueva();
        solicitud.rechazar(9L, "Soporte insuficiente", LocalDateTime.now());
        assertThrows(ReglaNegocioException.class,
                () -> solicitud.aprobar(10L, null, LocalDateTime.now()));
    }

    private static SolicitudCambioDatos nueva() {
        return new SolicitudCambioDatos(1L, 2L, 8L, "CORREO",
                "anterior@correo.com", "nuevo@correo.com", "Actualización");
    }
}
