package com.inmobiliaria.inmobiliaria_api.controller.crm;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.CitaComercialResponse;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoCitaComercial;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.crm.SeguimientoComercialService;
import java.time.LocalDateTime;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agenda/comercial")
public class AgendaComercialController {

    private final SeguimientoComercialService seguimientoService;

    public AgendaComercialController(
            SeguimientoComercialService seguimientoService
    ) {
        this.seguimientoService = seguimientoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AGENDA_VER')")
    public PaginaResponse<CitaComercialResponse> agenda(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam LocalDateTime desde,
            @RequestParam LocalDateTime hasta,
            @RequestParam(required = false) EstadoCitaComercial estado,
            @RequestParam(required = false) Long asesorId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return seguimientoService.agenda(
                ContextoUsuario.empresa(jwt),
                desde,
                hasta,
                estado,
                asesorId,
                pagina,
                tamano
        );
    }
}
