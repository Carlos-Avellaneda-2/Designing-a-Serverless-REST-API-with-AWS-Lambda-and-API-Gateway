package com.orders.lambda.mapping;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.orders.application.OrderService;
import com.orders.domain.Order;
import com.orders.lambda.common.OrderServiceProvider;

/**
 * Handler: com.orders.lambda.mapping.GetOrderHandler::handleRequest
 * Arquitectura A: GET /orders/{id}. Recibe { "id": "..." } ya extraído por API Gateway.
 */
public class GetOrderHandler implements RequestHandler<OrderIdInput, Order> {

    private final OrderService service;

    public GetOrderHandler() {
        this(OrderServiceProvider.get());
    }

    GetOrderHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public Order handleRequest(OrderIdInput input, Context context) {
        String id = input == null ? null : input.getId();
        return ErrorTranslator.run(() -> service.getOrder(id));
    }
}
