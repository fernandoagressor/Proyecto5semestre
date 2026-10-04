package com.inmobiliaria.inmobiliaria_api.repository.ventas.reserva;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.EstadoReserva;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.Reserva;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaRepository
        extends JpaRepository<Reserva, Long>, JpaSpecificationExecutor<Reserva> {

    Optional<Reserva> findByIdAndEmpresaId(Long id, Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from Reserva r
            where r.id = :id
              and r.empresaId = :empresaId
            """)
    Optional<Reserva> findForUpdate(
            @Param("id") Long id,
            @Param("empresaId") Long empresaId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from Reserva r
            where r.estado in :estados
              and r.fechaVencimiento <= :ahora
            order by r.fechaVencimiento asc
            """)
    List<Reserva> findVencidasForUpdate(
            @Param("estados") Collection<EstadoReserva> estados,
            @Param("ahora") LocalDateTime ahora,
            Pageable pageable
    );
}
