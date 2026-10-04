package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConciliacionBancaria;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConciliacionRepository extends JpaRepository<ConciliacionBancaria, Long> {
    List<ConciliacionBancaria> findByEmpresaIdOrderByCreadoEnDesc(Long empresaId);
    Optional<ConciliacionBancaria> findByIdAndEmpresaId(Long id, Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConciliacionBancaria c where c.id = :id and c.empresaId = :empresaId")
    Optional<ConciliacionBancaria> findForUpdate(@Param("id") Long id, @Param("empresaId") Long empresaId);
}
