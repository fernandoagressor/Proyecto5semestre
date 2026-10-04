package com.inmobiliaria.inmobiliaria_api.repository.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.ReglaComision;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReglaComisionRepository extends JpaRepository<ReglaComision, Long>, JpaSpecificationExecutor<ReglaComision> {
    Optional<ReglaComision> findByIdAndEmpresaId(Long id, Long empresaId);
}
