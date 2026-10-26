package com.orders.application;

import com.orders.domain.Order;

import java.util.List;

/**
 * Datos predefinidos con los que arranca cada instancia de Lambda.
 *
 * Existen porque la memoria de Lambda NO es persistente ni compartida:
 * en la arquitectura A cada operación es una Lambda distinta, así que una
 * orden creada con POST no es visible para la Lambda del GET. Estas órdenes
 * permiten demostrar GET 200 en cualquier momento.
 */
public final class SampleOrders {

    private SampleOrders() {
    }

    public static List<Order> all() {
        return List.of(
                new Order("ORD-100", "CUST-42", "MacBook Air", 1),
                new Order("ORD-101", "CUST-42", "USB-C Hub", 2),
                new Order("ORD-102", "CUST-7", "Keyboard", 2));
    }
}
