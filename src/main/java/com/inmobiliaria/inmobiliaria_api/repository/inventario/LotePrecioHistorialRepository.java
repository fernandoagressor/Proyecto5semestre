package com.inmobiliaria.inmobiliaria_api.repository.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.LotePrecioHistorial;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotePrecioHistorialRepository
        extends JpaRepository<LotePrecioHistorial, Long> {

    Optional<LotePrecioHistorial>
    findFirstByLoteIdAndVigenteHastaIsNullOrderByVigenteDesdeDesc(Long loteId);

    List<LotePrecioHistorial> findByLoteIdOrderByVigenteDesdeDesc(Long loteId);
}
