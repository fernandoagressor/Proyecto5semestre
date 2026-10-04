package com.inmobiliaria.inmobiliaria_api.repository.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.Manzana;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManzanaRepository extends JpaRepository<Manzana, Long> {

    List<Manzana> findByEmpresaIdAndProyectoIdAndEtapaIdOrderBySecuenciaAscCodigoAsc(
            Long empresaId,
            Long proyectoId,
            Long etapaId
    );

    Optional<Manzana> findByIdAndEmpresaIdAndProyectoIdAndEtapaId(
            Long id,
            Long empresaId,
            Long proyectoId,
            Long etapaId
    );

    Optional<Manzana> findByIdAndEmpresaIdAndProyectoId(
            Long id,
            Long empresaId,
            Long proyectoId
    );

    boolean existsByEtapaIdAndCodigoIgnoreCase(Long etapaId, String codigo);

    boolean existsByEtapaIdAndCodigoIgnoreCaseAndIdNot(
            Long etapaId,
            String codigo,
            Long id
    );
}
