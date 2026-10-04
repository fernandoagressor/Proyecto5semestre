package com.inmobiliaria.inmobiliaria_api.controller.servicio;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.servicio.ComunicacionDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.servicio.ComunicacionClienteService;
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
@RequestMapping("/api/v1/comunicaciones-clientes")
public class ComunicacionClienteController {
    private final ComunicacionClienteService service;
    public ComunicacionClienteController(ComunicacionClienteService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('COMUNICACIONES_CREAR')")
    public PaginaResponse<ComunicacionDtos.Response> listar(@AuthenticationPrincipal Jwt jwt,
                                                            @RequestParam(required = false) Long clienteId,
                                                            @RequestParam(required = false) String estado,
                                                            @RequestParam(defaultValue = "0") int pagina,
                                                            @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(ContextoUsuario.empresa(jwt), clienteId, estado, pagina, tamano);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMUNICACIONES_CREAR')")
    public ComunicacionDtos.Response crear(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody ComunicacionDtos.CrearRequest request) {
        return service.crear(ContextoUsuario.empresa(jwt), request, false);
    }

    @PostMapping("/masivas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMUNICACIONES_MASIVAS_ENVIAR')")
    public ComunicacionDtos.Response crearMasiva(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody ComunicacionDtos.CrearRequest request) {
        return service.crear(ContextoUsuario.empresa(jwt), request, true);
    }

    @PatchMapping("/{id}/enviada")
    @PreAuthorize("hasAuthority('COMUNICACIONES_CREAR')")
    public ComunicacionDtos.Response enviada(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.confirmarEnvio(ContextoUsuario.empresa(jwt), id);
    }

    @PatchMapping("/{id}/fallida")
    @PreAuthorize("hasAuthority('COMUNICACIONES_CREAR')")
    public ComunicacionDtos.Response fallida(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.marcarFallida(ContextoUsuario.empresa(jwt), id);
    }
}
