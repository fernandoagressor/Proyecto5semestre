package com.inmobiliaria.inmobiliaria_api.controller.crm;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ProspectoCalificacionRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ProspectoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ProspectoResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ReasignarProspectoRequest;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.TemperaturaProspecto;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.crm.ProspectoService;
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
@RequestMapping("/api/v1/prospectos")
public class ProspectoController {

    private final ProspectoService prospectoService;

    public ProspectoController(ProspectoService prospectoService) {
        this.prospectoService = prospectoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROSPECTOS_VER')")
    public PaginaResponse<ProspectoResponse> listar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoProspecto estado,
            @RequestParam(required = false) TemperaturaProspecto temperatura,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) Long asesorId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return prospectoService.listar(
                ContextoUsuario.empresa(jwt),
                texto,
                estado,
                temperatura,
                proyectoId,
                asesorId,
                pagina,
                tamano
        );
    }

    @GetMapping("/{prospectoId}")
    @PreAuthorize("hasAuthority('PROSPECTOS_VER')")
    public ProspectoResponse obtener(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long prospectoId
    ) {
        return prospectoService.obtener(
                ContextoUsuario.empresa(jwt),
                prospectoId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PROSPECTOS_CREAR')")
    public ProspectoResponse crear(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProspectoRequest request
    ) {
        return prospectoService.crear(
                ContextoUsuario.empresa(jwt),
                request
        );
    }

    @PutMapping("/{prospectoId}")
    @PreAuthorize("hasAuthority('PROSPECTOS_EDITAR')")
    public ProspectoResponse actualizar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long prospectoId,
            @Valid @RequestBody ProspectoRequest request
    ) {
        return prospectoService.actualizar(
                ContextoUsuario.empresa(jwt),
                prospectoId,
                request
        );
    }

    @PatchMapping("/{prospectoId}/calificacion")
    @PreAuthorize("hasAuthority('PROSPECTOS_CALIFICAR')")
    public ProspectoResponse calificar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long prospectoId,
            @Valid @RequestBody ProspectoCalificacionRequest request
    ) {
        return prospectoService.calificar(
                ContextoUsuario.empresa(jwt),
                prospectoId,
                request
        );
    }

    @PatchMapping("/{prospectoId}/asesor")
    @PreAuthorize("hasAuthority('PROSPECTOS_REASIGNAR')")
    public ProspectoResponse reasignar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long prospectoId,
            @Valid @RequestBody ReasignarProspectoRequest request
    ) {
        return prospectoService.reasignar(
                ContextoUsuario.empresa(jwt),
                prospectoId,
                request.asesorId()
        );
    }
}
