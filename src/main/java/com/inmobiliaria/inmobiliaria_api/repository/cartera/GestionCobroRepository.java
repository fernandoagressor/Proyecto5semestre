package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.GestionCobro;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GestionCobroRepository extends JpaRepository<GestionCobro, Long> {
    List<GestionCobro> findByContratoIdOrderByFechaGestionDesc(Long contratoId);
}
