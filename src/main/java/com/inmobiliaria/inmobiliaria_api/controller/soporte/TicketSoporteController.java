package com.inmobiliaria.inmobiliaria_api.controller.soporte;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.soporte.SoporteDtos;
import com.inmobiliaria.inmobiliaria_api.entity.soporte.EstadoTicket;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.soporte.TicketSoporteService;
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
@RequestMapping("/api/v1/soporte/tickets")
public class TicketSoporteController {
    private final TicketSoporteService service;
    public TicketSoporteController(TicketSoporteService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('SOPORTE_VER')")
    public PaginaResponse<SoporteDtos.Response> listar(@AuthenticationPrincipal Jwt jwt,
                                                       @RequestParam(required = false) EstadoTicket estado,
                                                       @RequestParam(required = false) Long asignadoA,
                                                       @RequestParam(defaultValue = "0") int pagina,
                                                       @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(ContextoUsuario.empresa(jwt), estado, asignadoA, pagina, tamano);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SOPORTE_CREAR')")
    public SoporteDtos.Response crear(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody SoporteDtos.CrearRequest request) {
        return service.crear(ContextoUsuario.empresa(jwt), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SOPORTE_VER')")
    public SoporteDtos.Response obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.obtener(ContextoUsuario.empresa(jwt), id);
    }

    @GetMapping("/{id}/mensajes")
    @PreAuthorize("hasAuthority('SOPORTE_VER')")
    public List<SoporteDtos.MensajeResponse> mensajes(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.mensajes(ContextoUsuario.empresa(jwt), id);
    }

    @PostMapping("/{id}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SOPORTE_RESPONDER')")
    public SoporteDtos.MensajeResponse responder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                 @Valid @RequestBody SoporteDtos.MensajeRequest request) {
        return service.responder(ContextoUsuario.empresa(jwt), id, request);
    }

    @PatchMapping("/{id}/asignacion")
    @PreAuthorize("hasAuthority('SOPORTE_ASIGNAR')")
    public SoporteDtos.Response asignar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                        @Valid @RequestBody SoporteDtos.AsignarRequest request) {
        return service.asignar(ContextoUsuario.empresa(jwt), id, request);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('SOPORTE_CAMBIAR_ESTADO')")
    public SoporteDtos.Response estado(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                       @Valid @RequestBody SoporteDtos.EstadoRequest request) {
        return service.cambiarEstado(ContextoUsuario.empresa(jwt), id, request);
    }

    @PostMapping("/{id}/adjuntos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SOPORTE_RESPONDER')")
    public SoporteDtos.AdjuntoResponse adjuntar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                @Valid @RequestBody SoporteDtos.AdjuntoRequest request) {
        return service.adjuntar(ContextoUsuario.empresa(jwt), id, request);
    }

    @GetMapping("/{id}/adjuntos")
    @PreAuthorize("hasAuthority('SOPORTE_VER')")
    public List<SoporteDtos.AdjuntoResponse> adjuntos(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.adjuntos(ContextoUsuario.empresa(jwt), id);
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAuthority('SOPORTE_ASIGNAR')")
    public List<SoporteDtos.HistorialResponse> historial(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.historial(ContextoUsuario.empresa(jwt), id);
    }
}
