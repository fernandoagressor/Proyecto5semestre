package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConciliacionMovimiento;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoMovimientoConciliacion;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConciliacionMovimientoRepository extends JpaRepository<ConciliacionMovimiento, Long> {
    List<ConciliacionMovimiento> findByConciliacionIdOrderByFechaMovimientoAscIdAsc(Long conciliacionId);
    long countByConciliacionIdAndEstadoIn(Long conciliacionId, Collection<EstadoMovimientoConciliacion> estados);

    @Query("select coalesce(sum(m.valor), 0) from ConciliacionMovimiento m where m.conciliacionId = :id and m.estado = :estado")
    BigDecimal totalPorEstado(@Param("id") Long id, @Param("estado") EstadoMovimientoConciliacion estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ConciliacionMovimiento m where m.id = :id and m.conciliacionId = :conciliacionId")
    Optional<ConciliacionMovimiento> findForUpdate(@Param("id") Long id, @Param("conciliacionId") Long conciliacionId);
}
