package com.inmobiliaria.inmobiliaria_api.repository.notificacion;

import com.inmobiliaria.inmobiliaria_api.entity.notificacion.EnvioNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvioNotificacionRepository extends JpaRepository<EnvioNotificacion, Long> {
}
