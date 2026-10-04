package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.Refinanciacion;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefinanciacionRepository extends JpaRepository<Refinanciacion, Long> {
    List<Refinanciacion> findByEmpresaIdOrderByCreadoEnDesc(Long empresaId);
    Optional<Refinanciacion> findByIdAndEmpresaId(Long id, Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refinanciacion r where r.id = :id and r.empresaId = :empresaId")
    Optional<Refinanciacion> findForUpdate(@Param("id") Long id, @Param("empresaId") Long empresaId);
}
