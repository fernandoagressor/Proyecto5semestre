package com.inmobiliaria.inmobiliaria_api.controller.ventas;

import com.inmobiliaria.inmobiliaria_api.dto.ventas.desistimiento.DesistimientoDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.ventas.desistimiento.DesistimientoService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.CacheControl;
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

/** Desistimientos de compra: liquidación, solicitud, aprobación o rechazo y acta en PDF. */
@RestController
@RequestMapping("/api/v1/desistimientos")
public class DesistimientoController {

    private final DesistimientoService service;

    public DesistimientoController(DesistimientoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DESISTIMIENTOS_VER')")
    public List<DesistimientoDtos.Response> listar(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(required = false) String estado) {
        return service.listar(ContextoUsuario.empresa(jwt), estado);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DESISTIMIENTOS_VER')")
    public DesistimientoDtos.Response obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.obtener(ContextoUsuario.empresa(jwt), id);
    }

    @GetMapping("/liquidacion")
    @PreAuthorize("hasAuthority('DESISTIMIENTOS_SOLICITAR')")
    public DesistimientoDtos.Liquidacion liquidacion(@AuthenticationPrincipal Jwt jwt, @RequestParam Long contratoId,
            @RequestParam(required = false) BigDecimal porcentajePenalidad,
            @RequestParam(required = false) BigDecimal otrosDescuentos) {
        return service.liquidar(ContextoUsuario.empresa(jwt), contratoId, porcentajePenalidad, otrosDescuentos);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DESISTIMIENTOS_SOLICITAR')")
    public DesistimientoDtos.Response solicitar(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody DesistimientoDtos.SolicitudRequest request) {
        return service.solicitar(ContextoUsuario.empresa(jwt), request);
    }

    @PatchMapping("/{id}/aprobar")
    @PreAuthorize("hasAuthority('DESISTIMIENTOS_APROBAR')")
    public DesistimientoDtos.Response aprobar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                              @Valid @RequestBody DesistimientoDtos.RevisionRequest request) {
        return service.aprobar(ContextoUsuario.empresa(jwt), id, request);
    }

    @PatchMapping("/{id}/rechazar")
    @PreAuthorize("hasAuthority('DESISTIMIENTOS_APROBAR')")
    public DesistimientoDtos.Response rechazar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                               @Valid @RequestBody DesistimientoDtos.RevisionRequest request) {
        return service.rechazar(ContextoUsuario.empresa(jwt), id, request);
    }

    @GetMapping("/{id}/acta")
    @PreAuthorize("hasAuthority('DESISTIMIENTOS_VER')")
    public ResponseEntity<byte[]> acta(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        byte[] pdf = service.acta(ContextoUsuario.empresa(jwt), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("acta-desistimiento-" + id + ".pdf", StandardCharsets.UTF_8).build().toString())
                .body(pdf);
    }
}
