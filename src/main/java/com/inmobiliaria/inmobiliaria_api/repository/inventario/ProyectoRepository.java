package com.inmobiliaria.inmobiliaria_api.repository.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.Proyecto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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

    /** Códigos automáticos (PRY-…) ya usados en la empresa. */
    @Query("select p.codigo from Proyecto p where p.empresaId = :empresaId and upper(p.codigo) like 'PRY-%'")
    List<String> codigosAutomaticos(@Param("empresaId") Long empresaId);
}
