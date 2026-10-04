package com.inmobiliaria.inmobiliaria_api.controller.inventario;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.CambioEstadoProyectoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.ProyectoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.ProyectoResponse;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoProyecto;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.inventario.ProyectoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/proyectos")
public class ProyectoController {

    private final ProyectoService proyectoService;

    public ProyectoController(ProyectoService proyectoService) {
        this.proyectoService = proyectoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROYECTOS_VER')")
    public PaginaResponse<ProyectoResponse> listar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoProyecto estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return proyectoService.listar(
                ContextoUsuario.empresa(jwt),
                texto,
                estado,
                pagina,
                tamano
        );
    }

    @GetMapping("/{proyectoId}")
    @PreAuthorize("hasAuthority('PROYECTOS_VER')")
    public ProyectoResponse obtener(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId
    ) {
        return proyectoService.obtener(
                ContextoUsuario.empresa(jwt),
                proyectoId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PROYECTOS_CREAR')")
    public ProyectoResponse crear(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProyectoRequest request
    ) {
        return proyectoService.crear(ContextoUsuario.empresa(jwt), request);
    }

    @PutMapping("/{proyectoId}")
    @PreAuthorize("hasAuthority('PROYECTOS_EDITAR')")
    public ProyectoResponse actualizar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @Valid @RequestBody ProyectoRequest request
    ) {
        return proyectoService.actualizar(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                request
        );
    }

    @PatchMapping("/{proyectoId}/estado")
    @PreAuthorize("hasAuthority('PROYECTOS_EDITAR')")
    public ProyectoResponse cambiarEstado(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @Valid @RequestBody CambioEstadoProyectoRequest request
    ) {
        return proyectoService.cambiarEstado(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                request.estado()
        );
    }
}
