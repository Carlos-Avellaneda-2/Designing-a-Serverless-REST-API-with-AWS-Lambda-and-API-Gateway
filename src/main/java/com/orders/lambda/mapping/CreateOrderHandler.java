package com.orders.lambda.mapping;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.orders.application.OrderService;
import com.orders.domain.CreateOrderRequest;
import com.orders.domain.Order;
import com.orders.lambda.common.OrderServiceProvider;

/**
 * Handler: com.orders.lambda.mapping.CreateOrderHandler::handleRequest
 *
 * Se usa en dos partes del taller:
 *  - Parte II: invocación directa de Lambda con un JSON de prueba.
 *  - Parte IV (arquitectura A): POST /orders con integración no-proxy.
 *
 * Entrada: el runtime de Lambda convierte el JSON en CreateOrderRequest.
 * Salida:  el runtime convierte el Order devuelto en JSON.
 * Este handler nunca ve HTTP: ni headers, ni status codes.
 */
public class CreateOrderHandler implements RequestHandler<CreateOrderRequest, Order> {

    private final OrderService service;

    public CreateOrderHandler() {
        this(OrderServiceProvider.get());
    }

    CreateOrderHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public Order handleRequest(CreateOrderRequest input, Context context) {
        return ErrorTranslator.run(() -> service.createOrder(input));
    }
}
