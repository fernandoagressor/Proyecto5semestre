package com.inmobiliaria.inmobiliaria_api.repository.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConfiguracionCartera;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionCarteraRepository extends JpaRepository<ConfiguracionCartera, Long> {
    Optional<ConfiguracionCartera> findByEmpresaId(Long empresaId);
}
