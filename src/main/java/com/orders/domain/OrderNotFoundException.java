package com.orders.domain;

/**
 * La orden solicitada no existe.
 * Excepción de dominio: no contiene códigos HTTP. Traducirla a 404
 * es responsabilidad del adaptador (o de API Gateway en la arquitectura A).
 */
public class OrderNotFoundException extends RuntimeException {

    private final String orderId;

    public OrderNotFoundException(String orderId) {
        super("Order " + orderId + " does not exist");
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
