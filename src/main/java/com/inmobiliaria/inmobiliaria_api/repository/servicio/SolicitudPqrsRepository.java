package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudPqrs;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudPqrsRepository extends JpaRepository<SolicitudPqrs, Long>, JpaSpecificationExecutor<SolicitudPqrs> {
    Optional<SolicitudPqrs> findByIdAndEmpresaId(Long id, Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudPqrs s where s.id = :id and s.empresaId = :empresaId")
    Optional<SolicitudPqrs> findForUpdate(@Param("id") Long id, @Param("empresaId") Long empresaId);
}
