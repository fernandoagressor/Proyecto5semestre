package com.inmobiliaria.inmobiliaria_api.repository.seguridad;

import com.inmobiliaria.inmobiliaria_api.entity.seguridad.IntentoAcceso;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntentoAccesoRepository extends JpaRepository<IntentoAcceso, Long> {
}
