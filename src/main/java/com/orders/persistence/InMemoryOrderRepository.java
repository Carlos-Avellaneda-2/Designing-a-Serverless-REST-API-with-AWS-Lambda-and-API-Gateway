package com.orders.persistence;

import com.orders.application.OrderRepository;
import com.orders.domain.Order;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Almacenamiento en memoria. NO es persistente: en Lambda los datos se pierden
 * cuando el entorno de ejecución se recicla y no se comparten entre instancias.
 */
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public InMemoryOrderRepository() {
    }

    public InMemoryOrderRepository(Collection<Order> initialOrders) {
        initialOrders.forEach(o -> orders.put(o.getId(), o));
    }

    @Override
    public Order save(Order order) {
        orders.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        return orders.values().stream()
                .filter(o -> o.getCustomerId().equals(customerId))
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteById(String id) {
        return orders.remove(id) != null;
    }
}
