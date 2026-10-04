package com.inmobiliaria.inmobiliaria_api.repository.seguridad;

import com.inmobiliaria.inmobiliaria_api.entity.seguridad.SesionUsuario;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SesionUsuarioRepository extends JpaRepository<SesionUsuario, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from SesionUsuario s
            where s.tokenHash = :tokenHash
              and s.revocadaEn is null
            """)
    Optional<SesionUsuario> findActiveForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SesionUsuario s
            set s.revocadaEn = :revocadaEn,
                s.motivoRevocacion = :motivo
            where s.usuarioId = :usuarioId
              and s.revocadaEn is null
            """)
    int revokeAllActive(
            @Param("usuarioId") Long usuarioId,
            @Param("revocadaEn") LocalDateTime revocadaEn,
            @Param("motivo") String motivo
    );
}
