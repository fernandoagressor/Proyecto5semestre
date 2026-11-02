package com.inmobiliaria.inmobiliaria_api.controller.catalogo;

import com.inmobiliaria.inmobiliaria_api.dto.catalogo.CatalogoDtos;
import com.inmobiliaria.inmobiliaria_api.service.catalogo.CatalogoPublicoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.time.Duration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo público de proyectos de una empresa: abierto, sin sesión (ver SecurityConfig). */
@RestController
@RequestMapping("/api/v1/publico/catalogo/{empresa}")
public class CatalogoPublicoController {

    private final CatalogoPublicoService service;

    public CatalogoPublicoController(CatalogoPublicoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<CatalogoDtos.Empresa> empresa(@PathVariable String empresa) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(service.empresa(empresa));
    }

    @GetMapping("/proyectos/{proyectoId}")
    public ResponseEntity<CatalogoDtos.ProyectoDetalle> proyecto(@PathVariable String empresa, @PathVariable Long proyectoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(service.proyecto(empresa, proyectoId));
    }

    @PostMapping("/contacto")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void contacto(@PathVariable String empresa, @Valid @RequestBody CatalogoDtos.Contacto contacto,
                         HttpServletRequest request) {
        service.contactar(empresa, contacto, ip(request));
    }

    private static String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
