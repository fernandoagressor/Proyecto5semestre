package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SolicitudPqrsTest {
    @Test
    void exigeResolucionAntesDelCierre() {
        SolicitudPqrs solicitud = nueva();
        assertThrows(ReglaNegocioException.class,
                () -> solicitud.cambiarEstado(EstadoSolicitud.CERRADA, LocalDateTime.now()));
    }

    @Test
    void permiteResolverCerrarYReabrirConTrazabilidadTemporal() {
        SolicitudPqrs solicitud = nueva();
        LocalDateTime ahora = LocalDateTime.now();
        solicitud.cambiarEstado(EstadoSolicitud.EN_PROCESO, ahora);
        solicitud.cambiarEstado(EstadoSolicitud.RESUELTA, ahora.plusHours(1));
        solicitud.cambiarEstado(EstadoSolicitud.CERRADA, ahora.plusHours(2));
        assertEquals(EstadoSolicitud.CERRADA, solicitud.getEstado());
        solicitud.cambiarEstado(EstadoSolicitud.REABIERTA, ahora.plusHours(3));
        assertEquals(EstadoSolicitud.REABIERTA, solicitud.getEstado());
    }

    private static SolicitudPqrs nueva() {
        return new SolicitudPqrs(1L, 2L, "PQRS-1", 3L, null,
                TipoSolicitud.PETICION, "INFORMACION", "Asunto", "Detalle",
                PrioridadServicio.MEDIA, LocalDateTime.now().plusDays(3));
    }
}
