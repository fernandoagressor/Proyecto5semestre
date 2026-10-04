package com.inmobiliaria.inmobiliaria_api.repository.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.Prospecto;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProspectoRepository
        extends JpaRepository<Prospecto, Long>,
        JpaSpecificationExecutor<Prospecto> {

    Optional<Prospecto> findByIdAndEmpresaId(Long id, Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Prospecto p
            where p.id = :id
              and p.empresaId = :empresaId
            """)
    Optional<Prospecto> findForUpdate(
            @Param("id") Long id,
            @Param("empresaId") Long empresaId
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM prospectos p
            WHERE p.empresa_id = :empresaId
              AND p.estado NOT IN ('DESCARTADO', 'CONVERTIDO')
              AND (:excluirId IS NULL OR p.id <> :excluirId)
              and (
                    (:correo IS NOT NULL
                     AND LOWER(p.correo) = LOWER(:correo))
                    OR (:telefono IS NOT NULL
                        AND p.telefono = :telefono)
              )
            """, nativeQuery = true)
    long contarContactosActivos(
            @Param("empresaId") Long empresaId,
            @Param("correo") String correo,
            @Param("telefono") String telefono,
            @Param("excluirId") Long excluirId
    );
}
