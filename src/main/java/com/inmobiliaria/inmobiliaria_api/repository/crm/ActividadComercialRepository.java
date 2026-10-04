package com.inmobiliaria.inmobiliaria_api.repository.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.ActividadComercial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActividadComercialRepository
        extends JpaRepository<ActividadComercial, Long> {

    List<ActividadComercial>
    findByOportunidadIdOrderByFechaActividadDesc(Long oportunidadId);
}
