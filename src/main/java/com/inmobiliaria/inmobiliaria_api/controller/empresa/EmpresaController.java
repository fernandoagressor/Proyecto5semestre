package com.inmobiliaria.inmobiliaria_api.controller.empresa;

import com.inmobiliaria.inmobiliaria_api.dto.empresa.EmpresaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.empresa.UsuarioDirectorioResponse;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.empresa.DirectorioUsuariosService;
import com.inmobiliaria.inmobiliaria_api.service.empresa.EmpresaService;
import java.util.List;
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
    private final DirectorioUsuariosService directorioUsuarios;

    public EmpresaController(EmpresaService empresaService, DirectorioUsuariosService directorioUsuarios) {
        this.empresaService = empresaService;
        this.directorioUsuarios = directorioUsuarios;
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

    /**
     * Equipo de la empresa del token. Lo usan las pantallas para mostrar nombres (asesor de una
     * meta, autor de un evento, responsable de una alerta) en lugar de ids.
     */
    @GetMapping("/actual/usuarios")
    @PreAuthorize("isAuthenticated()")
    public List<UsuarioDirectorioResponse> usuariosActual(@AuthenticationPrincipal Jwt jwt) {
        return directorioUsuarios.listar(ContextoUsuario.empresa(jwt));
    }
}
