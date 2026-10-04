package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.Factura;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacturaRepository extends JpaRepository<Factura, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Factura f where f.id = :id and f.empresaId = :empresaId")
    Optional<Factura> findForUpdate(@Param("id") Long id, @Param("empresaId") Long empresaId);
}
