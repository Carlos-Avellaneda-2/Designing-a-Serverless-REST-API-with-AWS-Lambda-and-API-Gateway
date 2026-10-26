package com.orders.lambda.mapping;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.orders.application.OrderService;
import com.orders.lambda.common.OrderServiceProvider;

/**
 * Handler: com.orders.lambda.mapping.DeleteOrderHandler::handleRequest
 * Arquitectura A: DELETE /orders/{id}. Recibe { "id": "..." }.
 *
 * Devuelve null (no hay nada que devolver). API Gateway responde 204 en la
 * integration response por defecto y 404 si el errorMessage trae ORDER_NOT_FOUND.
 */
public class DeleteOrderHandler implements RequestHandler<OrderIdInput, Void> {

    private final OrderService service;

    public DeleteOrderHandler() {
        this(OrderServiceProvider.get());
    }

    DeleteOrderHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public Void handleRequest(OrderIdInput input, Context context) {
        String id = input == null ? null : input.getId();
        return ErrorTranslator.run(() -> {
            service.deleteOrder(id);
            return null;
        });
    }
}
