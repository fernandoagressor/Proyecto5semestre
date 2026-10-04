package com.inmobiliaria.inmobiliaria_api.repository.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.FirmaContrato;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FirmaContratoRepository
        extends JpaRepository<FirmaContrato, Long> {

    List<FirmaContrato> findByContratoFirmanteIdOrderByFirmadaEnAsc(
            Long contratoFirmanteId
    );
}
