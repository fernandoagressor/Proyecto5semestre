package com.inmobiliaria.inmobiliaria_api.controller.servicio;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.servicio.ConocimientoDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.servicio.BaseConocimientoService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/base-conocimiento")
public class BaseConocimientoController {
    private final BaseConocimientoService service;
    public BaseConocimientoController(BaseConocimientoService service) { this.service = service; }

    @GetMapping("/categorias")
    @PreAuthorize("hasAuthority('BASE_CONOCIMIENTO_VER')")
    public List<ConocimientoDtos.CategoriaResponse> categorias(@AuthenticationPrincipal Jwt jwt) {
        return service.categorias(ContextoUsuario.empresa(jwt));
    }

    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BASE_CONOCIMIENTO_EDITAR')")
    public ConocimientoDtos.CategoriaResponse crearCategoria(@AuthenticationPrincipal Jwt jwt,
                                                              @Valid @RequestBody ConocimientoDtos.CategoriaRequest request) {
        return service.crearCategoria(ContextoUsuario.empresa(jwt), request);
    }

    @GetMapping("/articulos")
    @PreAuthorize("hasAuthority('BASE_CONOCIMIENTO_VER')")
    public PaginaResponse<ConocimientoDtos.ArticuloResponse> listar(@AuthenticationPrincipal Jwt jwt,
                                                                    @RequestParam(required = false) Long categoriaId,
                                                                    @RequestParam(required = false) String texto,
                                                                    @RequestParam(defaultValue = "0") int pagina,
                                                                    @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(ContextoUsuario.empresa(jwt), categoriaId, texto, pagina, tamano);
    }

    @GetMapping("/articulos/{id}")
    @PreAuthorize("hasAuthority('BASE_CONOCIMIENTO_VER')")
    public ConocimientoDtos.ArticuloResponse obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.obtener(ContextoUsuario.empresa(jwt), id);
    }

    @PostMapping("/articulos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BASE_CONOCIMIENTO_EDITAR')")
    public ConocimientoDtos.ArticuloResponse crear(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody ConocimientoDtos.ArticuloRequest request) {
        return service.crearArticulo(ContextoUsuario.empresa(jwt), request);
    }

    @PutMapping("/articulos/{id}")
    @PreAuthorize("hasAuthority('BASE_CONOCIMIENTO_EDITAR')")
    public ConocimientoDtos.ArticuloResponse actualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                        @Valid @RequestBody ConocimientoDtos.ArticuloRequest request) {
        return service.actualizar(ContextoUsuario.empresa(jwt), id, request);
    }

    @PatchMapping("/articulos/{id}/publicar")
    @PreAuthorize("hasAuthority('BASE_CONOCIMIENTO_EDITAR')")
    public ConocimientoDtos.ArticuloResponse publicar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.publicar(ContextoUsuario.empresa(jwt), id);
    }

    @PatchMapping("/articulos/{id}/archivar")
    @PreAuthorize("hasAuthority('BASE_CONOCIMIENTO_EDITAR')")
    public ConocimientoDtos.ArticuloResponse archivar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.archivar(ContextoUsuario.empresa(jwt), id);
    }
}
