package com.inmobiliaria.inmobiliaria_api.controller.servicio;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.servicio.CitaServicioDtos;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.EstadoCitaServicio;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.servicio.CitaServicioService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
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
@RequestMapping("/api/v1/citas-servicio")
public class CitaServicioController {
    private final CitaServicioService service;
    public CitaServicioController(CitaServicioService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('CITAS_VER')")
    public PaginaResponse<CitaServicioDtos.Response> listar(@AuthenticationPrincipal Jwt jwt,
                                                            @RequestParam LocalDateTime desde,
                                                            @RequestParam LocalDateTime hasta,
                                                            @RequestParam(required = false) EstadoCitaServicio estado,
                                                            @RequestParam(required = false) Long responsableId,
                                                            @RequestParam(defaultValue = "0") int pagina,
                                                            @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(ContextoUsuario.empresa(jwt), desde, hasta, estado, responsableId, pagina, tamano);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CITAS_CREAR')")
    public CitaServicioDtos.Response crear(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody CitaServicioDtos.CrearRequest request) {
        return service.crear(ContextoUsuario.empresa(jwt), request);
    }

    @PatchMapping("/{id}/reprogramar")
    @PreAuthorize("hasAuthority('CITAS_REPROGRAMAR')")
    public CitaServicioDtos.Response reprogramar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                 @Valid @RequestBody CitaServicioDtos.ReprogramarRequest request) {
        return service.reprogramar(ContextoUsuario.empresa(jwt), id, request);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('CITAS_REPROGRAMAR')")
    public CitaServicioDtos.Response estado(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                            @Valid @RequestBody CitaServicioDtos.EstadoRequest request) {
        return service.cambiarEstado(ContextoUsuario.empresa(jwt), id, request);
    }
}
