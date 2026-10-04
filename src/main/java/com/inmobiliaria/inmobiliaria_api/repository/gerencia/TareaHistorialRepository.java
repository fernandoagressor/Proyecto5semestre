package com.inmobiliaria.inmobiliaria_api.repository.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.TareaHistorial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TareaHistorialRepository extends JpaRepository<TareaHistorial, Long> {
    List<TareaHistorial> findByTareaIdOrderByCreadoEnAsc(Long tareaId);
}
