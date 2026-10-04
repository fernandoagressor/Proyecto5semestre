package com.inmobiliaria.inmobiliaria_api.controller.simulacion;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.simulacion.SimulacionRequest;
import com.inmobiliaria.inmobiliaria_api.dto.simulacion.SimulacionResponse;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.EstadoSimulacion;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.simulacion.SimulacionService;
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
@RequestMapping("/api/v1/simulaciones")
public class SimulacionController {

    private final SimulacionService simulacionService;

    public SimulacionController(SimulacionService simulacionService) {
        this.simulacionService = simulacionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SIMULACIONES_VER')")
    public PaginaResponse<SimulacionResponse> listar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) EstadoSimulacion estado,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) Long oportunidadId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return simulacionService.listar(
                ContextoUsuario.empresa(jwt),
                estado,
                proyectoId,
                oportunidadId,
                pagina,
                tamano
        );
    }

    @GetMapping("/{simulacionId}")
    @PreAuthorize("hasAuthority('SIMULACIONES_VER')")
    public SimulacionResponse obtener(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long simulacionId
    ) {
        return simulacionService.obtener(
                ContextoUsuario.empresa(jwt),
                simulacionId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SIMULACIONES_CREAR')")
    public SimulacionResponse crear(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SimulacionRequest request
    ) {
        return simulacionService.crear(
                ContextoUsuario.empresa(jwt),
                request
        );
    }

    @PatchMapping("/{simulacionId}/enviar")
    @PreAuthorize("hasAuthority('SIMULACIONES_ENVIAR')")
    public SimulacionResponse enviar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long simulacionId
    ) {
        return simulacionService.enviar(
                ContextoUsuario.empresa(jwt),
                simulacionId
        );
    }

    @PatchMapping("/{simulacionId}/cancelar")
    @PreAuthorize("hasAuthority('SIMULACIONES_CREAR')")
    public SimulacionResponse cancelar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long simulacionId
    ) {
        return simulacionService.cancelar(
                ContextoUsuario.empresa(jwt),
                simulacionId
        );
    }
}
