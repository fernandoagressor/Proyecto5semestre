package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CitaServicioTest {
    @Test
    void noAceptaUnRangoInvertido() {
        LocalDateTime inicio = LocalDateTime.now().plusDays(1);
        assertThrows(ReglaNegocioException.class, () -> new CitaServicio(
                1L, null, 2L, null, 3L, "VISITA", inicio, inicio.minusHours(1), null));
    }

    @Test
    void unaCitaRealizadaNoPuedeReprogramarse() {
        LocalDateTime inicio = LocalDateTime.now().plusDays(1);
        CitaServicio cita = new CitaServicio(1L, null, 2L, null, 3L,
                "VISITA", inicio, inicio.plusHours(1), "Sala");
        cita.confirmar();
        cita.completar("Atención completada");
        assertEquals(EstadoCitaServicio.REALIZADA, cita.getEstado());
        assertThrows(ReglaNegocioException.class,
                () -> cita.reprogramar(inicio.plusDays(1), inicio.plusDays(1).plusHours(1), "Sala"));
    }
}
