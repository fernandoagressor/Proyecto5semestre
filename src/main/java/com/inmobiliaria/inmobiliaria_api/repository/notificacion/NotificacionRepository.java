package com.inmobiliaria.inmobiliaria_api.repository.notificacion;

import com.inmobiliaria.inmobiliaria_api.entity.notificacion.Notificacion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long>, JpaSpecificationExecutor<Notificacion> {
    Optional<Notificacion> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Query("""
            select count(n) from Notificacion n
            where n.usuarioId = :usuarioId and n.leidaEn is null
              and (n.expiraEn is null or n.expiraEn > current_timestamp)
            """)
    long countVigentesNoLeidas(@Param("usuarioId") Long usuarioId);
}
