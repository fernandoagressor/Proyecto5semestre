package com.inmobiliaria.inmobiliaria_api.controller.gerencia;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.ReporteDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.ReporteService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/reportes")
public class ReporteController {
    private final ReporteService service;
    public ReporteController(ReporteService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('REPORTES_GENERAR')")
    public ReporteDtos.Response solicitar(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReporteDtos.SolicitarRequest request) {
        return service.solicitar(ContextoUsuario.empresa(jwt), request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REPORTES_VER')")
    public PaginaResponse<ReporteDtos.Response> listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) String tipoReporte,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(ContextoUsuario.empresa(jwt), proyectoId, tipoReporte, estado, pagina, tamano);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('REPORTES_VER')")
    public ReporteDtos.Response obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.obtener(ContextoUsuario.empresa(jwt), id);
    }

    @PatchMapping("/{id}/completar")
    @PreAuthorize("hasAuthority('REPORTES_PROCESAR')")
    public ReporteDtos.Response completar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody ReporteDtos.CompletarRequest request) {
        return service.completar(ContextoUsuario.empresa(jwt), id, request);
    }
}
