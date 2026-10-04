package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.ArticuloConocimiento;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticuloConocimientoRepository extends JpaRepository<ArticuloConocimiento, Long>, JpaSpecificationExecutor<ArticuloConocimiento> {
    @Query("select a from ArticuloConocimiento a where a.id = :id and (a.empresaId is null or a.empresaId = :empresaId)")
    Optional<ArticuloConocimiento> findDisponible(@Param("id") Long id, @Param("empresaId") Long empresaId);
}
