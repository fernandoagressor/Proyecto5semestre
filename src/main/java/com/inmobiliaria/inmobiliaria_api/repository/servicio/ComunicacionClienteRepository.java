package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.ComunicacionCliente;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ComunicacionClienteRepository extends JpaRepository<ComunicacionCliente, Long>, JpaSpecificationExecutor<ComunicacionCliente> {
    Optional<ComunicacionCliente> findByIdAndEmpresaId(Long id, Long empresaId);
}
