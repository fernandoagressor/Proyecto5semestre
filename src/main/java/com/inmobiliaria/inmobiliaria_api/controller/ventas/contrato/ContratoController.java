package com.inmobiliaria.inmobiliaria_api.controller.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoAnularRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoClienteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoClienteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoCondicionesRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoDocumentoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoFirmanteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoFirmanteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoVersionResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.FirmaContratoRequest;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.EstadoContrato;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.ventas.contrato.ContratoService;
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
@RequestMapping("/api/v1/contratos")
public class ContratoController {

    private final ContratoService contratoService;

    public ContratoController(ContratoService contratoService) {
        this.contratoService = contratoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONTRATOS_VER')")
    public PaginaResponse<ContratoResponse> listar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) EstadoContrato estado,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long loteId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return contratoService.listar(
                ContextoUsuario.empresa(jwt),
                estado,
                proyectoId,
                clienteId,
                loteId,
                pagina,
                tamano
        );
    }

    @GetMapping("/{contratoId}")
    @PreAuthorize("hasAuthority('CONTRATOS_VER')")
    public ContratoResponse obtener(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long contratoId
    ) {
        return contratoService.obtener(
                ContextoUsuario.empresa(jwt),
                contratoId
        );
    }

    @PutMapping("/{contratoId}")
    @PreAuthorize("hasAuthority('CONTRATOS_EDITAR')")
    public ContratoResponse actualizar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long contratoId,
            @Valid @RequestBody ContratoCondicionesRequest request
    ) {
        return contratoService.actualizar(
                ContextoUsuario.empresa(jwt),
                contratoId,
                request
        );
    }

    @PostMapping("/{contratoId}/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONTRATOS_EDITAR')")
    public ContratoClienteResponse agregarCliente(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long contratoId,
            @Valid @RequestBody ContratoClienteRequest request
    ) {
        return contratoService.agregarCliente(
                ContextoUsuario.empresa(jwt),
                contratoId,
                request
        );
    }

    @PostMapping("/{contratoId}/firmantes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONTRATOS_FIRMAS_GESTIONAR')")
    public ContratoFirmanteResponse agregarFirmante(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long contratoId,
            @Valid @RequestBody ContratoFirmanteRequest request
    ) {
        return contratoService.agregarFirmante(
                ContextoUsuario.empresa(jwt),
                contratoId,
                request
        );
    }

    @PostMapping("/{contratoId}/versiones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONTRATOS_EDITAR')")
    public ContratoVersionResponse adjuntarDocumento(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long contratoId,
            @Valid @RequestBody ContratoDocumentoRequest request
    ) {
        return contratoService.adjuntarDocumento(
                ContextoUsuario.empresa(jwt),
                contratoId,
                request.archivoId(),
                request.motivo()
        );
    }

    @PatchMapping("/{contratoId}/negociacion")
    @PreAuthorize("hasAuthority('CONTRATOS_EDITAR')")
    public ContratoResponse iniciarNegociacion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long contratoId
    ) {
        return contratoService.iniciarNegociacion(
                ContextoUsuario.empresa(jwt),
                contratoId
        );
    }

    @PatchMapping("/{contratoId}/enviar-firma")
    @PreAuthorize("hasAuthority('CONTRATOS_FIRMAS_GESTIONAR')")
    public ContratoResponse enviarAFirma(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long contratoId
    ) {
        return contratoService.enviarAFirma(
                ContextoUsuario.empresa(jwt),
                contratoId
        );
    }

    @PostMapping("/{contratoId}/firmantes/{firmanteId}/firmas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONTRATOS_FIRMAS_GESTIONAR')")
    public ContratoResponse registrarFirma(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long contratoId,
            @PathVariable Long firmanteId,
            @Valid @RequestBody FirmaContratoRequest request
    ) {
        return contratoService.registrarFirma(
                ContextoUsuario.empresa(jwt),
                contratoId,
                firmanteId,
                request
        );
    }

    @PatchMapping("/{contratoId}/anular")
    @PreAuthorize("hasAuthority('CONTRATOS_ANULAR')")
    public ContratoResponse anular(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long contratoId,
            @Valid @RequestBody ContratoAnularRequest request
    ) {
        return contratoService.anular(
                ContextoUsuario.empresa(jwt),
                contratoId,
                request.motivo()
        );
    }
}
