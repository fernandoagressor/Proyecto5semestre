package com.inmobiliaria.inmobiliaria_api.service.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CuotaCalculadora {
    private static final MathContext CALCULO = new MathContext(20, RoundingMode.HALF_UP);

    public List<CuotaCalculada> generar(BigDecimal saldo, int numeroCuotas, BigDecimal tasaEfectivaAnual, Periodicidad periodicidad, LocalDate primeraFecha) {
        BigDecimal tasaPeriodo = tasaPeriodo(tasaEfectivaAnual, periodicidad);
        BigDecimal saldoActual = pesos(saldo);
        BigDecimal pagoFijo = pagoFijo(saldoActual, numeroCuotas, tasaPeriodo);
        List<CuotaCalculada> resultado = new ArrayList<>(numeroCuotas);
        for (int numero = 1; numero <= numeroCuotas; numero++) {
            BigDecimal interes = pesos(saldoActual.multiply(tasaPeriodo, CALCULO));
            BigDecimal capital = numero == numeroCuotas
                    ? saldoActual
                    : pesos(pagoFijo.subtract(interes, CALCULO))
                            .max(BigDecimal.ZERO.setScale(2))
                            .min(saldoActual);
            saldoActual = pesos(saldoActual.subtract(capital)).max(BigDecimal.ZERO.setScale(2));
            resultado.add(new CuotaCalculada(
                    numero,
                    primeraFecha.plusMonths((long) (numero - 1) * periodicidad.getMesesPorPeriodo()),
                    capital,
                    interes
            ));
        }
        return resultado;
    }

    public BigDecimal mora(BigDecimal saldo, BigDecimal tasaEfectivaAnual, int dias) {
        if (saldo.signum() == 0 || tasaEfectivaAnual.signum() == 0 || dias <= 0) return pesos(BigDecimal.ZERO);
        double factor = Math.pow(BigDecimal.ONE.add(tasaEfectivaAnual).doubleValue(), dias / 365.0) - 1.0;
        return pesos(saldo.multiply(BigDecimal.valueOf(factor), CALCULO));
    }

    private BigDecimal tasaPeriodo(BigDecimal tasa, Periodicidad periodicidad) {
        if (tasa.signum() == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(Math.pow(BigDecimal.ONE.add(tasa).doubleValue(), 1.0 / periodicidad.getPeriodosPorAno()) - 1.0);
    }

    private BigDecimal pagoFijo(BigDecimal saldo, int cuotas, BigDecimal tasa) {
        if (tasa.signum() == 0) return pesosAbajo(saldo.divide(BigDecimal.valueOf(cuotas), CALCULO));
        BigDecimal factor = BigDecimal.ONE.add(tasa).pow(cuotas, CALCULO);
        return pesos(saldo.multiply(tasa, CALCULO).multiply(factor, CALCULO)
                .divide(factor.subtract(BigDecimal.ONE, CALCULO), CALCULO));
    }

    private static BigDecimal pesos(BigDecimal valor) {
        return valor.setScale(0, RoundingMode.HALF_UP).setScale(2);
    }

    private static BigDecimal pesosAbajo(BigDecimal valor) {
        return valor.setScale(0, RoundingMode.DOWN).setScale(2);
    }

    public record CuotaCalculada(int numero, LocalDate fechaVencimiento, BigDecimal capital, BigDecimal interes) {
    }
}
