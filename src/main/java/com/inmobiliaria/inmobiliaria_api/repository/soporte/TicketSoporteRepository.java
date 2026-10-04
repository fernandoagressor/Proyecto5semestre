package com.inmobiliaria.inmobiliaria_api.repository.soporte;

import com.inmobiliaria.inmobiliaria_api.entity.soporte.TicketSoporte;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketSoporteRepository extends JpaRepository<TicketSoporte, Long>, JpaSpecificationExecutor<TicketSoporte> {
    Optional<TicketSoporte> findByIdAndEmpresaId(Long id, Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketSoporte t where t.id = :id and t.empresaId = :empresaId")
    Optional<TicketSoporte> findForUpdate(@Param("id") Long id, @Param("empresaId") Long empresaId);
}
