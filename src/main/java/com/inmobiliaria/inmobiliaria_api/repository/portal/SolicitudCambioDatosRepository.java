package com.inmobiliaria.inmobiliaria_api.repository.portal;

import com.inmobiliaria.inmobiliaria_api.entity.portal.SolicitudCambioDatos;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SolicitudCambioDatosRepository extends JpaRepository<SolicitudCambioDatos, Long>, JpaSpecificationExecutor<SolicitudCambioDatos> {
    Optional<SolicitudCambioDatos> findByIdAndEmpresaId(Long id, Long empresaId);
    boolean existsByClienteIdAndCampoAndEstado(Long clienteId, String campo, String estado);
}
