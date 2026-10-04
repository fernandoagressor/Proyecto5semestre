package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TareaTest {
    @Test
    void vencimientoYFinalizacionSonTrazables() {
        LocalDateTime ahora = LocalDateTime.of(2026, 7, 31, 10, 0);
        Tarea tarea = new Tarea(1L, 2L, 3L, 4L, "Llamar cliente", null,
                "SEGUIMIENTO", PrioridadTarea.ALTA, ahora.minusHours(1), null, null, null);
        tarea.marcarVencida(ahora);
        assertEquals(EstadoTarea.VENCIDA, tarea.getEstado());
        tarea.cambiarEstado(EstadoTarea.COMPLETADA, ahora);
        assertThrows(ReglaNegocioException.class, () -> tarea.reasignar(5L));
    }
}
