package com.inmobiliaria.inmobiliaria_api.controller.portal;

import com.inmobiliaria.inmobiliaria_api.dto.portal.PortalDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.portal.PortalAccesoService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Acceso de un cliente a su portal: crearlo o reenviarlo con una contraseña temporal nueva. */
@RestController
@RequestMapping("/api/v1/clientes/{clienteId}/portal")
public class PortalAccesoController {

    private final PortalAccesoService service;

    public PortalAccesoController(PortalAccesoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PORTAL_CLIENTE_HABILITAR')")
    public PortalDtos.Acceso habilitar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long clienteId) {
        return service.habilitar(ContextoUsuario.empresa(jwt), clienteId);
    }

    @PostMapping("/reenviar")
    @PreAuthorize("hasAuthority('PORTAL_CLIENTE_REENVIAR_ACCESO')")
    public PortalDtos.Acceso reenviar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long clienteId) {
        return service.reenviar(ContextoUsuario.empresa(jwt), clienteId);
    }
}
