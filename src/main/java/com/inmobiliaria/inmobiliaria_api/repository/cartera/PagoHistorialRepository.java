package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.PagoHistorialEstado;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoHistorialRepository extends JpaRepository<PagoHistorialEstado, Long> {
    List<PagoHistorialEstado> findByPagoIdOrderByCreadoEnAsc(Long pagoId);
}
