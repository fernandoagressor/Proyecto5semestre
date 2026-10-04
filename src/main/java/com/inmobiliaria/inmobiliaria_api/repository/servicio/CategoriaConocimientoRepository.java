package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.CategoriaConocimiento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoriaConocimientoRepository extends JpaRepository<CategoriaConocimiento, Long> {
    @Query("select c from CategoriaConocimiento c where c.activo = true and (c.empresaId is null or c.empresaId = :empresaId) order by c.orden, c.nombre")
    List<CategoriaConocimiento> listarDisponibles(@Param("empresaId") Long empresaId);

    @Query("select c from CategoriaConocimiento c where c.id = :id and (c.empresaId is null or c.empresaId = :empresaId)")
    Optional<CategoriaConocimiento> findDisponible(@Param("id") Long id, @Param("empresaId") Long empresaId);
}
