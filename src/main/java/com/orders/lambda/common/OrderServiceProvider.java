package com.orders.lambda.common;

import com.orders.application.OrderService;
import com.orders.application.SampleOrders;
import com.orders.application.SequentialOrderIdGenerator;
import com.orders.persistence.InMemoryOrderRepository;

/**
 * "Composition root": arma el servicio con sus dependencias.
 *
 * Es estático para que viva mientras viva la instancia de Lambda: las
 * invocaciones "warm" reutilizan el mismo repositorio en memoria. Un cold
 * start crea uno nuevo (solo con los datos de ejemplo).
 */
public final class OrderServiceProvider {

    private static final OrderService INSTANCE = new OrderService(
            new InMemoryOrderRepository(SampleOrders.all()),
            new SequentialOrderIdGenerator());

    private OrderServiceProvider() {
    }

    public static OrderService get() {
        return INSTANCE;
    }
}
