package com.inmobiliaria.inmobiliaria_api.dto.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoTarea;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.PrioridadTarea;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.Tarea;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.TareaHistorial;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class TareaDtos {
    private TareaDtos() { }

    public record CrearRequest(Long proyectoId, @NotNull Long asignadoA,
                               @NotBlank @Size(max = 180) String titulo,
                               @Size(max = 10000) String descripcion,
                               @NotBlank @Size(max = 40) String tipo,
                               PrioridadTarea prioridad, LocalDateTime fechaVencimiento,
                               @Size(max = 50) String entidadTipo, Long entidadId,
                               @Size(max = 100) String reglaOrigen) { }

    public record EstadoRequest(@NotNull EstadoTarea estado,
                                @Size(max = 500) String comentario) { }

    public record ReasignarRequest(@NotNull Long usuarioId,
                                   @NotBlank @Size(max = 500) String motivo) { }

    public record Response(Long id, Long proyectoId, Long asignadoA, Long creadoPor,
                           String titulo, String descripcion, String tipo,
                           PrioridadTarea prioridad, EstadoTarea estado,
                           LocalDateTime fechaVencimiento, String entidadTipo,
                           Long entidadId, String reglaOrigen,
                           LocalDateTime completadaEn, LocalDateTime creadoEn,
                           Long version) {
        public static Response from(Tarea t) {
            return new Response(t.getId(), t.getProyectoId(), t.getAsignadoA(), t.getCreadoPor(),
                    t.getTitulo(), t.getDescripcion(), t.getTipo(), t.getPrioridad(), t.getEstado(),
                    t.getFechaVencimiento(), t.getEntidadTipo(), t.getEntidadId(), t.getReglaOrigen(),
                    t.getCompletadaEn(), t.getCreadoEn(), t.getVersion());
        }
    }

    public record HistorialResponse(Long id, String estadoAnterior, String estadoNuevo,
                                    Long usuarioId, String comentario, LocalDateTime creadoEn) {
        public static HistorialResponse from(TareaHistorial h) {
            return new HistorialResponse(h.getId(), h.getEstadoAnterior(), h.getEstadoNuevo(),
                    h.getUsuarioId(), h.getComentario(), h.getCreadoEn());
        }
    }
}
