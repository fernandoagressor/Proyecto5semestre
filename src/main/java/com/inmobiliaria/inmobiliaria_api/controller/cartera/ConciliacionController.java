package com.inmobiliaria.inmobiliaria_api.controller.cartera;

import com.inmobiliaria.inmobiliaria_api.dto.cartera.ConciliacionCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.ConciliacionResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.ConciliarMovimientoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.MovimientoCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.MovimientoResponse;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.cartera.ConciliacionService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conciliaciones-bancarias")
@PreAuthorize("hasAuthority('CONCILIACION_BANCARIA')")
public class ConciliacionController {
    private final ConciliacionService service;

    public ConciliacionController(ConciliacionService service) { this.service = service; }

    @GetMapping
    public List<ConciliacionResponse> listar(@AuthenticationPrincipal Jwt jwt) {
        return service.listar(ContextoUsuario.empresa(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConciliacionResponse crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConciliacionCrearRequest request) {
        return service.crear(ContextoUsuario.empresa(jwt), request);
    }

    @PostMapping("/{conciliacionId}/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoResponse agregarMovimiento(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conciliacionId, @Valid @RequestBody MovimientoCrearRequest request) {
        return service.agregarMovimiento(ContextoUsuario.empresa(jwt), conciliacionId, request);
    }

    @PatchMapping("/{conciliacionId}/movimientos/{movimientoId}/conciliar")
    public MovimientoResponse conciliar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conciliacionId, @PathVariable Long movimientoId, @Valid @RequestBody ConciliarMovimientoRequest request) {
        return service.conciliarMovimiento(ContextoUsuario.empresa(jwt), conciliacionId, movimientoId, request.pagoId());
    }

    @PatchMapping("/{conciliacionId}/movimientos/{movimientoId}/ignorar")
    public MovimientoResponse ignorar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conciliacionId, @PathVariable Long movimientoId) {
        return service.ignorarMovimiento(ContextoUsuario.empresa(jwt), conciliacionId, movimientoId);
    }

    @PatchMapping("/{conciliacionId}/cerrar")
    public ConciliacionResponse cerrar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conciliacionId) {
        return service.cerrar(ContextoUsuario.empresa(jwt), conciliacionId);
    }
}
