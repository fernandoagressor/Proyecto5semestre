package com.inmobiliaria.inmobiliaria_api.repository.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.PresupuestoProyecto;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PresupuestoProyectoRepository extends JpaRepository<PresupuestoProyecto, Long>, JpaSpecificationExecutor<PresupuestoProyecto> {
    Optional<PresupuestoProyecto> findByIdAndEmpresaId(Long id, Long empresaId);
}
