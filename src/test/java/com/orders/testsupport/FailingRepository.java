package com.orders.testsupport;

import com.orders.application.OrderRepository;
import com.orders.domain.Order;

import java.util.List;
import java.util.Optional;

/** Repositorio que siempre falla, para simular un error inesperado (500). */
public class FailingRepository implements OrderRepository {

    public static final String SECRET_DETAIL = "database is on fire";

    @Override
    public Order save(Order order) {
        throw new IllegalStateException(SECRET_DETAIL);
    }

    @Override
    public Optional<Order> findById(String id) {
        throw new IllegalStateException(SECRET_DETAIL);
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        throw new IllegalStateException(SECRET_DETAIL);
    }

    @Override
    public boolean deleteById(String id) {
        throw new IllegalStateException(SECRET_DETAIL);
    }
}
