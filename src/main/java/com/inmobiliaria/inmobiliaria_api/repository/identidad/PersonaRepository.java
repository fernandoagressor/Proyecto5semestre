package com.inmobiliaria.inmobiliaria_api.repository.identidad;

import com.inmobiliaria.inmobiliaria_api.entity.identidad.Persona;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

    Optional<Persona> findByTipoDocumentoAndNumeroDocumento(
            String tipoDocumento,
            String numeroDocumento
    );
}
