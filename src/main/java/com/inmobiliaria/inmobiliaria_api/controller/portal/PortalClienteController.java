package com.inmobiliaria.inmobiliaria_api.controller.portal;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.portal.PortalDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.portal.PortalClienteService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/portal")
public class PortalClienteController {
    private final PortalClienteService service;
    public PortalClienteController(PortalClienteService service) { this.service = service; }

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('DASHBOARD_VER')")
    public PortalDtos.Resumen resumen(@AuthenticationPrincipal Jwt jwt) {
        return service.resumen(ContextoUsuario.empresa(jwt));
    }

    @GetMapping("/documentos")
    @PreAuthorize("hasAuthority('DOCUMENTOS_VER')")
    public PaginaResponse<PortalDtos.Documento> documentos(@AuthenticationPrincipal Jwt jwt,
                                                            @RequestParam(defaultValue = "0") int pagina,
                                                            @RequestParam(defaultValue = "20") int tamano) {
        return service.documentos(ContextoUsuario.empresa(jwt), pagina, tamano);
    }

    @GetMapping("/documentos/{archivoId}/descargar")
    @PreAuthorize("hasAuthority('DOCUMENTOS_DESCARGAR')")
    public ResponseEntity<?> descargarDocumento(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable Long archivoId) {
        var descarga = service.descargarDocumento(ContextoUsuario.empresa(jwt), archivoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descarga.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(descarga.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(descarga.recurso());
    }

    @GetMapping("/cambios-datos")
    @PreAuthorize("hasAnyAuthority('DATOS_CLIENTE_SOLICITAR_CAMBIO','DATOS_CLIENTE_APROBAR_CAMBIO')")
    public PaginaResponse<PortalDtos.CambioResponse> cambios(@AuthenticationPrincipal Jwt jwt,
                                                             @RequestParam(required = false) String estado,
                                                             @RequestParam(required = false) Long clienteId,
                                                             @RequestParam(defaultValue = "0") int pagina,
                                                             @RequestParam(defaultValue = "20") int tamano) {
        return service.cambios(ContextoUsuario.empresa(jwt), estado, clienteId, pagina, tamano);
    }

    @PostMapping("/cambios-datos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DATOS_CLIENTE_SOLICITAR_CAMBIO')")
    public PortalDtos.CambioResponse solicitar(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody PortalDtos.CambioCrearRequest request) {
        return service.solicitarCambio(ContextoUsuario.empresa(jwt), request);
    }

    @PatchMapping("/cambios-datos/{id}/aprobar")
    @PreAuthorize("hasAuthority('DATOS_CLIENTE_APROBAR_CAMBIO')")
    public PortalDtos.CambioResponse aprobar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                             @RequestBody(required = false) PortalDtos.CambioRevisarRequest request) {
        return service.aprobar(ContextoUsuario.empresa(jwt), id, request == null ? null : request.comentario());
    }

    @PatchMapping("/cambios-datos/{id}/rechazar")
    @PreAuthorize("hasAuthority('DATOS_CLIENTE_APROBAR_CAMBIO')")
    public PortalDtos.CambioResponse rechazar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                              @Valid @RequestBody PortalDtos.CambioRevisarRequest request) {
        return service.rechazar(ContextoUsuario.empresa(jwt), id, request.comentario());
    }

    @PatchMapping("/cambios-datos/{id}/cancelar")
    @PreAuthorize("hasAuthority('DATOS_CLIENTE_SOLICITAR_CAMBIO')")
    public PortalDtos.CambioResponse cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.cancelar(ContextoUsuario.empresa(jwt), id);
    }
}
