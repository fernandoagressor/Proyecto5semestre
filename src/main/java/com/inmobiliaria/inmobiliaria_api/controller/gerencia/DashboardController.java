package com.inmobiliaria.inmobiliaria_api.controller.gerencia;

import com.inmobiliaria.inmobiliaria_api.dto.gerencia.DashboardDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.DashboardService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping("/gerencial")
    @PreAuthorize("hasAuthority('DASHBOARD_GERENCIAL_VER')")
    public DashboardDtos.ResumenGerencial gerencial(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.gerencial(ContextoUsuario.empresa(jwt), proyectoId, desde, hasta);
    }

    @GetMapping("/comercial")
    @PreAuthorize("hasAuthority('DASHBOARD_COMERCIAL_VER')")
    public DashboardDtos.ResumenComercial comercial(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.comercial(ContextoUsuario.empresa(jwt), proyectoId, desde, hasta);
    }

    @GetMapping("/cartera")
    @PreAuthorize("hasAuthority('DASHBOARD_CARTERA_VER')")
    public DashboardDtos.ResumenCartera cartera(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate corte) {
        return service.cartera(ContextoUsuario.empresa(jwt), proyectoId, corte);
    }

    @GetMapping("/servicio")
    @PreAuthorize("hasAuthority('DASHBOARD_SERVICIO_VER')")
    public DashboardDtos.ResumenServicio servicio(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.servicio(ContextoUsuario.empresa(jwt), proyectoId, desde, hasta);
    }
}
