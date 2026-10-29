package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void elIdentificadorSoloSeAsignaUnaVez() {
        SolicitudPqrs solicitud = nueva();
        solicitud.identificar(15L);
        assertEquals(15L, solicitud.getId());
        assertThrows(IllegalStateException.class, () -> solicitud.identificar(16L));
    }

    @Test
    void elClienteNoVeLasNotasInternas() {
        SolicitudPqrs solicitud = nueva();
        solicitud.agregarMensaje(1L, 3L, "Necesito ayuda", false, "PORTAL");
        solicitud.agregarMensaje(2L, 9L, "Revisar con obra", true, "INTERNO");

        List<SolicitudMensaje> paraCliente = solicitud.mensajes(false);
        List<SolicitudMensaje> paraAgente = solicitud.mensajes(true);

        assertEquals(List.of(1L), paraCliente.stream().map(SolicitudMensaje::getId).toList());
        assertEquals(List.of(1L, 2L), paraAgente.stream().map(SolicitudMensaje::getId).toList());
    }

    @Test
    void unAdjuntoDebeSerDeUnMensajeDeLaSolicitudYNoRepetirse() {
        SolicitudPqrs solicitud = nueva();
        solicitud.agregarMensaje(1L, 3L, "Foto del daño", false, "PORTAL");

        assertThrows(ReglaNegocioException.class, () -> solicitud.adjuntar(10L, 99L, 500L));
        solicitud.adjuntar(10L, 1L, 500L);
        assertTrue(solicitud.tieneArchivo(500L));
        assertThrows(ReglaNegocioException.class, () -> solicitud.adjuntar(11L, null, 500L));
        assertEquals(1, solicitud.getAdjuntos().size());
    }

    @Test
    void laEncuestaExigeResolucionYSeRespondeUnaSolaVez() {
        SolicitudPqrs solicitud = nueva();
        LocalDateTime ahora = LocalDateTime.now();
        assertThrows(ReglaNegocioException.class, () -> solicitud.calificar(3L, 5, null, ahora));

        solicitud.cambiarEstado(EstadoSolicitud.EN_PROCESO, ahora);
        solicitud.cambiarEstado(EstadoSolicitud.RESUELTA, ahora);
        assertFalse(solicitud.estaCalificada());
        assertThrows(ReglaNegocioException.class, () -> solicitud.calificar(3L, 6, null, ahora));
        solicitud.calificar(3L, 4, "Buena atención", ahora);

        assertEquals(4, solicitud.getEncuesta().getPuntuacion());
        assertThrows(ReglaNegocioException.class, () -> solicitud.calificar(3L, 5, null, ahora));
    }

    @Test
    void elHistorialQuedaEnOrdenCronologico() {
        SolicitudPqrs solicitud = nueva();
        solicitud.registrarCambio(1L, "estado", "ABIERTA", "EN_PROCESO", 9L, null);
        solicitud.registrarCambio(2L, "asignado_a", null, "9", 9L, "Turno");

        assertEquals(List.of("estado", "asignado_a"),
                solicitud.getHistorial().stream().map(SolicitudHistorial::getCampo).toList());
    }

    private static SolicitudPqrs nueva() {
        return new SolicitudPqrs(1L, 2L, "PQRS-1", 3L, null,
                TipoSolicitud.PETICION, "INFORMACION", "Asunto", "Detalle",
                PrioridadServicio.MEDIA, LocalDateTime.now().plusDays(3));
    }
}
