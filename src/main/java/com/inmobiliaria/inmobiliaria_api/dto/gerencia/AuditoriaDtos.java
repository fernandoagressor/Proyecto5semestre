package com.inmobiliaria.inmobiliaria_api.dto.gerencia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;

public final class AuditoriaDtos {
    private AuditoriaDtos() { }

    public record EventoResponse(Long id, Long empresaId, Long proyectoId, Long actorId,
                                 String modulo, String accion, String entidadTipo,
                                 Long entidadId, String descripcion,
                                 String datosAnterioresJson, String datosNuevosJson,
                                 String ipOrigen, String agenteUsuario,
                                 String correlationId, LocalDateTime ocurridoEn) { }

    public record AlertaResponse(Long id, String tipo, String severidad, Long usuarioId,
                                 Long eventoAuditoriaId, String descripcion, String estado,
                                 Long asignadoA, LocalDateTime resueltaEn, String resolucion,
                                 LocalDateTime creadoEn, long version) { }

    public record AsignarAlertaRequest(@NotNull Long usuarioId) { }
    public record ResolverAlertaRequest(@NotBlank @Size(max = 5000) String resolucion,
                                        boolean descartar) { }

    public record ExportarRequest(@NotBlank @Size(max = 500) String motivo,
                                  @NotBlank String formato,
                                  Map<String, Object> filtros) { }

    public record ExportacionResponse(Long id, String formato, String motivo,
                                      String estado, Long archivoId,
                                      LocalDateTime expiraEn, LocalDateTime creadoEn) { }
}
