package com.inmobiliaria.inmobiliaria_api.repository.seguridad;

import com.inmobiliaria.inmobiliaria_api.entity.seguridad.EstadoUsuario;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("""
            select u
            from Usuario u
            where lower(u.correo) = lower(:identificador)
               or lower(u.nombreUsuario) = lower(:identificador)
            """)
    Optional<Usuario> findByIdentificador(@Param("identificador") String identificador);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :usuarioId")
    Optional<Usuario> findByIdForUpdate(@Param("usuarioId") Long usuarioId);

    /**
     * Marca un inicio de sesión exitoso sin pasar por la versión de la entidad: dos inicios de sesión
     * simultáneos del mismo usuario (dos dispositivos, o pruebas de carga) chocaban por bloqueo
     * optimista y el segundo respondía 409.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE usuarios
            SET ultimo_acceso_en = :ahora, intentos_fallidos = 0, bloqueado_hasta = NULL
            WHERE id = :usuarioId
            """, nativeQuery = true)
    int registrarAccesoExitoso(@Param("usuarioId") Long usuarioId, @Param("ahora") LocalDateTime ahora);

    @Query("select u.tokenVersion from Usuario u where u.id = :usuarioId")
    Optional<Long> findTokenVersionById(@Param("usuarioId") Long usuarioId);

    @Query(value = """
            SELECT DISTINCT r.codigo
            FROM usuario_roles ur
            JOIN roles r ON r.id = ur.rol_id
            WHERE ur.usuario_id = :usuarioId
              AND r.activo = 1
            """, nativeQuery = true)
    List<String> findRoleCodes(@Param("usuarioId") Long usuarioId);

    @Query(value = """
            SELECT DISTINCT p.codigo
            FROM usuario_roles ur
            JOIN roles r ON r.id = ur.rol_id
            JOIN rol_permisos rp ON rp.rol_id = r.id AND rp.concedido = 1
            JOIN permisos p ON p.id = rp.permiso_id
            WHERE ur.usuario_id = :usuarioId
              AND r.activo = 1
              AND p.activo = 1
            """, nativeQuery = true)
    List<String> findPermissionCodes(@Param("usuarioId") Long usuarioId);

    boolean existsByIdAndEmpresaIdAndEstado(
            Long id,
            Long empresaId,
            EstadoUsuario estado
    );

    long countByEmpresaId(Long empresaId);
}
