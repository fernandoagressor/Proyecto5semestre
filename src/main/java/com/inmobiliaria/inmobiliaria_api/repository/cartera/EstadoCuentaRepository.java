package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoCuentaGenerado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadoCuentaRepository extends JpaRepository<EstadoCuentaGenerado, Long> {
}
