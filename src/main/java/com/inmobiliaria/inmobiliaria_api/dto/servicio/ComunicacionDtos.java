package com.inmobiliaria.inmobiliaria_api.dto.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.ComunicacionCliente;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class ComunicacionDtos {
    private ComunicacionDtos() { }

    public record CrearRequest(Long clienteId, @NotBlank @Size(max = 20) String canal,
                               @Size(max = 200) String asunto,
                               @NotBlank @Size(max = 20000) String contenido,
                               LocalDateTime programadaPara) { }

    public record Response(Long id, Long clienteId, Long enviadoPor, String canal,
                           String asunto, String contenido, boolean masiva, String estado,
                           LocalDateTime programadaPara, LocalDateTime enviadaEn,
                           LocalDateTime creadoEn) {
        public static Response from(ComunicacionCliente c) {
            return new Response(c.getId(), c.getClienteId(), c.getEnviadoPor(), c.getCanal(),
                    c.getAsunto(), c.getContenido(), c.isMasiva(), c.getEstado(),
                    c.getProgramadaPara(), c.getEnviadaEn(), c.getCreadoEn());
        }
    }
}
