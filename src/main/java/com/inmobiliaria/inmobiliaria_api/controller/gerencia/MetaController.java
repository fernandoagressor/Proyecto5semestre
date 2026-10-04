package com.inmobiliaria.inmobiliaria_api.controller.gerencia;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.ComisionMetaDtos;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoMeta;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.MetaComisionService;
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
@RequestMapping("/api/v1/metas-comerciales")
public class MetaController {
    private final MetaComisionService service;
    public MetaController(MetaComisionService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('METAS_VER')")
    public PaginaResponse<ComisionMetaDtos.MetaResponse> listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String periodo, @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) Long asesorId, @RequestParam(required = false) EstadoMeta estado,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.listarMetas(ContextoUsuario.empresa(jwt), periodo, proyectoId, asesorId, estado, pagina, tamano);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('METAS_GESTIONAR')")
    public ComisionMetaDtos.MetaResponse crear(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ComisionMetaDtos.MetaRequest request) {
        return service.crearMeta(ContextoUsuario.empresa(jwt), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('METAS_GESTIONAR')")
    public ComisionMetaDtos.MetaResponse actualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody ComisionMetaDtos.MetaValoresRequest request) {
        return service.actualizarMeta(ContextoUsuario.empresa(jwt), id, request);
    }

    @PatchMapping("/{id}/aprobar")
    @PreAuthorize("hasAuthority('METAS_APROBAR')")
    public ComisionMetaDtos.MetaResponse aprobar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.aprobarMeta(ContextoUsuario.empresa(jwt), id);
    }

    @PatchMapping("/{id}/cerrar")
    @PreAuthorize("hasAuthority('METAS_APROBAR')")
    public ComisionMetaDtos.MetaResponse cerrar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.cerrarMeta(ContextoUsuario.empresa(jwt), id);
    }

    @GetMapping("/{id}/cumplimiento")
    @PreAuthorize("hasAuthority('METAS_VER')")
    public ComisionMetaDtos.CumplimientoMetaResponse cumplimiento(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return service.cumplimiento(ContextoUsuario.empresa(jwt), id);
    }
}
