package com.orders.lambda.mapping;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.orders.application.OrderService;
import com.orders.domain.Order;
import com.orders.lambda.common.OrderServiceProvider;

import java.util.List;

/**
 * Handler: com.orders.lambda.mapping.FindOrdersByCustomerHandler::handleRequest
 * Arquitectura A: GET /orders?customerId=... Recibe { "customerId": "..." }.
 */
public class FindOrdersByCustomerHandler implements RequestHandler<CustomerQueryInput, List<Order>> {

    private final OrderService service;

    public FindOrdersByCustomerHandler() {
        this(OrderServiceProvider.get());
    }

    FindOrdersByCustomerHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public List<Order> handleRequest(CustomerQueryInput input, Context context) {
        String customerId = input == null ? null : input.getCustomerId();
        return ErrorTranslator.run(() -> service.getOrdersByCustomer(customerId));
    }
}
