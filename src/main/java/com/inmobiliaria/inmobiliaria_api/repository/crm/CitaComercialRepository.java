package com.inmobiliaria.inmobiliaria_api.repository.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.CitaComercial;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CitaComercialRepository
        extends JpaRepository<CitaComercial, Long>,
        JpaSpecificationExecutor<CitaComercial> {

    List<CitaComercial> findByOportunidadIdOrderByFechaInicioAsc(
            Long oportunidadId
    );

    Optional<CitaComercial> findByIdAndOportunidadId(
            Long id,
            Long oportunidadId
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM citas_comerciales c
            WHERE c.asesor_id = :asesorId
              AND c.estado NOT IN ('CANCELADA', 'REALIZADA', 'NO_ASISTIO')
              AND (:excluirId IS NULL OR c.id <> :excluirId)
              AND c.fecha_inicio < :fechaFin
              AND c.fecha_fin > :fechaInicio
            """, nativeQuery = true)
    long contarCrucesAgenda(
            @Param("asesorId") Long asesorId,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFin") LocalDateTime fechaFin,
            @Param("excluirId") Long excluirId
    );
}
