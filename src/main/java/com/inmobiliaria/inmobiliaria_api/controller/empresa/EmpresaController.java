package com.inmobiliaria.inmobiliaria_api.controller.empresa;

import com.inmobiliaria.inmobiliaria_api.dto.empresa.EmpresaResponse;
import com.inmobiliaria.inmobiliaria_api.service.empresa.EmpresaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/empresas")
public class EmpresaController {

    private final EmpresaService empresaService;

    public EmpresaController(EmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    @GetMapping("/actual")
    @PreAuthorize("isAuthenticated()")
    public EmpresaResponse obtenerActual(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Object empresaId = jwt.getClaim("empresa_id");
        Long id = empresaId instanceof Number number
                ? number.longValue()
                : null;
        return empresaService.obtenerActual(id);
    }
}
