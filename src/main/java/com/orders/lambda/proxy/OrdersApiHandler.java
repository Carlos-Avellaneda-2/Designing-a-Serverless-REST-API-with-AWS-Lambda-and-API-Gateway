package com.orders.lambda.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.orders.application.OrderService;
import com.orders.domain.CreateOrderRequest;
import com.orders.domain.InvalidOrderException;
import com.orders.domain.Order;
import com.orders.domain.OrderNotFoundException;
import com.orders.lambda.common.ErrorResponse;
import com.orders.lambda.common.Json;
import com.orders.lambda.common.OrderServiceProvider;

import java.util.List;
import java.util.Map;

/**
 * Handler: com.orders.lambda.proxy.OrdersApiHandler::handleRequest
 *
 * ARQUITECTURA B (Lambda proxy integration): adaptador HTTP delgado.
 *
 * API Gateway entrega el request HTTP completo (APIGatewayProxyRequestEvent)
 * y espera una respuesta HTTP completa (status, headers, body).
 *
 * Responsabilidades de esta clase (y SOLO de esta clase):
 *  - enrutar por método + recurso;
 *  - extraer path params, query params y body;
 *  - convertir el body JSON a objetos;
 *  - traducir resultados y excepciones de dominio a status codes;
 *  - serializar la respuesta y poner headers (Content-Type, Location).
 *
 * La lógica de negocio sigue en OrderService, que no cambió.
 */
public class OrdersApiHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String ORDERS = "/orders";
    private static final String ORDER_BY_ID = "/orders/{id}";

    private final OrderService service;

    public OrdersApiHandler() {
        this(OrderServiceProvider.get());
    }

    OrdersApiHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        if ("true".equalsIgnoreCase(System.getenv("LOG_EVENTS"))) {
            // Solo para estudiar el formato del evento (sección 11). Desactivado por defecto.
            System.out.println("API Gateway event: " + Json.write(event));
        }
        try {
            return route(event);
        } catch (InvalidOrderException e) {
            return error(400, ErrorResponse.INVALID_REQUEST, e.getMessage());
        } catch (OrderNotFoundException e) {
            return error(404, ErrorResponse.ORDER_NOT_FOUND, e.getMessage());
        } catch (MalformedBodyException e) {
            return error(400, ErrorResponse.MALFORMED_JSON, e.getMessage());
        } catch (RuntimeException e) {
            // El detalle va a CloudWatch Logs; al cliente nunca se le envía el stack trace.
            System.err.println("Unexpected error: " + e);
            e.printStackTrace();
            return error(500, ErrorResponse.INTERNAL_ERROR, "Unexpected internal error");
        }
    }

    // ---------------------------------------------------------------- routing

    private APIGatewayProxyResponseEvent route(APIGatewayProxyRequestEvent event) {
        String method = event.getHttpMethod() == null ? "" : event.getHttpMethod();
        String resource = event.getResource();

        if (ORDERS.equals(resource)) {
            switch (method) {
                case "POST":
                    return createOrder(event);
                case "GET":
                    return findByCustomer(event);
                default:
                    return methodNotAllowed(method, resource);
            }
        }
        if (ORDER_BY_ID.equals(resource)) {
            switch (method) {
                case "GET":
                    return getOrder(event);
                case "DELETE":
                    return deleteOrder(event);
                default:
                    return methodNotAllowed(method, resource);
            }
        }
        return error(404, "ROUTE_NOT_FOUND", "No route for " + method + " " + event.getPath());
    }

    // ------------------------------------------------------------- operations

    private APIGatewayProxyResponseEvent createOrder(APIGatewayProxyRequestEvent event) {
        CreateOrderRequest request = parseBody(event.getBody());
        Order created = service.createOrder(request);
        return json(201, created, Map.of("Location", ORDERS + "/" + created.getId()));
    }

    private APIGatewayProxyResponseEvent getOrder(APIGatewayProxyRequestEvent event) {
        String id = param(event.getPathParameters(), "id");
        return json(200, service.getOrder(id), Map.of());
    }

    private APIGatewayProxyResponseEvent deleteOrder(APIGatewayProxyRequestEvent event) {
        service.deleteOrder(param(event.getPathParameters(), "id"));
        // 204 No Content: sin body y, por tanto, sin Content-Type.
        return new APIGatewayProxyResponseEvent().withStatusCode(204).withHeaders(Map.of());
    }

    private APIGatewayProxyResponseEvent findByCustomer(APIGatewayProxyRequestEvent event) {
        String customerId = param(event.getQueryStringParameters(), "customerId");
        List<Order> orders = service.getOrdersByCustomer(customerId);
        return json(200, orders, Map.of());
    }

    // ---------------------------------------------------------------- helpers

    private static CreateOrderRequest parseBody(String body) {
        if (body == null || body.isBlank()) {
            throw new MalformedBodyException("Request body is required");
        }
        try {
            return Json.MAPPER.readValue(body, CreateOrderRequest.class);
        } catch (JsonProcessingException e) {
            throw new MalformedBodyException("Request body is not valid JSON for an order");
        }
    }

    private static String param(Map<String, String> params, String name) {
        return params == null ? null : params.get(name);
    }

    private static APIGatewayProxyResponseEvent methodNotAllowed(String method, String resource) {
        return error(405, ErrorResponse.METHOD_NOT_ALLOWED,
                "Method " + method + " is not allowed on " + resource);
    }

    private static APIGatewayProxyResponseEvent error(int status, String code, String message) {
        return json(status, new ErrorResponse(code, message), Map.of());
    }

    private static APIGatewayProxyResponseEvent json(int status, Object body, Map<String, String> extraHeaders) {
        Map<String, String> headers = new java.util.HashMap<>(extraHeaders);
        headers.put("Content-Type", "application/json");
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(headers)
                .withBody(Json.write(body));
    }

    /** Body ausente o JSON inválido: problema del protocolo HTTP, no del dominio. */
    static class MalformedBodyException extends RuntimeException {
        MalformedBodyException(String message) {
            super(message);
        }
    }
}
