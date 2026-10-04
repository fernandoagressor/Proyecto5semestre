package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.PagoReversion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoReversionRepository extends JpaRepository<PagoReversion, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PagoReversion r where r.id = :id and r.pagoId = :pagoId")
    Optional<PagoReversion> findForUpdate(@Param("id") Long id, @Param("pagoId") Long pagoId);
}
