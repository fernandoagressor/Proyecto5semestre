package com.inmobiliaria.inmobiliaria_api.repository.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.MetaComercial;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MetaComercialRepository extends JpaRepository<MetaComercial, Long>, JpaSpecificationExecutor<MetaComercial> {
    Optional<MetaComercial> findByIdAndEmpresaId(Long id, Long empresaId);
}
