package com.inmobiliaria.inmobiliaria_api.repository.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.Proyecto;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProyectoRepository
        extends JpaRepository<Proyecto, Long>, JpaSpecificationExecutor<Proyecto> {

    Optional<Proyecto> findByIdAndEmpresaId(Long id, Long empresaId);

    boolean existsByEmpresaIdAndCodigoIgnoreCase(Long empresaId, String codigo);

    boolean existsByEmpresaIdAndCodigoIgnoreCaseAndIdNot(
            Long empresaId,
            String codigo,
            Long id
    );

    long countByEmpresaId(Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Proyecto p where p.id = :id and p.empresaId = :empresaId")
    Optional<Proyecto> findForUpdate(
            @Param("id") Long id,
            @Param("empresaId") Long empresaId
    );
}
