package com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion;

public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String message) {
        super(message);
    }
}
