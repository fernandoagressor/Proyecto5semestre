package com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion;

public class DemasiadosIntentosException extends RuntimeException {

    public DemasiadosIntentosException(String message) {
        super(message);
    }
}