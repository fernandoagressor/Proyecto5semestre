package com.inmobiliaria.inmobiliaria_api.service.empresa;

import com.inmobiliaria.inmobiliaria_api.dto.empresa.UsuarioDirectorioResponse;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Directorio de usuarios de la empresa del token (nombre, usuario, estado y roles). */
@Service
public class DirectorioUsuariosService {

    private final JdbcTemplate jdbcTemplate;

    public DirectorioUsuariosService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<UsuarioDirectorioResponse> listar(ContextoUsuario contexto) {
        return jdbcTemplate.query("""
                SELECT u.id,
                       COALESCE(NULLIF(TRIM(CONCAT(p.nombres, ' ', COALESCE(p.apellidos, ''))), ''),
                                u.nombre_usuario) AS nombre,
                       u.nombre_usuario, u.estado,
                       GROUP_CONCAT(DISTINCT r.codigo ORDER BY r.codigo SEPARATOR ',') AS roles
                FROM usuarios u
                LEFT JOIN personas p ON p.id = u.persona_id
                LEFT JOIN usuario_roles ur ON ur.usuario_id = u.id
                LEFT JOIN roles r ON r.id = ur.rol_id AND r.activo = 1
                WHERE u.empresa_id = ?
                GROUP BY u.id, p.nombres, p.apellidos, u.nombre_usuario, u.estado
                ORDER BY nombre
                """,
                (rs, i) -> new UsuarioDirectorioResponse(
                        rs.getLong("id"),
                        rs.getString("nombre"),
                        rs.getString("nombre_usuario"),
                        rs.getString("estado"),
                        roles(rs.getString("roles"))),
                contexto.empresaId());
    }

    private static List<String> roles(String valor) {
        return valor == null || valor.isBlank() ? List.of() : Arrays.asList(valor.split(","));
    }
}
