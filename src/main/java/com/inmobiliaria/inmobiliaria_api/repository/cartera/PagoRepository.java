package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.Pago;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoRepository extends JpaRepository<Pago, Long>, JpaSpecificationExecutor<Pago> {
    Optional<Pago> findByIdAndEmpresaId(Long id, Long empresaId);
    boolean existsByEmpresaIdAndReferencia(Long empresaId, String referencia);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pago p where p.id = :id and p.empresaId = :empresaId")
    Optional<Pago> findForUpdate(@Param("id") Long id, @Param("empresaId") Long empresaId);
}
