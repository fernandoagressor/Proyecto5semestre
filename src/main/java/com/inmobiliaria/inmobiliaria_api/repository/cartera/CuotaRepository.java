package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.Cuota;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoCuota;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CuotaRepository extends JpaRepository<Cuota, Long>, JpaSpecificationExecutor<Cuota> {
    List<Cuota> findByPlanPagoIdOrderByNumeroAsc(Long planPagoId);
    List<Cuota> findByContratoIdAndEstadoIn(Long contratoId, Collection<EstadoCuota> estados);
    Optional<Cuota> findByIdAndEmpresaId(Long id, Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuota c where c.id = :id and c.empresaId = :empresaId")
    Optional<Cuota> findForUpdate(@Param("id") Long id, @Param("empresaId") Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuota c where c.empresaId = :empresaId and c.fechaVencimiento < :fecha and c.saldo > 0 and c.estado in :estados order by c.fechaVencimiento")
    List<Cuota> findVencidasForUpdate(@Param("empresaId") Long empresaId, @Param("fecha") LocalDate fecha, @Param("estados") Collection<EstadoCuota> estados, Pageable pageable);

    long countByPlanPagoIdAndSaldoGreaterThan(Long planPagoId, java.math.BigDecimal saldo);
}
