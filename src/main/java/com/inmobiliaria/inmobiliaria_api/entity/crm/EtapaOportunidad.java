package com.inmobiliaria.inmobiliaria_api.entity.crm;

public enum EtapaOportunidad {
    PROSPECTO_RECIBIDO(5),
    CONTACTO_INICIAL(10),
    CALIFICACION(25),
    PRESENTACION_PROYECTO(40),
    VISITA(55),
    PROPUESTA(70),
    NEGOCIACION(85),
    CIERRE(95);

    private final int probabilidadSugerida;

    EtapaOportunidad(int probabilidadSugerida) {
        this.probabilidadSugerida = probabilidadSugerida;
    }

    public int getProbabilidadSugerida() {
        return probabilidadSugerida;
    }
}
