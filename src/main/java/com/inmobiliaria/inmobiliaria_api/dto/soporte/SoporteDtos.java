package com.inmobiliaria.inmobiliaria_api.dto.soporte;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.PrioridadServicio;
import com.inmobiliaria.inmobiliaria_api.entity.soporte.EstadoTicket;
import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketAdjunto;
import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketHistorial;
import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketMensaje;
import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketSoporte;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class SoporteDtos {
    private SoporteDtos() { }

    public record CrearRequest(Long proyectoId,
                               @NotBlank @Size(max = 200) String asunto,
                               @NotBlank @Size(max = 60) String categoria,
                               PrioridadServicio prioridad,
                               @NotBlank @Size(max = 10000) String descripcion) { }
    public record MensajeRequest(@NotBlank @Size(max = 10000) String mensaje, boolean interno) { }
    public record AsignarRequest(@NotNull Long usuarioId, @Size(max = 500) String motivo) { }
    public record EstadoRequest(@NotNull EstadoTicket estado, @Size(max = 500) String motivo) { }
    public record AdjuntoRequest(@NotNull Long archivoId, Long mensajeId) { }

    public record Response(Long id, Long proyectoId, String codigo, Long solicitanteId,
                           Long asignadoA, String asunto, String categoria,
                           PrioridadServicio prioridad, String descripcion, EstadoTicket estado,
                           LocalDateTime fechaPrimeraRespuesta, LocalDateTime fechaResolucion,
                           LocalDateTime fechaCierre, LocalDateTime creadoEn) {
        public static Response from(TicketSoporte t) {
            return new Response(t.getId(), t.getProyectoId(), t.getCodigo(), t.getSolicitanteId(),
                    t.getAsignadoA(), t.getAsunto(), t.getCategoria(), t.getPrioridad(),
                    t.getDescripcion(), t.getEstado(), t.getFechaPrimeraRespuesta(),
                    t.getFechaResolucion(), t.getFechaCierre(), t.getCreadoEn());
        }
    }
    public record MensajeResponse(Long id, Long usuarioId, String mensaje, boolean interno, LocalDateTime creadoEn) {
        public static MensajeResponse from(TicketMensaje m) {
            return new MensajeResponse(m.getId(), m.getUsuarioId(), m.getMensaje(), m.isInterno(), m.getCreadoEn());
        }
    }
    public record AdjuntoResponse(Long id, Long mensajeId, Long archivoId, LocalDateTime creadoEn) {
        public static AdjuntoResponse from(TicketAdjunto a) {
            return new AdjuntoResponse(a.getId(), a.getMensajeId(), a.getArchivoId(), a.getCreadoEn());
        }
    }
    public record HistorialResponse(Long id, String campo, String valorAnterior, String valorNuevo,
                                    Long usuarioId, String motivo, LocalDateTime creadoEn) {
        public static HistorialResponse from(TicketHistorial h) {
            return new HistorialResponse(h.getId(), h.getCampo(), h.getValorAnterior(), h.getValorNuevo(),
                    h.getUsuarioId(), h.getMotivo(), h.getCreadoEn());
        }
    }
}
