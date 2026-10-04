package com.inmobiliaria.inmobiliaria_api.repository.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.ContratoFirmante;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.EstadoFirmante;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContratoFirmanteRepository
        extends JpaRepository<ContratoFirmante, Long> {

    List<ContratoFirmante> findByContratoIdOrderByOrdenFirmaAsc(Long contratoId);

    long countByContratoId(Long contratoId);

    long countByContratoIdAndEstadoNotIn(
            Long contratoId,
            Collection<EstadoFirmante> estados
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select f
            from ContratoFirmante f
            where f.id = :id
              and f.contratoId = :contratoId
            """)
    Optional<ContratoFirmante> findForUpdate(
            @Param("id") Long id,
            @Param("contratoId") Long contratoId
    );
}
