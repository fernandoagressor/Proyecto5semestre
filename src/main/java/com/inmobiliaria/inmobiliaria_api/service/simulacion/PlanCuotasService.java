package com.inmobiliaria.inmobiliaria_api.service.simulacion;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.SimulacionCuota;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlanCuotasService {

    private static final MathContext CALCULO =
            new MathContext(20, RoundingMode.HALF_UP);
    private static final int ESCALA_MONEDA = 2;

    public List<SimulacionCuota> generar(
            Long simulacionId,
            BigDecimal saldo,
            int numeroCuotas,
            BigDecimal tasaEfectivaAnual,
            Periodicidad periodicidad,
            LocalDate primeraFecha
    ) {
        BigDecimal tasaPeriodo = tasaPeriodo(
                tasaEfectivaAnual,
                periodicidad
        );
        BigDecimal pagoFijo = pagoFijo(
                saldo,
                numeroCuotas,
                tasaPeriodo
        );
        BigDecimal saldoActual = dinero(saldo);
        List<SimulacionCuota> cuotas = new ArrayList<>(numeroCuotas);

        for (int numero = 1; numero <= numeroCuotas; numero++) {
            BigDecimal interes = dinero(saldoActual.multiply(
                    tasaPeriodo,
                    CALCULO
            ));
            BigDecimal capital = numero == numeroCuotas
                    ? saldoActual
                    : dinero(pagoFijo.subtract(interes, CALCULO));
            if (capital.signum() < 0) {
                capital = BigDecimal.ZERO.setScale(
                        ESCALA_MONEDA,
                        RoundingMode.HALF_UP
                );
            }
            BigDecimal valorTotal = dinero(capital.add(interes));
            saldoActual = dinero(saldoActual.subtract(capital));
            if (saldoActual.signum() < 0 || numero == numeroCuotas) {
                saldoActual = BigDecimal.ZERO.setScale(
                        ESCALA_MONEDA,
                        RoundingMode.HALF_UP
                );
            }
            LocalDate vencimiento = primeraFecha.plusMonths(
                    (long) (numero - 1) * periodicidad.getMesesPorPeriodo()
            );
            cuotas.add(new SimulacionCuota(
                    simulacionId,
                    numero,
                    vencimiento,
                    capital,
                    interes,
                    BigDecimal.ZERO.setScale(ESCALA_MONEDA),
                    valorTotal,
                    saldoActual
            ));
        }
        return cuotas;
    }

    BigDecimal tasaPeriodo(
            BigDecimal tasaEfectivaAnual,
            Periodicidad periodicidad
    ) {
        if (tasaEfectivaAnual.signum() == 0) {
            return BigDecimal.ZERO;
        }
        double tasa = Math.pow(
                BigDecimal.ONE.add(tasaEfectivaAnual)
                        .doubleValue(),
                1.0 / periodicidad.getPeriodosPorAno()
        ) - 1.0;
        return BigDecimal.valueOf(tasa);
    }

    private BigDecimal pagoFijo(
            BigDecimal saldo,
            int numeroCuotas,
            BigDecimal tasaPeriodo
    ) {
        if (tasaPeriodo.signum() == 0) {
            return dinero(saldo.divide(
                    BigDecimal.valueOf(numeroCuotas),
                    CALCULO
            ));
        }
        BigDecimal factor = BigDecimal.ONE
                .add(tasaPeriodo)
                .pow(numeroCuotas, CALCULO);
        BigDecimal numerador = saldo
                .multiply(tasaPeriodo, CALCULO)
                .multiply(factor, CALCULO);
        BigDecimal denominador = factor.subtract(BigDecimal.ONE, CALCULO);
        return dinero(numerador.divide(denominador, CALCULO));
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
    }
}
