package com.inmobiliaria.inmobiliaria_api.repository.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.EncuestaSatisfaccion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncuestaSatisfaccionRepository extends JpaRepository<EncuestaSatisfaccion, Long> {
    boolean existsBySolicitudIdAndClienteId(Long solicitudId, Long clienteId);
}
