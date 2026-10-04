package com.inmobiliaria.inmobiliaria_api.repository.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.Oportunidad;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OportunidadRepository
        extends JpaRepository<Oportunidad, Long>,
        JpaSpecificationExecutor<Oportunidad> {

    Optional<Oportunidad> findByIdAndEmpresaId(Long id, Long empresaId);

    boolean existsByProspectoIdAndEstado(
            Long prospectoId,
            com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoOportunidad estado
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Oportunidad o
            where o.id = :id
              and o.empresaId = :empresaId
            """)
    Optional<Oportunidad> findForUpdate(
            @Param("id") Long id,
            @Param("empresaId") Long empresaId
    );
}
