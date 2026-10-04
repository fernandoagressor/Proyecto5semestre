package com.inmobiliaria.inmobiliaria_api.repository.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.CategoriaCosto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaCostoRepository extends JpaRepository<CategoriaCosto, Long> {
    List<CategoriaCosto> findByEmpresaIdOrderByNombreAsc(Long empresaId);
    Optional<CategoriaCosto> findByIdAndEmpresaId(Long id, Long empresaId);
    boolean existsByEmpresaIdAndCodigo(Long empresaId, String codigo);
}
