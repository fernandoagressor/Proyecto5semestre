package com.inmobiliaria.inmobiliaria_api.service.administracion;

import com.inmobiliaria.inmobiliaria_api.dto.administracion.AdministracionDtos;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usuarios y roles administrados por la propia empresa (sin superadministración).
 *
 * <p>Reutiliza los servicios de administración, que ya aíslan los datos por empresa y protegen
 * los roles del sistema y la cuenta SUPER_ADMIN, y añade la regla de delegación: nadie puede
 * otorgar, ni a otros ni a sí mismo, un permiso que no tiene. Por eso se exige que:
 * <ul>
 *   <li>los roles asignados y los permisos de un rol sean un subconjunto de los del actor;</li>
 *   <li>el usuario o rol que se modifica no tenga permisos que el actor no tiene;</li>
 *   <li>el actor no cambie sus propios roles, estado ni credenciales desde aquí.</li>
 * </ul>
 */
@Service
public class SeguridadEmpresaService {

    private final AdministracionUsuarioService usuarios;
    private final AdministracionRolService roles;
    private final UsuarioRepository usuarioRepository;
    private final NamedParameterJdbcTemplate jdbc;

    public SeguridadEmpresaService(
            AdministracionUsuarioService usuarios,
            AdministracionRolService roles,
            UsuarioRepository usuarioRepository,
            NamedParameterJdbcTemplate jdbc
    ) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.usuarioRepository = usuarioRepository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AdministracionDtos.UsuarioResponse> listarUsuarios(
            ContextoUsuario ctx, String texto, String estado) {
        return usuarios.listar(ctx.empresaId(), texto, estado);
    }

    @Transactional
    public AdministracionDtos.UsuarioResponse crearUsuario(
            ContextoUsuario ctx, AdministracionDtos.UsuarioCrearRequest request) {
        exigirSubconjunto(ctx, permisosDeRoles(request.rolIds()), "asignar roles con permisos");
        return usuarios.crear(ctx.usuarioId(), ctx.empresaId(), request);
    }

    @Transactional
    public AdministracionDtos.UsuarioResponse actualizarUsuario(
            ContextoUsuario ctx, Long usuarioId, AdministracionDtos.UsuarioActualizarRequest request) {
        exigirUsuarioGestionable(ctx, usuarioId);
        exigirSubconjunto(ctx, permisosDeRoles(request.rolIds()), "asignar roles con permisos");
        return usuarios.actualizar(ctx.usuarioId(), ctx.empresaId(), usuarioId, request);
    }

    @Transactional
    public AdministracionDtos.UsuarioResponse cambiarEstadoUsuario(
            ContextoUsuario ctx, Long usuarioId, AdministracionDtos.CambioEstadoUsuarioRequest request) {
        exigirUsuarioGestionable(ctx, usuarioId);
        return usuarios.cambiarEstado(ctx.usuarioId(), ctx.empresaId(), usuarioId, request);
    }

    @Transactional
    public void reiniciarCredenciales(
            ContextoUsuario ctx, Long usuarioId, AdministracionDtos.ReinicioCredencialesRequest request) {
        exigirUsuarioGestionable(ctx, usuarioId);
        usuarios.reiniciarCredenciales(ctx.usuarioId(), ctx.empresaId(), usuarioId, request);
    }

    @Transactional(readOnly = true)
    public List<AdministracionDtos.RolResponse> listarRoles(ContextoUsuario ctx) {
        return roles.listar(ctx.empresaId());
    }

    @Transactional(readOnly = true)
    public List<AdministracionDtos.PermisoResponse> permisos() {
        return roles.permisos();
    }

    @Transactional
    public AdministracionDtos.RolResponse crearRol(
            ContextoUsuario ctx, AdministracionDtos.RolGuardarRequest request) {
        exigirSubconjunto(ctx, codigosDePermisos(request.permisoIds()), "otorgar permisos");
        return roles.crear(ctx.usuarioId(), ctx.empresaId(), request);
    }

    @Transactional
    public AdministracionDtos.RolResponse actualizarRol(
            ContextoUsuario ctx, Long rolId, AdministracionDtos.RolGuardarRequest request) {
        exigirSubconjunto(ctx, permisosDeRoles(List.of(rolId)), "modificar un rol con permisos");
        exigirSubconjunto(ctx, codigosDePermisos(request.permisoIds()), "otorgar permisos");
        return roles.actualizar(ctx.usuarioId(), ctx.empresaId(), rolId, request);
    }

    private void exigirUsuarioGestionable(ContextoUsuario ctx, Long usuarioId) {
        if (ctx.usuarioId().equals(usuarioId)) {
            throw new AccesoDenegadoException(
                    "Su propia cuenta no se administra desde aquí; pida el cambio a otro administrador");
        }
        exigirSubconjunto(ctx, Set.copyOf(usuarioRepository.findPermissionCodes(usuarioId)),
                "administrar a un usuario con permisos");
    }

    private void exigirSubconjunto(ContextoUsuario ctx, Set<String> requeridos, String accion) {
        if (requeridos.isEmpty()) {
            return;
        }
        Set<String> propios = new HashSet<>(usuarioRepository.findPermissionCodes(ctx.usuarioId()));
        Set<String> faltantes = new TreeSet<>(requeridos);
        faltantes.removeAll(propios);
        if (!faltantes.isEmpty()) {
            throw new AccesoDenegadoException(
                    "No puede " + accion + " que usted no tiene: " + String.join(", ", faltantes));
        }
    }

    private Set<String> permisosDeRoles(Collection<Long> rolIds) {
        if (rolIds == null || rolIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT p.codigo
                FROM roles r
                JOIN rol_permisos rp ON rp.rol_id = r.id AND rp.concedido = 1
                JOIN permisos p ON p.id = rp.permiso_id AND p.activo = 1
                WHERE r.id IN (:ids)
                """, new MapSqlParameterSource("ids", rolIds), String.class));
    }

    private Set<String> codigosDePermisos(Collection<Long> permisoIds) {
        if (permisoIds == null || permisoIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
                "SELECT codigo FROM permisos WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", permisoIds), String.class));
    }
}
