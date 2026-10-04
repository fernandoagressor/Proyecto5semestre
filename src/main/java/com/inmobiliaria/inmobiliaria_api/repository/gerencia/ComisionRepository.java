package com.inmobiliaria.inmobiliaria_api.repository.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.Comision;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ComisionRepository extends JpaRepository<Comision, Long>, JpaSpecificationExecutor<Comision> {
    Optional<Comision> findByIdAndEmpresaId(Long id, Long empresaId);
    boolean existsByContratoIdAndAsesorIdAndReglaComisionId(Long contratoId, Long asesorId, Long reglaComisionId);
}
