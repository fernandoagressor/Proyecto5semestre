package com.inmobiliaria.inmobiliaria_api.repository.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.AvanceObra;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvanceObraRepository extends JpaRepository<AvanceObra, Long> {

    List<AvanceObra> findByProyectoIdAndEmpresaIdOrderByFechaDescIdDesc(
            Long proyectoId,
            Long empresaId
    );
}
