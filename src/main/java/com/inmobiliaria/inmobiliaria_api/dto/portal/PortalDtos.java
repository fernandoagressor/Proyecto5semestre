package com.inmobiliaria.inmobiliaria_api.dto.portal;

import com.inmobiliaria.inmobiliaria_api.entity.portal.SolicitudCambioDatos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PortalDtos {
    private PortalDtos() { }

    public record Resumen(Long clienteId, String codigoCliente, long contratosVigentes,
                          long cuotasPendientes, BigDecimal saldoPendiente,
                          long documentosDisponibles, long solicitudesAbiertas,
                          LocalDateTime proximaCita) { }

    public record Documento(Long id, String codigo, String tipoDocumento, String nombre,
                            Long proyectoId, String entidadTipo, Long entidadId,
                            Integer numeroVersion, Long archivoId, String nombreArchivo,
                            String mimeType, Long tamanoBytes, String sha256,
                            LocalDateTime actualizadoEn) { }

    public record CambioCrearRequest(@NotBlank @Size(max = 60) String campo,
                                     @NotBlank @Size(max = 500) String valorSolicitado,
                                     @NotBlank @Size(max = 500) String motivo) { }
    public record CambioRevisarRequest(@Size(max = 500) String comentario) { }

    public record CambioResponse(Long id, Long clienteId, Long solicitadoPor, String campo,
                                 String valorAnterior, String valorSolicitado, String motivo,
                                 String estado, Long revisadoPor, LocalDateTime revisadoEn,
                                 String comentarioRevision, LocalDateTime creadoEn) {
        public static CambioResponse from(SolicitudCambioDatos s) {
            return new CambioResponse(s.getId(), s.getClienteId(), s.getSolicitadoPor(), s.getCampo(),
                    s.getValorAnterior(), s.getValorSolicitado(), s.getMotivo(), s.getEstado(),
                    s.getRevisadoPor(), s.getRevisadoEn(), s.getComentarioRevision(), s.getCreadoEn());
        }
    }
}
