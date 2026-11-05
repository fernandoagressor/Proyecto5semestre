package com.inmobiliaria.inmobiliaria_api.repository.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.Manzana;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("select coalesce(max(m.secuencia), 0) from Manzana m where m.etapaId = :etapaId")
    int maxSecuencia(@Param("etapaId") Long etapaId);

    @Query("select upper(m.codigo) from Manzana m where m.etapaId = :etapaId")
    List<String> codigosDeEtapa(@Param("etapaId") Long etapaId);
}
