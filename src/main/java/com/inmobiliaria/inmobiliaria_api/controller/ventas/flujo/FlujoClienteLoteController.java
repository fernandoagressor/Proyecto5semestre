package com.inmobiliaria.inmobiliaria_api.controller.ventas.flujo;

import com.inmobiliaria.inmobiliaria_api.dto.ventas.flujo.FlujoClienteLoteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.flujo.FlujoClienteLoteResponse;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.ventas.flujo.FlujoClienteLoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ventas/cliente-lote")
public class FlujoClienteLoteController {

    private final FlujoClienteLoteService flujoService;

    public FlujoClienteLoteController(FlujoClienteLoteService flujoService) {
        this.flujoService = flujoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("""
            hasAuthority('CLIENTES_CREAR')
            and hasAuthority('PROSPECTOS_CREAR')
            and hasAuthority('PROSPECTOS_CALIFICAR')
            and hasAuthority('OPORTUNIDADES_CREAR')
            and hasAuthority('OPORTUNIDADES_EDITAR')
            and hasAuthority('RESERVAS_CREAR')
            """)
    public FlujoClienteLoteResponse crear(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FlujoClienteLoteRequest request
    ) {
        return flujoService.crear(ContextoUsuario.empresa(jwt), request);
    }
}
