package com.inmobiliaria.inmobiliaria_api.repository.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.ContratoVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContratoVersionRepository
        extends JpaRepository<ContratoVersion, Long> {

    List<ContratoVersion> findByContratoIdOrderByNumeroVersionDesc(
            Long contratoId
    );

    List<ContratoVersion> findByContratoIdAndVigenteTrue(Long contratoId);

    @Query("""
            select coalesce(max(cv.numeroVersion), 0)
            from ContratoVersion cv
            where cv.contratoId = :contratoId
            """)
    int maxNumeroVersion(@Param("contratoId") Long contratoId);
}
