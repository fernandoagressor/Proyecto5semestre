package com.inmobiliaria.inmobiliaria_api.controller.gerencia;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.AuditoriaDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaConsultaService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auditoria")
public class AuditoriaController {
    private final AuditoriaConsultaService service;
    public AuditoriaController(AuditoriaConsultaService service) { this.service = service; }

    @GetMapping("/eventos")
    @PreAuthorize("hasAuthority('AUDITORIA_VER')")
    public PaginaResponse<AuditoriaDtos.EventoResponse> eventos(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long proyectoId, @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String modulo, @RequestParam(required = false) String accion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.eventos(ContextoUsuario.empresa(jwt), proyectoId, actorId, modulo, accion,
                desde, hasta, pagina, tamano);
    }

    @GetMapping("/eventos/{id}")
    @PreAuthorize("hasAuthority('AUDITORIA_DETALLE_VER')")
    public AuditoriaDtos.EventoResponse evento(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.evento(ContextoUsuario.empresa(jwt), id);
    }

    @GetMapping("/alertas")
    @PreAuthorize("hasAuthority('ALERTAS_ANOMALIAS_VER')")
    public PaginaResponse<AuditoriaDtos.AlertaResponse> alertas(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String estado, @RequestParam(required = false) String severidad,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.alertas(ContextoUsuario.empresa(jwt), estado, severidad, pagina, tamano);
    }

    @PatchMapping("/alertas/{id}/asignar")
    @PreAuthorize("hasAuthority('AUDITORIA_ALERTAS_GESTIONAR')")
    public AuditoriaDtos.AlertaResponse asignar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody AuditoriaDtos.AsignarAlertaRequest request) {
        return service.asignar(ContextoUsuario.empresa(jwt), id, request);
    }

    @PatchMapping("/alertas/{id}/resolver")
    @PreAuthorize("hasAuthority('AUDITORIA_ALERTAS_GESTIONAR')")
    public AuditoriaDtos.AlertaResponse resolver(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody AuditoriaDtos.ResolverAlertaRequest request) {
        return service.resolver(ContextoUsuario.empresa(jwt), id, request);
    }

    @PostMapping("/exportaciones")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('AUDITORIA_EXPORTAR')")
    public AuditoriaDtos.ExportacionResponse exportar(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AuditoriaDtos.ExportarRequest request) {
        return service.solicitarExportacion(ContextoUsuario.empresa(jwt), request);
    }

    @GetMapping("/exportaciones/{id}")
    @PreAuthorize("hasAuthority('AUDITORIA_EXPORTAR')")
    public AuditoriaDtos.ExportacionResponse exportacion(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return service.exportacion(ContextoUsuario.empresa(jwt), id);
    }
}
