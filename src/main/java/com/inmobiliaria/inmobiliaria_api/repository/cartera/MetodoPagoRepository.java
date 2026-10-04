package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.MetodoPago;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetodoPagoRepository extends JpaRepository<MetodoPago, Long> {
    @Query("select m from MetodoPago m where m.id = :id and m.activo = true and (m.empresaId is null or m.empresaId = :empresaId)")
    Optional<MetodoPago> findDisponible(@Param("id") Long id, @Param("empresaId") Long empresaId);

    @Query("select m from MetodoPago m where m.activo = true and (m.empresaId is null or m.empresaId = :empresaId) order by m.nombre")
    List<MetodoPago> findDisponibles(@Param("empresaId") Long empresaId);
}
