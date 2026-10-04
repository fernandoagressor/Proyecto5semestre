package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComunicacionClienteTest {
    @Test
    void comunicacionMasivaNoAceptaClienteIndividual() {
        assertThrows(ReglaNegocioException.class, () -> new ComunicacionCliente(
                1L, 2L, 3L, "EMAIL", "Aviso", "Contenido", true, null));
    }

    @Test
    void registraConfirmacionDeEnvioSinConfundirlaConBorrador() {
        ComunicacionCliente c = new ComunicacionCliente(1L, 2L, 3L,
                "EMAIL", "Aviso", "Contenido", false, null);
        c.confirmarEnvio(java.time.LocalDateTime.now());
        assertEquals("ENVIADA", c.getEstado());
    }
}
