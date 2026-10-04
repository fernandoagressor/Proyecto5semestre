package com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion;

public class ConflictoException extends RuntimeException {

    public ConflictoException(String message) {
        super(message);
    }
}
