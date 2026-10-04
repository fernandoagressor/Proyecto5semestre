package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.RefinanciacionCuota;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefinanciacionCuotaRepository extends JpaRepository<RefinanciacionCuota, Long> {
    List<RefinanciacionCuota> findByRefinanciacionIdOrderByNumeroAsc(Long refinanciacionId);
}
