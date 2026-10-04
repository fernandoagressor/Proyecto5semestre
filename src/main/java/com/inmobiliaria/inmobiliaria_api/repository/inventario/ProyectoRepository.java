package com.inmobiliaria.inmobiliaria_api.repository.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.Proyecto;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}
