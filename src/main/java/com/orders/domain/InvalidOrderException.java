package com.orders.domain;

/**
 * Los datos recibidos no cumplen las reglas de negocio
 * (campos obligatorios vacíos, cantidad inválida, etc.).
 * Excepción de dominio: no contiene códigos HTTP.
 */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
