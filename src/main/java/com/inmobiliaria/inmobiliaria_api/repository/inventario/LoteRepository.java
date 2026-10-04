package com.inmobiliaria.inmobiliaria_api.repository.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.Lote;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoteRepository
        extends JpaRepository<Lote, Long>, JpaSpecificationExecutor<Lote> {

    Optional<Lote> findByIdAndEmpresaIdAndProyectoId(
            Long id,
            Long empresaId,
            Long proyectoId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l
            from Lote l
            where l.id = :id
              and l.empresaId = :empresaId
              and l.proyectoId = :proyectoId
            """)
    Optional<Lote> findForUpdate(
            @Param("id") Long id,
            @Param("empresaId") Long empresaId,
            @Param("proyectoId") Long proyectoId
    );

    boolean existsByProyectoIdAndCodigoIgnoreCase(Long proyectoId, String codigo);

    boolean existsByProyectoIdAndCodigoIgnoreCaseAndIdNot(
            Long proyectoId,
            String codigo,
            Long id
    );

    boolean existsByManzanaIdAndNumeroIgnoreCase(Long manzanaId, String numero);

    boolean existsByManzanaIdAndNumeroIgnoreCaseAndIdNot(
            Long manzanaId,
            String numero,
            Long id
    );

    long countByProyectoIdAndEmpresaId(Long proyectoId, Long empresaId);

    @Query("""
            select l.estado, count(l), coalesce(sum(l.valorLista), 0)
            from Lote l
            where l.empresaId = :empresaId
              and l.proyectoId = :proyectoId
            group by l.estado
            """)
    List<Object[]> resumirPorEstado(
            @Param("empresaId") Long empresaId,
            @Param("proyectoId") Long proyectoId
    );

    @Query("""
            select coalesce(sum(l.valorLista), 0)
            from Lote l
            where l.empresaId = :empresaId
              and l.proyectoId = :proyectoId
              and l.disponible = true
            """)
    BigDecimal sumarValorDisponible(
            @Param("empresaId") Long empresaId,
            @Param("proyectoId") Long proyectoId
    );
}
