package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoPlanPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.PlanPago;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanPagoRepository extends JpaRepository<PlanPago, Long> {
    List<PlanPago> findByContratoIdAndEmpresaIdOrderByVersionPlanDesc(Long contratoId, Long empresaId);
    Optional<PlanPago> findByIdAndEmpresaId(Long id, Long empresaId);
    Optional<PlanPago> findByContratoIdAndEmpresaIdAndEstado(Long contratoId, Long empresaId, EstadoPlanPago estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PlanPago p where p.id = :id and p.empresaId = :empresaId")
    Optional<PlanPago> findForUpdate(@Param("id") Long id, @Param("empresaId") Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PlanPago p where p.contratoId = :contratoId and p.empresaId = :empresaId and p.estado = :estado")
    Optional<PlanPago> findActivoForUpdate(@Param("contratoId") Long contratoId, @Param("empresaId") Long empresaId, @Param("estado") EstadoPlanPago estado);

    @Query("select coalesce(max(p.versionPlan), 0) from PlanPago p where p.contratoId = :contratoId")
    int maxVersion(@Param("contratoId") Long contratoId);
}
