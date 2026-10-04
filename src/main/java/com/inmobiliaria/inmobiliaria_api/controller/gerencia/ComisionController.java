package com.inmobiliaria.inmobiliaria_api.controller.gerencia;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.ComisionMetaDtos;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoComision;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.MetaComisionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/comisiones")
public class ComisionController {
    private final MetaComisionService service;
    public ComisionController(MetaComisionService service) { this.service = service; }

    @GetMapping("/reglas")
    @PreAuthorize("hasAnyAuthority('COMISIONES_PROPIAS_VER','COMISIONES_EQUIPO_VER')")
    public PaginaResponse<ComisionMetaDtos.ReglaResponse> reglas(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.listarReglas(ContextoUsuario.empresa(jwt), proyectoId, activo, pagina, tamano);
    }

    @PostMapping("/reglas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMISIONES_REGLAS_GESTIONAR')")
    public ComisionMetaDtos.ReglaResponse crearRegla(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ComisionMetaDtos.ReglaRequest request) {
        return service.crearRegla(ContextoUsuario.empresa(jwt), request);
    }

    @PutMapping("/reglas/{id}")
    @PreAuthorize("hasAuthority('COMISIONES_REGLAS_GESTIONAR')")
    public ComisionMetaDtos.ReglaResponse actualizarRegla(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id, @Valid @RequestBody ComisionMetaDtos.ReglaRequest request) {
        return service.actualizarRegla(ContextoUsuario.empresa(jwt), id, request);
    }

    @PatchMapping("/reglas/{id}/cerrar")
    @PreAuthorize("hasAuthority('COMISIONES_REGLAS_GESTIONAR')")
    public ComisionMetaDtos.ReglaResponse cerrarRegla(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id, @Valid @RequestBody ComisionMetaDtos.FechaRequest request) {
        return service.cerrarRegla(ContextoUsuario.empresa(jwt), id, request.fecha());
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMISIONES_PROPIAS_VER','COMISIONES_EQUIPO_VER')")
    public PaginaResponse<ComisionMetaDtos.ComisionResponse> listar(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) Long asesorId,
            @RequestParam(required = false) EstadoComision estado,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.listarComisiones(ContextoUsuario.empresa(jwt), proyectoId, asesorId, estado, pagina, tamano);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMISIONES_GESTIONAR')")
    public ComisionMetaDtos.ComisionResponse proyectar(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ComisionMetaDtos.ProyectarComisionRequest request) {
        return service.proyectar(ContextoUsuario.empresa(jwt), request);
    }

    @PatchMapping("/{id}/causar")
    @PreAuthorize("hasAuthority('COMISIONES_GESTIONAR')")
    public ComisionMetaDtos.ComisionResponse causar(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id, @Valid @RequestBody ComisionMetaDtos.FechaRequest request) {
        return service.causar(ContextoUsuario.empresa(jwt), id, request.fecha());
    }

    @PatchMapping("/{id}/aprobar")
    @PreAuthorize("hasAuthority('COMISIONES_APROBAR')")
    public ComisionMetaDtos.ComisionResponse aprobar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.aprobar(ContextoUsuario.empresa(jwt), id);
    }

    @PatchMapping("/{id}/pagar")
    @PreAuthorize("hasAuthority('COMISIONES_APROBAR')")
    public ComisionMetaDtos.ComisionResponse pagar(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id, @Valid @RequestBody ComisionMetaDtos.FechaRequest request) {
        return service.pagar(ContextoUsuario.empresa(jwt), id, request.fecha());
    }

    @PatchMapping("/{id}/anular")
    @PreAuthorize("hasAuthority('COMISIONES_GESTIONAR')")
    public ComisionMetaDtos.ComisionResponse anular(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.anular(ContextoUsuario.empresa(jwt), id);
    }
}
