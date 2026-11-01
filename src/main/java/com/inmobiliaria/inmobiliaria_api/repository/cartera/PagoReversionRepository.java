package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoReversion;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.PagoReversion;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoReversionRepository extends JpaRepository<PagoReversion, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PagoReversion r where r.id = :id and r.pagoId = :pagoId")
    Optional<PagoReversion> findForUpdate(@Param("id") Long id, @Param("pagoId") Long pagoId);

    /** Solicitudes de reversión de los pagos de una empresa, las más recientes primero (máximo 100). */
    @Query("""
            select r from PagoReversion r, Pago p
            where p.id = r.pagoId and p.empresaId = :empresaId
              and (:estado is null or r.estado = :estado)
            order by r.id desc
            """)
    List<PagoReversion> findDeEmpresa(@Param("empresaId") Long empresaId, @Param("estado") EstadoReversion estado,
                                      org.springframework.data.domain.Pageable pageable);
}
