package com.inmobiliaria.inmobiliaria_api.controller.cartera;

import com.inmobiliaria.inmobiliaria_api.dto.cartera.CierreCarteraRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.CierreCarteraResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.DocumentoCarteraRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.DocumentoCarteraResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.GestionCobroRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.GestionCobroResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.MoraResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.RefinanciacionCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.RefinanciacionResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.SeguimientoCuotaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.cartera.CarteraGestionService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cartera")
public class CarteraController {
    private final CarteraGestionService service;

    public CarteraController(CarteraGestionService service) { this.service = service; }

    @PostMapping("/mora/actualizar")
    @PreAuthorize("hasAuthority('MORA_GESTIONAR')")
    public int actualizarMora(@AuthenticationPrincipal Jwt jwt) {
        return service.actualizarMora(ContextoUsuario.empresa(jwt));
    }

    @GetMapping("/mora")
    @PreAuthorize("hasAuthority('MORA_VER')")
    public PaginaResponse<MoraResponse> listarMora(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long contratoId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return service.listarMora(ContextoUsuario.empresa(jwt), contratoId, pagina, tamano);
    }

    @GetMapping("/seguimiento")
    @PreAuthorize("hasAuthority('MORA_VER')")
    public PaginaResponse<SeguimientoCuotaResponse> listarSeguimiento(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long contratoId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return service.listarSeguimiento(ContextoUsuario.empresa(jwt), contratoId, pagina, tamano);
    }

    @GetMapping("/gestiones-cobro")
    @PreAuthorize("hasAuthority('MORA_VER')")
    public List<GestionCobroResponse> listarGestiones(@AuthenticationPrincipal Jwt jwt, @RequestParam Long contratoId) {
        return service.listarGestiones(ContextoUsuario.empresa(jwt), contratoId);
    }

    @PostMapping("/gestiones-cobro")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MORA_GESTIONAR')")
    public GestionCobroResponse registrarGestion(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GestionCobroRequest request) {
        return service.registrarGestion(ContextoUsuario.empresa(jwt), request);
    }

    @GetMapping("/refinanciaciones")
    @PreAuthorize("hasAuthority('REFINANCIACIONES_VER')")
    public List<RefinanciacionResponse> listarRefinanciaciones(@AuthenticationPrincipal Jwt jwt) {
        return service.listarRefinanciaciones(ContextoUsuario.empresa(jwt));
    }

    @PostMapping("/refinanciaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('REFINANCIACIONES_PROPONER')")
    public RefinanciacionResponse proponerRefinanciacion(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RefinanciacionCrearRequest request) {
        return service.proponerRefinanciacion(ContextoUsuario.empresa(jwt), request);
    }

    @PatchMapping("/refinanciaciones/{refinanciacionId}/aprobar")
    @PreAuthorize("hasAuthority('REFINANCIACIONES_APROBAR')")
    public RefinanciacionResponse aprobarRefinanciacion(@AuthenticationPrincipal Jwt jwt, @PathVariable Long refinanciacionId) {
        return service.aprobarRefinanciacion(ContextoUsuario.empresa(jwt), refinanciacionId);
    }

    @PatchMapping("/refinanciaciones/{refinanciacionId}/rechazar")
    @PreAuthorize("hasAuthority('REFINANCIACIONES_APROBAR')")
    public RefinanciacionResponse rechazarRefinanciacion(@AuthenticationPrincipal Jwt jwt, @PathVariable Long refinanciacionId) {
        return service.rechazarRefinanciacion(ContextoUsuario.empresa(jwt), refinanciacionId);
    }

    @PatchMapping("/refinanciaciones/{refinanciacionId}/aplicar")
    @PreAuthorize("hasAuthority('REFINANCIACIONES_APROBAR')")
    public RefinanciacionResponse aplicarRefinanciacion(@AuthenticationPrincipal Jwt jwt, @PathVariable Long refinanciacionId) {
        return service.aplicarRefinanciacion(ContextoUsuario.empresa(jwt), refinanciacionId);
    }

    @PostMapping("/estados-cuenta")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ESTADOS_CUENTA_GENERAR')")
    public DocumentoCarteraResponse generarEstadoCuenta(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DocumentoCarteraRequest request) {
        return service.generarEstadoCuenta(ContextoUsuario.empresa(jwt), request);
    }

    @PostMapping("/paz-salvos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAZ_SALVO_GENERAR')")
    public DocumentoCarteraResponse generarPazSalvo(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DocumentoCarteraRequest request) {
        return service.generarPazSalvo(ContextoUsuario.empresa(jwt), request);
    }

    @PostMapping("/cierres")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CIERRE_CARTERA')")
    public CierreCarteraResponse cerrarPeriodo(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CierreCarteraRequest request) {
        return service.cerrarPeriodo(ContextoUsuario.empresa(jwt), request.periodo());
    }
}
