package com.orders.application;

import com.orders.domain.Order;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de almacenamiento. El servicio depende de esta interfaz, no de una
 * implementación concreta: hoy es memoria, mañana podría ser DynamoDB.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(String id);

    List<Order> findByCustomerId(String customerId);

    /** Elimina la orden. Devuelve true si existía. */
    boolean deleteById(String id);
}
