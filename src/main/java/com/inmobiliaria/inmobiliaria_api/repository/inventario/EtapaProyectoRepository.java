package com.inmobiliaria.inmobiliaria_api.repository.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.EtapaProyecto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtapaProyectoRepository extends JpaRepository<EtapaProyecto, Long> {

    List<EtapaProyecto> findByEmpresaIdAndProyectoIdOrderBySecuencia(
            Long empresaId,
            Long proyectoId
    );

    Optional<EtapaProyecto> findByIdAndEmpresaIdAndProyectoId(
            Long id,
            Long empresaId,
            Long proyectoId
    );

    boolean existsByProyectoIdAndCodigoIgnoreCase(Long proyectoId, String codigo);

    boolean existsByProyectoIdAndCodigoIgnoreCaseAndIdNot(
            Long proyectoId,
            String codigo,
            Long id
    );

    boolean existsByProyectoIdAndSecuencia(Long proyectoId, Integer secuencia);

    boolean existsByProyectoIdAndSecuenciaAndIdNot(
            Long proyectoId,
            Integer secuencia,
            Long id
    );
}
