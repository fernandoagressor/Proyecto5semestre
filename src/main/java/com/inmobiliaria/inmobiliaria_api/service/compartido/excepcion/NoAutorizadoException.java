package com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion;

public class NoAutorizadoException extends RuntimeException {

    public NoAutorizadoException(String message) {
        super(message);
    }
}
