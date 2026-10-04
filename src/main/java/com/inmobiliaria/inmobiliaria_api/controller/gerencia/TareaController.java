package com.inmobiliaria.inmobiliaria_api.controller.gerencia;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.TareaDtos;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoTarea;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.PrioridadTarea;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.TareaService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/tareas")
public class TareaController {
    private final TareaService service;
    public TareaController(TareaService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('TAREAS_VER')")
    public PaginaResponse<TareaDtos.Response> listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long proyectoId, @RequestParam(required = false) Long asignadoA,
            @RequestParam(required = false) EstadoTarea estado,
            @RequestParam(required = false) PrioridadTarea prioridad,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(ContextoUsuario.empresa(jwt), proyectoId, asignadoA, estado, prioridad, pagina, tamano);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TAREAS_GESTIONAR')")
    public TareaDtos.Response crear(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TareaDtos.CrearRequest request) {
        return service.crear(ContextoUsuario.empresa(jwt), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TAREAS_VER')")
    public TareaDtos.Response obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.obtener(ContextoUsuario.empresa(jwt), id);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('TAREAS_GESTIONAR')")
    public TareaDtos.Response estado(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody TareaDtos.EstadoRequest request) {
        return service.cambiarEstado(ContextoUsuario.empresa(jwt), id, request);
    }

    @PatchMapping("/{id}/asignacion")
    @PreAuthorize("hasAuthority('TAREAS_REASIGNAR')")
    public TareaDtos.Response reasignar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody TareaDtos.ReasignarRequest request) {
        return service.reasignar(ContextoUsuario.empresa(jwt), id, request);
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAuthority('TAREAS_VER')")
    public List<TareaDtos.HistorialResponse> historial(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return service.historial(ContextoUsuario.empresa(jwt), id);
    }
}
