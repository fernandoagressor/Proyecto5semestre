package com.inmobiliaria.inmobiliaria_api.controller.catalogo;

import com.inmobiliaria.inmobiliaria_api.dto.catalogo.CatalogoDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.catalogo.CatalogoEmpresaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Publicar el catálogo de la empresa y administrar la galería de fotos de cada proyecto. */
@RestController
public class CatalogoEmpresaController {

    private final CatalogoEmpresaService service;

    public CatalogoEmpresaController(CatalogoEmpresaService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/empresas/actual/catalogo")
    @PreAuthorize("hasAuthority('PROYECTOS_VER')")
    public CatalogoDtos.EstadoCatalogo estado(@AuthenticationPrincipal Jwt jwt) {
        return service.estado(ContextoUsuario.empresa(jwt));
    }

    @PutMapping("/api/v1/empresas/actual/catalogo")
    @PreAuthorize("hasAuthority('CONFIGURACION_EMPRESA_EDITAR')")
    public CatalogoDtos.EstadoCatalogo cambiar(@AuthenticationPrincipal Jwt jwt,
                                               @RequestBody CatalogoDtos.CambioCatalogo request) {
        return service.cambiar(ContextoUsuario.empresa(jwt), request.publico());
    }

    @GetMapping("/api/v1/proyectos/{proyectoId}/imagenes")
    @PreAuthorize("hasAuthority('PROYECTOS_VER')")
    public List<CatalogoDtos.Imagen> imagenes(@AuthenticationPrincipal Jwt jwt, @PathVariable Long proyectoId) {
        return service.imagenes(ContextoUsuario.empresa(jwt), proyectoId);
    }

    @PostMapping("/api/v1/proyectos/{proyectoId}/imagenes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PROYECTOS_EDITAR')")
    public CatalogoDtos.Imagen agregar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long proyectoId,
                                       @Valid @RequestBody CatalogoDtos.ImagenRequest request) {
        return service.agregar(ContextoUsuario.empresa(jwt), proyectoId, request);
    }

    @DeleteMapping("/api/v1/proyectos/{proyectoId}/imagenes/{imagenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PROYECTOS_EDITAR')")
    public void eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long proyectoId, @PathVariable Long imagenId) {
        service.eliminar(ContextoUsuario.empresa(jwt), proyectoId, imagenId);
    }
}
