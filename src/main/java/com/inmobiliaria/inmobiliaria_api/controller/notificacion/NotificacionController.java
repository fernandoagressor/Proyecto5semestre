package com.inmobiliaria.inmobiliaria_api.controller.notificacion;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.notificacion.NotificacionDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.notificacion.NotificacionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificaciones")
public class NotificacionController {
    private final NotificacionService service;

    public NotificacionController(NotificacionService service) { this.service = service; }

    @GetMapping
    public PaginaResponse<NotificacionDtos.Response> listar(@AuthenticationPrincipal Jwt jwt,
                                                            @RequestParam(required = false) Boolean leida,
                                                            @RequestParam(defaultValue = "0") int pagina,
                                                            @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(ContextoUsuario.empresa(jwt), leida, pagina, tamano);
    }

    @GetMapping("/resumen")
    public NotificacionDtos.Resumen resumen(@AuthenticationPrincipal Jwt jwt) {
        return service.resumen(ContextoUsuario.empresa(jwt));
    }

    @PatchMapping("/{id}/leer")
    public NotificacionDtos.Response leer(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.marcarLeida(ContextoUsuario.empresa(jwt), id);
    }
}
