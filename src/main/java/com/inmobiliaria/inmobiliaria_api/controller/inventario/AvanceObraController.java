package com.inmobiliaria.inmobiliaria_api.controller.inventario;

import com.inmobiliaria.inmobiliaria_api.dto.inventario.AvanceObraDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AvanceObraService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Avances de obra de un proyecto. */
@RestController
@RequestMapping("/api/v1/proyectos/{proyectoId}/avances")
public class AvanceObraController {

    private final AvanceObraService service;

    public AvanceObraController(AvanceObraService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AVANCES_OBRA_VER')")
    public List<AvanceObraDtos.Response> listar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId
    ) {
        return service.listar(ContextoUsuario.empresa(jwt), proyectoId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AVANCES_OBRA_REGISTRAR')")
    public AvanceObraDtos.Response registrar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @Valid @RequestBody AvanceObraDtos.RegistroRequest request
    ) {
        return service.registrar(ContextoUsuario.empresa(jwt), proyectoId, request);
    }
}
