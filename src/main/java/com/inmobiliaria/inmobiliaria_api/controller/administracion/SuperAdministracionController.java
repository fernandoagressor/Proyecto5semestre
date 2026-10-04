package com.inmobiliaria.inmobiliaria_api.controller.administracion;

import com.inmobiliaria.inmobiliaria_api.dto.administracion.AdministracionDtos;
import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.empresa.EmpresaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.CambioEstadoProyectoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.ProyectoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.ProyectoResponse;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoProyecto;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.administracion.AdministracionEmpresaService;
import com.inmobiliaria.inmobiliaria_api.service.administracion.AdministracionRolService;
import com.inmobiliaria.inmobiliaria_api.service.administracion.AdministracionUsuarioService;
import com.inmobiliaria.inmobiliaria_api.service.administracion.AnaliticaGlobalService;
import com.inmobiliaria.inmobiliaria_api.service.inventario.ProyectoService;
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

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class SuperAdministracionController {

    private final AdministracionEmpresaService empresaService;
    private final AnaliticaGlobalService analiticaGlobalService;
    private final AdministracionUsuarioService usuarioService;
    private final AdministracionRolService rolService;
    private final ProyectoService proyectoService;

    public SuperAdministracionController(
            AdministracionEmpresaService empresaService,
            AnaliticaGlobalService analiticaGlobalService,
            AdministracionUsuarioService usuarioService,
            AdministracionRolService rolService,
            ProyectoService proyectoService
    ) {
        this.empresaService = empresaService;
        this.analiticaGlobalService = analiticaGlobalService;
        this.usuarioService = usuarioService;
        this.rolService = rolService;
        this.proyectoService = proyectoService;
    }

    @GetMapping("/resumen")
    public AdministracionDtos.ResumenGlobalResponse resumen() {
        return empresaService.resumen();
    }

    @GetMapping("/analitica")
    public AdministracionDtos.AnaliticaGlobalResponse analitica() {
        return analiticaGlobalService.obtener();
    }

    @GetMapping("/empresas")
    public PaginaResponse<AdministracionDtos.EmpresaResponse> listarEmpresas(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return empresaService.listar(texto, estado, pagina, tamano);
    }

    @GetMapping("/empresas/{empresaId}")
    public AdministracionDtos.EmpresaResponse obtenerEmpresa(
            @PathVariable Long empresaId
    ) {
        return empresaService.obtener(empresaId);
    }

    @PostMapping("/empresas")
    @ResponseStatus(HttpStatus.CREATED)
    public AdministracionDtos.EmpresaResponse crearEmpresa(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdministracionDtos.EmpresaCrearRequest request
    ) {
        return empresaService.crear(actor(jwt), request);
    }

    @PutMapping("/empresas/{empresaId}")
    public AdministracionDtos.EmpresaResponse actualizarEmpresa(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long empresaId,
            @Valid @RequestBody AdministracionDtos.EmpresaActualizarRequest request
    ) {
        return empresaService.actualizar(actor(jwt), empresaId, request);
    }

    @PatchMapping("/empresas/{empresaId}/estado")
    public AdministracionDtos.EmpresaResponse cambiarEstadoEmpresa(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long empresaId,
            @Valid @RequestBody AdministracionDtos.CambioEstadoEmpresaRequest request
    ) {
        return empresaService.cambiarEstado(actor(jwt), empresaId, request);
    }

    @GetMapping("/empresas/{empresaId}/usuarios")
    public List<AdministracionDtos.UsuarioResponse> listarUsuarios(
            @PathVariable Long empresaId,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String estado
    ) {
        return usuarioService.listar(empresaId, texto, estado);
    }

    @PostMapping("/empresas/{empresaId}/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public AdministracionDtos.UsuarioResponse crearUsuario(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long empresaId,
            @Valid @RequestBody AdministracionDtos.UsuarioCrearRequest request
    ) {
        return usuarioService.crear(actor(jwt), empresaId, request);
    }

    @PutMapping("/empresas/{empresaId}/usuarios/{usuarioId}")
    public AdministracionDtos.UsuarioResponse actualizarUsuario(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long empresaId,
            @PathVariable Long usuarioId,
            @Valid @RequestBody AdministracionDtos.UsuarioActualizarRequest request
    ) {
        return usuarioService.actualizar(
                actor(jwt),
                empresaId,
                usuarioId,
                request
        );
    }

    @PatchMapping("/empresas/{empresaId}/usuarios/{usuarioId}/estado")
    public AdministracionDtos.UsuarioResponse cambiarEstadoUsuario(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long empresaId,
            @PathVariable Long usuarioId,
            @Valid @RequestBody AdministracionDtos.CambioEstadoUsuarioRequest request
    ) {
        return usuarioService.cambiarEstado(
                actor(jwt),
                empresaId,
                usuarioId,
                request
        );
    }

    @PostMapping("/empresas/{empresaId}/usuarios/{usuarioId}/credenciales")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reiniciarCredenciales(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long empresaId,
            @PathVariable Long usuarioId,
            @Valid @RequestBody AdministracionDtos.ReinicioCredencialesRequest request
    ) {
        usuarioService.reiniciarCredenciales(
                actor(jwt),
                empresaId,
                usuarioId,
                request
        );
    }

    @GetMapping("/empresas/{empresaId}/roles")
    public List<AdministracionDtos.RolResponse> listarRoles(
            @PathVariable Long empresaId
    ) {
        return rolService.listar(empresaId);
    }

    @PostMapping("/empresas/{empresaId}/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public AdministracionDtos.RolResponse crearRol(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long empresaId,
            @Valid @RequestBody AdministracionDtos.RolGuardarRequest request
    ) {
        return rolService.crear(actor(jwt), empresaId, request);
    }

    @PutMapping("/empresas/{empresaId}/roles/{rolId}")
    public AdministracionDtos.RolResponse actualizarRol(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long empresaId,
            @PathVariable Long rolId,
            @Valid @RequestBody AdministracionDtos.RolGuardarRequest request
    ) {
        return rolService.actualizar(actor(jwt), empresaId, rolId, request);
    }

    @GetMapping("/permisos")
    public List<AdministracionDtos.PermisoResponse> listarPermisos() {
        return rolService.permisos();
    }

    @GetMapping("/empresas/{empresaId}/proyectos")
    public PaginaResponse<ProyectoResponse> listarProyectos(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long empresaId,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoProyecto estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return proyectoService.listar(
                contexto(jwt, empresaId),
                texto,
                estado,
                pagina,
                tamano
        );
    }

    @PostMapping("/empresas/{empresaId}/proyectos")
    @ResponseStatus(HttpStatus.CREATED)
    public ProyectoResponse crearProyecto(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long empresaId,
            @Valid @RequestBody ProyectoRequest request
    ) {
        return proyectoService.crear(contexto(jwt, empresaId), request);
    }

    @PutMapping("/empresas/{empresaId}/proyectos/{proyectoId}")
    public ProyectoResponse actualizarProyecto(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long empresaId,
            @PathVariable Long proyectoId,
            @Valid @RequestBody ProyectoRequest request
    ) {
        return proyectoService.actualizar(
                contexto(jwt, empresaId),
                proyectoId,
                request
        );
    }

    @PatchMapping("/empresas/{empresaId}/proyectos/{proyectoId}/estado")
    public ProyectoResponse cambiarEstadoProyecto(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long empresaId,
            @PathVariable Long proyectoId,
            @Valid @RequestBody CambioEstadoProyectoRequest request
    ) {
        return proyectoService.cambiarEstado(
                contexto(jwt, empresaId),
                proyectoId,
                request.estado()
        );
    }

    private Long actor(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    private ContextoUsuario contexto(Jwt jwt, Long empresaId) {
        return new ContextoUsuario(actor(jwt), empresaId);
    }
}
