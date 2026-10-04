package com.inmobiliaria.inmobiliaria_api.repository.empresa;

import com.inmobiliaria.inmobiliaria_api.entity.empresa.Empresa;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmpresaRepository extends JpaRepository<Empresa, Long>, JpaSpecificationExecutor<Empresa> {

    Optional<Empresa> findByCodigo(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);

    boolean existsByNitIgnoreCase(String nit);

    boolean existsByNitIgnoreCaseAndIdNot(String nit, Long id);
}
