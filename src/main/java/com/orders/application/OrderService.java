package com.orders.application;

import com.orders.domain.CreateOrderRequest;
import com.orders.domain.InvalidOrderException;
import com.orders.domain.Order;
import com.orders.domain.OrderNotFoundException;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Lógica de aplicación del API de órdenes.
 *
 * Aquí viven las reglas de negocio y la validación. Esta clase NO conoce
 * HTTP, API Gateway, status codes ni JSON: comunica los problemas lanzando
 * excepciones de dominio (InvalidOrderException, OrderNotFoundException).
 */
public class OrderService {

    private final OrderRepository repository;
    private final Supplier<String> idGenerator;

    public OrderService(OrderRepository repository, Supplier<String> idGenerator) {
        this.repository = Objects.requireNonNull(repository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
    }

    public Order createOrder(CreateOrderRequest request) {
        if (request == null) {
            throw new InvalidOrderException("Request must not be empty");
        }
        requireText(request.getCustomerId(), "customerId");
        requireText(request.getProduct(), "product");
        if (request.getQuantity() == null) {
            throw new InvalidOrderException("quantity is required");
        }
        if (request.getQuantity() < 1) {
            throw new InvalidOrderException("quantity must be greater than or equal to 1");
        }

        Order order = new Order(
                nextFreeId(),
                request.getCustomerId().trim(),
                request.getProduct().trim(),
                request.getQuantity());
        return repository.save(order);
    }

    public Order getOrder(String id) {
        requireText(id, "id");
        return repository.findById(id.trim())
                .orElseThrow(() -> new OrderNotFoundException(id.trim()));
    }

    public List<Order> getOrdersByCustomer(String customerId) {
        requireText(customerId, "customerId");
        return repository.findByCustomerId(customerId.trim());
    }

    /**
     * Elimina una orden. Si no existe lanza OrderNotFoundException.
     * El servicio decide el hecho de negocio ("existía" / "no existe");
     * traducirlo a 204 o 404 es responsabilidad de la capa HTTP.
     */
    public void deleteOrder(String id) {
        requireText(id, "id");
        if (!repository.deleteById(id.trim())) {
            throw new OrderNotFoundException(id.trim());
        }
    }

    /** Evita colisiones con órdenes que ya existan (p. ej. los datos de ejemplo). */
    private String nextFreeId() {
        String id;
        do {
            id = idGenerator.get();
        } while (repository.findById(id).isPresent());
        return id;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderException(field + " is required");
        }
    }
}
