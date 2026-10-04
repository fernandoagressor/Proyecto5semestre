package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudMensaje;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudMensajeRepository extends JpaRepository<SolicitudMensaje, Long> {
    List<SolicitudMensaje> findBySolicitudIdOrderByCreadoEnAsc(Long solicitudId);
    List<SolicitudMensaje> findBySolicitudIdAndInternoFalseOrderByCreadoEnAsc(Long solicitudId);
    Optional<SolicitudMensaje> findByIdAndSolicitudId(Long id, Long solicitudId);
}
