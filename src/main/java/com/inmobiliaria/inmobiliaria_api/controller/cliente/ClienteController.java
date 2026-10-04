package com.inmobiliaria.inmobiliaria_api.controller.cliente;

import com.inmobiliaria.inmobiliaria_api.dto.cliente.ClienteEstadoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cliente.ClienteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cliente.ClienteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.cliente.ClienteService;
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
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CLIENTES_VER')")
    public PaginaResponse<ClienteResponse> listar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return clienteService.listar(
                ContextoUsuario.empresa(jwt), texto, estado, pagina, tamano);
    }

    @GetMapping("/{clienteId}")
    @PreAuthorize("hasAuthority('CLIENTES_VER')")
    public ClienteResponse obtener(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long clienteId
    ) {
        return clienteService.obtener(ContextoUsuario.empresa(jwt), clienteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CLIENTES_CREAR')")
    public ClienteResponse crear(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ClienteRequest request
    ) {
        return clienteService.crear(ContextoUsuario.empresa(jwt), request);
    }

    @PutMapping("/{clienteId}")
    @PreAuthorize("hasAuthority('CLIENTES_EDITAR')")
    public ClienteResponse actualizar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long clienteId,
            @Valid @RequestBody ClienteRequest request
    ) {
        return clienteService.actualizar(
                ContextoUsuario.empresa(jwt), clienteId, request);
    }

    @PatchMapping("/{clienteId}/estado")
    @PreAuthorize("hasAuthority('CLIENTES_INACTIVAR')")
    public ClienteResponse cambiarEstado(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long clienteId,
            @Valid @RequestBody ClienteEstadoRequest request
    ) {
        return clienteService.cambiarEstado(
                ContextoUsuario.empresa(jwt), clienteId, request.estado());
    }
}
