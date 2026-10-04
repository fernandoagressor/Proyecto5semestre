package com.inmobiliaria.inmobiliaria_api.repository.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.LoteHistorialEstado;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoteHistorialEstadoRepository
        extends JpaRepository<LoteHistorialEstado, Long> {

    List<LoteHistorialEstado> findByLoteIdOrderByCreadoEnDesc(Long loteId);
}
