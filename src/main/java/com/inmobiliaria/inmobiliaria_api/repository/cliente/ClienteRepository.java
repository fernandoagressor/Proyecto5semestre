package com.inmobiliaria.inmobiliaria_api.repository.cliente;

import com.inmobiliaria.inmobiliaria_api.entity.cliente.Cliente;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

    Optional<Cliente> findByIdAndEmpresaIdAndEstado(
            Long id,
            Long empresaId,
            String estado
    );

    Optional<Cliente> findByUsuarioIdAndEmpresaIdAndEstado(
            Long usuarioId,
            Long empresaId,
            String estado
    );

    boolean existsByIdAndEmpresaId(Long id, Long empresaId);

    Optional<Cliente> findByEmpresaIdAndPersonaId(Long empresaId, Long personaId);

    Optional<Cliente> findByIdAndEmpresaId(Long id, Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cliente c where c.id = :id and c.empresaId = :empresaId")
    Optional<Cliente> findForUpdate(
            @Param("id") Long id,
            @Param("empresaId") Long empresaId
    );
}
