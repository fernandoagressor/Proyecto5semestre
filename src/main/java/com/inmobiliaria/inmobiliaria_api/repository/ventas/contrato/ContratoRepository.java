package com.inmobiliaria.inmobiliaria_api.repository.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.Contrato;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContratoRepository
        extends JpaRepository<Contrato, Long>, JpaSpecificationExecutor<Contrato> {

    Optional<Contrato> findByIdAndEmpresaId(Long id, Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from Contrato c
            where c.id = :id
              and c.empresaId = :empresaId
            """)
    Optional<Contrato> findForUpdate(
            @Param("id") Long id,
            @Param("empresaId") Long empresaId
    );
}
