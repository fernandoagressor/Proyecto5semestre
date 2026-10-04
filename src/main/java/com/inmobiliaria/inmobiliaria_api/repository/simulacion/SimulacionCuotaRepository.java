package com.inmobiliaria.inmobiliaria_api.repository.simulacion;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.SimulacionCuota;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulacionCuotaRepository
        extends JpaRepository<SimulacionCuota, Long> {

    List<SimulacionCuota> findBySimulacionIdOrderByNumeroAsc(
            Long simulacionId
    );
}
