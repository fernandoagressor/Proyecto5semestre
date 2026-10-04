package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.SolicitudAdjunto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudAdjuntoRepository extends JpaRepository<SolicitudAdjunto, Long> {
    boolean existsBySolicitudIdAndArchivoId(Long solicitudId, Long archivoId);
    List<SolicitudAdjunto> findBySolicitudIdOrderByCreadoEnAsc(Long solicitudId);
}
