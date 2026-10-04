package com.inmobiliaria.inmobiliaria_api.controller.cartera;

import com.inmobiliaria.inmobiliaria_api.dto.cartera.PlanPagoCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.PlanPagoResponse;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.cartera.PlanPagoService;
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
@RequestMapping("/api/v1/cartera/planes-pago")
public class PlanPagoController {
    private final PlanPagoService service;

    public PlanPagoController(PlanPagoService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('CUOTAS_VER')")
    public List<PlanPagoResponse> listar(@AuthenticationPrincipal Jwt jwt, @RequestParam Long contratoId) {
        return service.listar(ContextoUsuario.empresa(jwt), contratoId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CUOTAS_PROGRAMAR')")
    public PlanPagoResponse crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlanPagoCrearRequest request) {
        return service.crear(ContextoUsuario.empresa(jwt), request);
    }

    @PatchMapping("/{planId}/activar")
    @PreAuthorize("hasAuthority('CUOTAS_PROGRAMAR')")
    public PlanPagoResponse activar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
        return service.activar(ContextoUsuario.empresa(jwt), planId);
    }

    @PostMapping("/contratos/{contratoId}/sincronizar")
    @PreAuthorize("hasAuthority('CUOTAS_PROGRAMAR')")
    public PlanPagoResponse sincronizarContrato(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long contratoId
    ) {
        return service.sincronizarContrato(ContextoUsuario.empresa(jwt), contratoId);
    }
}
