package com.inmobiliaria.inmobiliaria_api.repository.ventas.reserva;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.ReservaHistorial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaHistorialRepository
        extends JpaRepository<ReservaHistorial, Long> {

    List<ReservaHistorial> findByReservaIdOrderByCreadoEnAsc(Long reservaId);
}
