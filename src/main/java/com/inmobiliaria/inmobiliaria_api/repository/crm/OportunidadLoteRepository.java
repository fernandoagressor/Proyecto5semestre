package com.inmobiliaria.inmobiliaria_api.repository.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.OportunidadLote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OportunidadLoteRepository
        extends JpaRepository<OportunidadLote, Long> {

    List<OportunidadLote> findByOportunidadIdOrderByPrioridadAsc(
            Long oportunidadId
    );

    Optional<OportunidadLote> findByOportunidadIdAndLoteId(
            Long oportunidadId,
            Long loteId
    );

    boolean existsByOportunidadIdAndLoteId(Long oportunidadId, Long loteId);

    long countByOportunidadId(Long oportunidadId);
}
