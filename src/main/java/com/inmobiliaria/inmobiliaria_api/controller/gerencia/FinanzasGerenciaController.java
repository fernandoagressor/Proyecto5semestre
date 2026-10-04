package com.inmobiliaria.inmobiliaria_api.controller.gerencia;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.FinanzasDtos;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoCosto;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EstadoPresupuesto;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.FinanzasGerenciaService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/gerencia")
public class FinanzasGerenciaController {
    private final FinanzasGerenciaService service;
    public FinanzasGerenciaController(FinanzasGerenciaService service) { this.service = service; }

    @GetMapping("/categorias-costo")
    @PreAuthorize("hasAnyAuthority('PRESUPUESTOS_VER','COSTOS_PROYECTO_VER')")
    public List<FinanzasDtos.CategoriaResponse> categorias(@AuthenticationPrincipal Jwt jwt) {
        return service.categorias(ContextoUsuario.empresa(jwt));
    }

    @PostMapping("/categorias-costo")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COSTOS_PROYECTO_GESTIONAR')")
    public FinanzasDtos.CategoriaResponse crearCategoria(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FinanzasDtos.CategoriaRequest request) {
        return service.crearCategoria(ContextoUsuario.empresa(jwt), request);
    }

    @PatchMapping("/categorias-costo/{id}/inactivar")
    @PreAuthorize("hasAuthority('COSTOS_PROYECTO_GESTIONAR')")
    public FinanzasDtos.CategoriaResponse inactivarCategoria(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return service.inactivarCategoria(ContextoUsuario.empresa(jwt), id);
    }

    @GetMapping("/presupuestos")
    @PreAuthorize("hasAuthority('PRESUPUESTOS_VER')")
    public PaginaResponse<FinanzasDtos.PresupuestoResponse> presupuestos(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) EstadoPresupuesto estado,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.presupuestos(ContextoUsuario.empresa(jwt), proyectoId, estado, pagina, tamano);
    }

    @PostMapping("/presupuestos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COSTOS_PROYECTO_GESTIONAR')")
    public FinanzasDtos.PresupuestoResponse crearPresupuesto(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FinanzasDtos.PresupuestoRequest request) {
        return service.crearPresupuesto(ContextoUsuario.empresa(jwt), request);
    }

    @PutMapping("/presupuestos/{id}")
    @PreAuthorize("hasAuthority('COSTOS_PROYECTO_GESTIONAR')")
    public FinanzasDtos.PresupuestoResponse actualizarPresupuesto(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id, @Valid @RequestBody FinanzasDtos.PresupuestoRequest request) {
        return service.actualizarPresupuesto(ContextoUsuario.empresa(jwt), id, request);
    }

    @PatchMapping("/presupuestos/{id}/aprobar")
    @PreAuthorize("hasAuthority('PRESUPUESTOS_APROBAR')")
    public FinanzasDtos.PresupuestoResponse aprobarPresupuesto(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return service.aprobarPresupuesto(ContextoUsuario.empresa(jwt), id);
    }

    @PatchMapping("/presupuestos/{id}/cerrar")
    @PreAuthorize("hasAuthority('PRESUPUESTOS_APROBAR')")
    public FinanzasDtos.PresupuestoResponse cerrarPresupuesto(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return service.cerrarPresupuesto(ContextoUsuario.empresa(jwt), id);
    }

    @PatchMapping("/presupuestos/{id}/anular")
    @PreAuthorize("hasAuthority('PRESUPUESTOS_APROBAR')")
    public FinanzasDtos.PresupuestoResponse anularPresupuesto(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return service.anularPresupuesto(ContextoUsuario.empresa(jwt), id);
    }

    @GetMapping("/costos")
    @PreAuthorize("hasAuthority('COSTOS_PROYECTO_VER')")
    public PaginaResponse<FinanzasDtos.CostoResponse> costos(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long proyectoId, @RequestParam(required = false) EstadoCosto estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.costos(ContextoUsuario.empresa(jwt), proyectoId, estado, desde, hasta, pagina, tamano);
    }

    @PostMapping("/costos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COSTOS_PROYECTO_GESTIONAR')")
    public FinanzasDtos.CostoResponse registrarCosto(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FinanzasDtos.CostoRequest request) {
        return service.registrarCosto(ContextoUsuario.empresa(jwt), request);
    }

    @PatchMapping("/costos/{id}/aprobar")
    @PreAuthorize("hasAuthority('PRESUPUESTOS_APROBAR')")
    public FinanzasDtos.CostoResponse aprobarCosto(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.aprobarCosto(ContextoUsuario.empresa(jwt), id);
    }

    @PatchMapping("/costos/{id}/pagar")
    @PreAuthorize("hasAuthority('COSTOS_PROYECTO_GESTIONAR')")
    public FinanzasDtos.CostoResponse pagarCosto(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.pagarCosto(ContextoUsuario.empresa(jwt), id);
    }

    @PatchMapping("/costos/{id}/anular")
    @PreAuthorize("hasAuthority('PRESUPUESTOS_APROBAR')")
    public FinanzasDtos.CostoResponse anularCosto(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.anularCosto(ContextoUsuario.empresa(jwt), id);
    }

    @GetMapping("/rentabilidad")
    @PreAuthorize("hasAuthority('RENTABILIDAD_VER')")
    public FinanzasDtos.RentabilidadResponse rentabilidad(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.rentabilidad(ContextoUsuario.empresa(jwt), proyectoId, desde, hasta);
    }
}
