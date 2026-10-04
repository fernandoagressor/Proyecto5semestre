package com.inmobiliaria.inmobiliaria_api.dto.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.CitaServicio;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.EstadoCitaServicio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class CitaServicioDtos {
    private CitaServicioDtos() { }

    public record CrearRequest(Long proyectoId, Long clienteId, Long solicitudId,
                               Long responsableId, @NotBlank @Size(max = 30) String tipo,
                               @NotNull LocalDateTime fechaInicio,
                               @NotNull LocalDateTime fechaFin,
                               @Size(max = 300) String lugar) { }

    public record ReprogramarRequest(@NotNull LocalDateTime fechaInicio,
                                     @NotNull LocalDateTime fechaFin,
                                     @Size(max = 300) String lugar) { }

    public record EstadoRequest(@NotNull EstadoCitaServicio estado,
                                @Size(max = 5000) String resultado) { }

    public record Response(Long id, Long proyectoId, Long clienteId, Long solicitudId,
                           Long responsableId, String tipo, LocalDateTime fechaInicio,
                           LocalDateTime fechaFin, String lugar, EstadoCitaServicio estado,
                           String resultado, LocalDateTime creadoEn) {
        public static Response from(CitaServicio c) {
            return new Response(c.getId(), c.getProyectoId(), c.getClienteId(), c.getSolicitudId(),
                    c.getResponsableId(), c.getTipo(), c.getFechaInicio(), c.getFechaFin(),
                    c.getLugar(), c.getEstado(), c.getResultado(), c.getCreadoEn());
        }
    }
}
