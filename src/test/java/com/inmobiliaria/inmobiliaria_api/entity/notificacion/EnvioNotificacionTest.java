package com.inmobiliaria.inmobiliaria_api.entity.notificacion;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvioNotificacionTest {
    @Test
    void noConfirmaEntregaAntesDeQueElProveedorAcepteElEnvio() {
        EnvioNotificacion envio = new EnvioNotificacion(1L, null, "EMAIL", "cliente@correo.com", LocalDateTime.now());
        assertThrows(ReglaNegocioException.class, () -> envio.confirmarEntrega(LocalDateTime.now()));
        envio.registrarEnvio("proveedor", "ref-1", LocalDateTime.now());
        envio.confirmarEntrega(LocalDateTime.now());
        assertEquals("ENTREGADO", envio.getEstado());
        assertEquals(1, envio.getIntentos());
    }
}
