package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.CitaServicio;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CitaServicioRepository extends JpaRepository<CitaServicio, Long>, JpaSpecificationExecutor<CitaServicio> {
    Optional<CitaServicio> findByIdAndEmpresaId(Long id, Long empresaId);

    @Query("""
            select (count(c) > 0) from CitaServicio c
            where c.responsableId = :responsableId
              and c.estado not in (com.inmobiliaria.inmobiliaria_api.entity.servicio.EstadoCitaServicio.CANCELADA,
                                   com.inmobiliaria.inmobiliaria_api.entity.servicio.EstadoCitaServicio.REALIZADA)
              and c.fechaInicio < :fin
              and c.fechaFin > :inicio
              and (:excluirId is null or c.id <> :excluirId)
            """)
    boolean existeCruce(@Param("responsableId") Long responsableId,
                        @Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin,
                        @Param("excluirId") Long excluirId);
}
