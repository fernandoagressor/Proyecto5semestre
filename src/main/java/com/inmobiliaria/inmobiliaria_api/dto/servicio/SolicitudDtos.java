package com.inmobiliaria.inmobiliaria_api.dto.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.EstadoSolicitud;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.PrioridadServicio;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudAdjunto;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudHistorial;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudMensaje;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudPqrs;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.TipoSolicitud;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class SolicitudDtos {
    private SolicitudDtos() { }

    public record CrearRequest(Long proyectoId, Long clienteId, Long contratoId,
                               @NotNull TipoSolicitud tipo,
                               @NotBlank @Size(max = 60) String categoria,
                               @NotBlank @Size(max = 200) String asunto,
                               @NotBlank @Size(max = 10000) String descripcion,
                               PrioridadServicio prioridad) { }

    public record MensajeRequest(@NotBlank @Size(max = 10000) String mensaje,
                                  boolean interno, @Size(max = 20) String canal) { }

    public record AsignarRequest(@NotNull Long usuarioId, @Size(max = 500) String motivo) { }

    public record EstadoRequest(@NotNull EstadoSolicitud estado, @Size(max = 500) String motivo) { }

    public record CerrarRequest(@Size(max = 500) String motivo) { }

    public record AdjuntoRequest(@NotNull Long archivoId, Long mensajeId) { }

    public record EncuestaRequest(@Min(1) @Max(5) short puntuacion,
                                  @Size(max = 3000) String comentario) { }

    public record Response(Long id, String codigo, Long proyectoId, Long clienteId,
                           Long contratoId, Long asignadoA, TipoSolicitud tipo,
                           String categoria, String asunto, String descripcion,
                           PrioridadServicio prioridad, EstadoSolicitud estado,
                           LocalDateTime fechaLimite, LocalDateTime fechaPrimeraRespuesta,
                           LocalDateTime fechaResolucion, LocalDateTime fechaCierre,
                           LocalDateTime creadoEn, LocalDateTime actualizadoEn) {
        public static Response from(SolicitudPqrs s) {
            return new Response(s.getId(), s.getCodigo(), s.getProyectoId(), s.getClienteId(),
                    s.getContratoId(), s.getAsignadoA(), s.getTipo(), s.getCategoria(), s.getAsunto(),
                    s.getDescripcion(), s.getPrioridad(), s.getEstado(), s.getFechaLimite(),
                    s.getFechaPrimeraRespuesta(), s.getFechaResolucion(), s.getFechaCierre(),
                    s.getCreadoEn(), s.getActualizadoEn());
        }
    }

    public record MensajeResponse(Long id, Long usuarioId, String mensaje, boolean interno,
                                  String canal, LocalDateTime creadoEn) {
        public static MensajeResponse from(SolicitudMensaje m) {
            return new MensajeResponse(m.getId(), m.getUsuarioId(), m.getMensaje(),
                    m.isInterno(), m.getCanal(), m.getCreadoEn());
        }
    }

    public record HistorialResponse(Long id, String campo, String valorAnterior,
                                    String valorNuevo, Long usuarioId, String motivo,
                                    LocalDateTime creadoEn) {
        public static HistorialResponse from(SolicitudHistorial h) {
            return new HistorialResponse(h.getId(), h.getCampo(), h.getValorAnterior(),
                    h.getValorNuevo(), h.getUsuarioId(), h.getMotivo(), h.getCreadoEn());
        }
    }

    public record AdjuntoResponse(Long id, Long mensajeId, Long archivoId,
                                  String nombreArchivo, String mimeType,
                                  Long tamanoBytes, LocalDateTime creadoEn) {
        public static AdjuntoResponse from(SolicitudAdjunto a) {
            return new AdjuntoResponse(a.getId(), a.getMensajeId(), a.getArchivoId(),
                    null, null, null, a.getCreadoEn());
        }
    }
}
