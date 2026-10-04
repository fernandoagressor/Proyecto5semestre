package com.inmobiliaria.inmobiliaria_api.repository.simulacion;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Simulacion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SimulacionRepository
        extends JpaRepository<Simulacion, Long>,
        JpaSpecificationExecutor<Simulacion> {

    Optional<Simulacion> findByIdAndEmpresaId(Long id, Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from Simulacion s
            where s.id = :id
              and s.empresaId = :empresaId
            """)
    Optional<Simulacion> findForUpdate(
            @Param("id") Long id,
            @Param("empresaId") Long empresaId
    );
}
