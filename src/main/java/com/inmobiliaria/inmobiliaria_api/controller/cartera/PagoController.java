package com.inmobiliaria.inmobiliaria_api.controller.cartera;

import com.inmobiliaria.inmobiliaria_api.dto.cartera.MotivoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.PagoConfirmarRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.PagoCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.PagoResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.ReversionCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.ReversionResponse;
import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoPago;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.cartera.PagoService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/pagos")
public class PagoController {
    private final PagoService service;

    public PagoController(PagoService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('PAGOS_VER')")
    public PaginaResponse<PagoResponse> listar(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) EstadoPago estado, @RequestParam(required = false) Long contratoId, @RequestParam(required = false) Long clienteId, @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(ContextoUsuario.empresa(jwt), estado, contratoId, clienteId, pagina, tamano);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAGOS_CREAR')")
    public PagoResponse registrar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PagoCrearRequest request) {
        return service.registrar(ContextoUsuario.empresa(jwt), request);
    }

    @PatchMapping("/{pagoId}/revision")
    @PreAuthorize("hasAuthority('PAGOS_CREAR')")
    public PagoResponse enviarRevision(@AuthenticationPrincipal Jwt jwt, @PathVariable Long pagoId) {
        return service.enviarRevision(ContextoUsuario.empresa(jwt), pagoId);
    }

    @PatchMapping("/{pagoId}/confirmar")
    @PreAuthorize("hasAuthority('PAGOS_CONFIRMAR')")
    public PagoResponse confirmar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long pagoId, @Valid @RequestBody PagoConfirmarRequest request) {
        return service.confirmar(ContextoUsuario.empresa(jwt), pagoId, request);
    }

    @PatchMapping("/{pagoId}/rechazar")
    @PreAuthorize("hasAuthority('PAGOS_RECHAZAR')")
    public PagoResponse rechazar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long pagoId, @Valid @RequestBody MotivoRequest request) {
        return service.rechazar(ContextoUsuario.empresa(jwt), pagoId, request.motivo());
    }

    @PostMapping("/{pagoId}/reversiones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAGOS_REVERSAR')")
    public ReversionResponse solicitarReversion(@AuthenticationPrincipal Jwt jwt, @PathVariable Long pagoId, @Valid @RequestBody ReversionCrearRequest request) {
        return service.solicitarReversion(ContextoUsuario.empresa(jwt), pagoId, request);
    }

    @PatchMapping("/{pagoId}/reversiones/{reversionId}/aprobar")
    @PreAuthorize("hasAuthority('PAGOS_REVERSAR')")
    public PagoResponse aprobarReversion(@AuthenticationPrincipal Jwt jwt, @PathVariable Long pagoId, @PathVariable Long reversionId) {
        return service.aprobarReversion(ContextoUsuario.empresa(jwt), pagoId, reversionId);
    }

    @PatchMapping("/{pagoId}/reversiones/{reversionId}/rechazar")
    @PreAuthorize("hasAuthority('PAGOS_REVERSAR')")
    public ReversionResponse rechazarReversion(@AuthenticationPrincipal Jwt jwt, @PathVariable Long pagoId, @PathVariable Long reversionId) {
        return service.rechazarReversion(ContextoUsuario.empresa(jwt), pagoId, reversionId);
    }
}
