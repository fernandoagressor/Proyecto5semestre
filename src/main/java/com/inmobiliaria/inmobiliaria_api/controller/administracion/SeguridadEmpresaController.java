package com.inmobiliaria.inmobiliaria_api.controller.administracion;

import com.inmobiliaria.inmobiliaria_api.dto.administracion.AdministracionDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.administracion.SeguridadEmpresaService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Usuarios, roles y permisos de la empresa del token (vista "Usuarios y roles"). */
@RestController
@RequestMapping("/api/v1/seguridad")
public class SeguridadEmpresaController {

    private final SeguridadEmpresaService service;

    public SeguridadEmpresaController(SeguridadEmpresaService service) {
        this.service = service;
    }

    @GetMapping("/usuarios")
    @PreAuthorize("hasAuthority('USUARIOS_VER')")
    public List<AdministracionDtos.UsuarioResponse> usuarios(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String estado
    ) {
        return service.listarUsuarios(ContextoUsuario.empresa(jwt), texto, estado);
    }

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USUARIOS_CREAR')")
    public AdministracionDtos.UsuarioResponse crearUsuario(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdministracionDtos.UsuarioCrearRequest request
    ) {
        return service.crearUsuario(ContextoUsuario.empresa(jwt), request);
    }

    @PutMapping("/usuarios/{usuarioId}")
    @PreAuthorize("hasAuthority('USUARIOS_EDITAR')")
    public AdministracionDtos.UsuarioResponse actualizarUsuario(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long usuarioId,
            @Valid @RequestBody AdministracionDtos.UsuarioActualizarRequest request
    ) {
        return service.actualizarUsuario(ContextoUsuario.empresa(jwt), usuarioId, request);
    }

    @PatchMapping("/usuarios/{usuarioId}/estado")
    @PreAuthorize("hasAuthority('USUARIOS_BLOQUEAR')")
    public AdministracionDtos.UsuarioResponse cambiarEstadoUsuario(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long usuarioId,
            @Valid @RequestBody AdministracionDtos.CambioEstadoUsuarioRequest request
    ) {
        return service.cambiarEstadoUsuario(ContextoUsuario.empresa(jwt), usuarioId, request);
    }

    @PostMapping("/usuarios/{usuarioId}/credenciales")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USUARIOS_CREDENCIALES')")
    public void reiniciarCredenciales(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long usuarioId,
            @Valid @RequestBody AdministracionDtos.ReinicioCredencialesRequest request
    ) {
        service.reiniciarCredenciales(ContextoUsuario.empresa(jwt), usuarioId, request);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('ROLES_VER', 'USUARIOS_CREAR', 'USUARIOS_EDITAR')")
    public List<AdministracionDtos.RolResponse> roles(@AuthenticationPrincipal Jwt jwt) {
        return service.listarRoles(ContextoUsuario.empresa(jwt));
    }

    @GetMapping("/permisos")
    @PreAuthorize("hasAuthority('ROLES_VER')")
    public List<AdministracionDtos.PermisoResponse> permisos(@AuthenticationPrincipal Jwt jwt) {
        ContextoUsuario.empresa(jwt);
        return service.permisos();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLES_CREAR')")
    public AdministracionDtos.RolResponse crearRol(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdministracionDtos.RolGuardarRequest request
    ) {
        return service.crearRol(ContextoUsuario.empresa(jwt), request);
    }

    @PutMapping("/roles/{rolId}")
    @PreAuthorize("hasAuthority('ROLES_EDITAR')")
    public AdministracionDtos.RolResponse actualizarRol(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long rolId,
            @Valid @RequestBody AdministracionDtos.RolGuardarRequest request
    ) {
        return service.actualizarRol(ContextoUsuario.empresa(jwt), rolId, request);
    }
}
