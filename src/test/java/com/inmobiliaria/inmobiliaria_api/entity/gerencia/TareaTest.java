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

    @Test
    void elHistorialQuedaEmbebidoYElIdSoloSeAsignaUnaVez() {
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 29, 10, 0);
        Tarea tarea = new Tarea(1L, null, 3L, 4L, "Revisar planos", null,
                "REVISION", null, null, null, null, null);
        tarea.identificar(30L);
        assertThrows(IllegalStateException.class, () -> tarea.identificar(31L));

        tarea.registrarHistorial(300L, null, "PENDIENTE", 4L, "  Tarea creada ", ahora);
        tarea.registrarHistorial(301L, "PENDIENTE", "EN_PROCESO", 4L, " ", ahora);

        assertEquals(2, tarea.getHistorial().size());
        assertEquals(30L, tarea.getHistorial().get(0).getTareaId());
        assertEquals("Tarea creada", tarea.getHistorial().get(0).getComentario());
        assertEquals(null, tarea.getHistorial().get(1).getComentario());
        assertEquals(PrioridadTarea.MEDIA, tarea.getPrioridad());
        assertThrows(UnsupportedOperationException.class, () -> tarea.getHistorial().clear());
        assertEquals(false, tarea.marcarVencida(ahora), "sin fecha de vencimiento no vence");
    }
}
