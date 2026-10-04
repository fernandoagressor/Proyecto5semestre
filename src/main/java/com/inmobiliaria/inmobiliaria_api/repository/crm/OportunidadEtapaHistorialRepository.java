package com.inmobiliaria.inmobiliaria_api.repository.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.OportunidadEtapaHistorial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OportunidadEtapaHistorialRepository
        extends JpaRepository<OportunidadEtapaHistorial, Long> {

    List<OportunidadEtapaHistorial>
    findByOportunidadIdOrderByCreadoEnAsc(Long oportunidadId);
}
