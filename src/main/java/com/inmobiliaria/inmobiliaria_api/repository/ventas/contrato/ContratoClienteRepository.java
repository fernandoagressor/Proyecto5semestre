package com.inmobiliaria.inmobiliaria_api.repository.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.ContratoCliente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContratoClienteRepository
        extends JpaRepository<ContratoCliente, Long> {

    List<ContratoCliente> findByContratoIdOrderByIdAsc(Long contratoId);

    boolean existsByContratoIdAndClienteIdAndRolEnContrato(
            Long contratoId,
            Long clienteId,
            com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.RolContratoCliente rol
    );

    Optional<ContratoCliente> findByContratoIdAndRolEnContrato(
            Long contratoId,
            com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.RolContratoCliente rol
    );

}
