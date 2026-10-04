package com.inmobiliaria.inmobiliaria_api.dto.notificacion;

import com.inmobiliaria.inmobiliaria_api.entity.notificacion.Notificacion;
import java.time.LocalDateTime;

public final class NotificacionDtos {
    private NotificacionDtos() { }

    public record Response(Long id, String tipo, String titulo, String mensaje,
                           String entidadTipo, Long entidadId, String prioridad,
                           LocalDateTime leidaEn, LocalDateTime expiraEn,
                           LocalDateTime creadoEn) {
        public static Response from(Notificacion n) {
            return new Response(n.getId(), n.getTipo(), n.getTitulo(), n.getMensaje(),
                    n.getEntidadTipo(), n.getEntidadId(), n.getPrioridad(), n.getLeidaEn(),
                    n.getExpiraEn(), n.getCreadoEn());
        }
    }

    public record Resumen(long noLeidas) { }
}
