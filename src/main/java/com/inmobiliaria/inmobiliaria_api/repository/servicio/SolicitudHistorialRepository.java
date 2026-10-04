package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudHistorial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudHistorialRepository extends JpaRepository<SolicitudHistorial, Long> {
    List<SolicitudHistorial> findBySolicitudIdOrderByCreadoEnAsc(Long solicitudId);
}
