package com.inmobiliaria.inmobiliaria_api.entity.simulacion;

public enum Periodicidad {
    MENSUAL(12, 1),
    BIMESTRAL(6, 2),
    TRIMESTRAL(4, 3),
    SEMESTRAL(2, 6),
    ANUAL(1, 12);

    private final int periodosPorAno;
    private final int mesesPorPeriodo;

    Periodicidad(int periodosPorAno, int mesesPorPeriodo) {
        this.periodosPorAno = periodosPorAno;
        this.mesesPorPeriodo = mesesPorPeriodo;
    }

    public int getPeriodosPorAno() {
        return periodosPorAno;
    }

    public int getMesesPorPeriodo() {
        return mesesPorPeriodo;
    }
}
