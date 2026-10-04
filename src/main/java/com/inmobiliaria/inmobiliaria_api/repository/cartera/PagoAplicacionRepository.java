package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.PagoAplicacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoAplicacionRepository extends JpaRepository<PagoAplicacion, Long> {
    List<PagoAplicacion> findByPagoIdOrderByIdAsc(Long pagoId);
}
