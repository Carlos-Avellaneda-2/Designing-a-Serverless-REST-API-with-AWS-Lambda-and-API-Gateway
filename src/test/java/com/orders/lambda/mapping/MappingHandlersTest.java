package com.orders.lambda.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.orders.application.OrderService;
import com.orders.application.SampleOrders;
import com.orders.application.SequentialOrderIdGenerator;
import com.orders.domain.CreateOrderRequest;
import com.orders.domain.Order;
import com.orders.lambda.common.Json;
import com.orders.persistence.InMemoryOrderRepository;
import com.orders.testsupport.FailingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arquitectura A: los handlers se prueban sin AWS. Se invocan como lo haría el
 * runtime de Lambda (con objetos ya deserializados) y se verifica que los errores
 * produzcan el errorMessage que API Gateway espera para sus regex.
 */
class MappingHandlersTest {

    /** Los mismos patrones que se configuran en API Gateway (docs/ARCH_A_GATEWAY_SETUP.md). */
    private static final Pattern NOT_FOUND = Pattern.compile(".*\"ORDER_NOT_FOUND\".*");
    private static final Pattern BAD_REQUEST = Pattern.compile(".*(\"INVALID_REQUEST\"|JSON).*");
    private static final Pattern INTERNAL = Pattern.compile(".*(\"INTERNAL_ERROR\"|Task timed out).*");

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(new InMemoryOrderRepository(SampleOrders.all()),
                new SequentialOrderIdGenerator());
    }

    @Test
    void createHandlerReturnsCreatedOrder() {
        Order order = new CreateOrderHandler(service)
                .handleRequest(new CreateOrderRequest("CUST-42", "MacBook Air", 1), null);

        assertEquals("ORD-001", order.getId());
    }

    @Test
    void getHandlerReturnsExistingOrder() {
        Order order = new GetOrderHandler(service).handleRequest(new OrderIdInput("ORD-100"), null);

        assertEquals("MacBook Air", order.getProduct());
    }

    @Test
    void findHandlerReturnsCustomerOrders() {
        List<Order> orders = new FindOrdersByCustomerHandler(service)
                .handleRequest(new CustomerQueryInput("CUST-42"), null);

        assertEquals(2, orders.size());
    }

    @Test
    void missingOrderProducesNotFoundErrorMessage() throws Exception {
        GatewayErrorException e = assertThrows(GatewayErrorException.class,
                () -> new GetOrderHandler(service).handleRequest(new OrderIdInput("ORD-999"), null));

        assertTrue(NOT_FOUND.matcher(e.getMessage()).matches());
        assertFalse(BAD_REQUEST.matcher(e.getMessage()).matches());
        JsonNode json = Json.MAPPER.readTree(e.getMessage());
        assertEquals("ORDER_NOT_FOUND", json.get("error").asText());
        assertEquals("Order ORD-999 does not exist", json.get("message").asText());
    }

    @Test
    void invalidInputProducesBadRequestErrorMessage() throws Exception {
        GatewayErrorException e = assertThrows(GatewayErrorException.class,
                () -> new CreateOrderHandler(service)
                        .handleRequest(new CreateOrderRequest("CUST-42", "", 1), null));

        assertTrue(BAD_REQUEST.matcher(e.getMessage()).matches());
        assertEquals("INVALID_REQUEST", Json.MAPPER.readTree(e.getMessage()).get("error").asText());
    }

    @Test
    void missingQueryParamProducesBadRequest() {
        GatewayErrorException e = assertThrows(GatewayErrorException.class,
                () -> new FindOrdersByCustomerHandler(service)
                        .handleRequest(new CustomerQueryInput(""), null));

        assertTrue(BAD_REQUEST.matcher(e.getMessage()).matches());
    }

    @Test
    void unexpectedErrorProducesGenericInternalError() throws Exception {
        OrderService broken = new OrderService(new FailingRepository(), new SequentialOrderIdGenerator());

        GatewayErrorException e = assertThrows(GatewayErrorException.class,
                () -> new GetOrderHandler(broken).handleRequest(new OrderIdInput("ORD-1"), null));

        assertTrue(INTERNAL.matcher(e.getMessage()).matches());
        JsonNode json = Json.MAPPER.readTree(e.getMessage());
        assertEquals("INTERNAL_ERROR", json.get("error").asText());
        assertFalse(e.getMessage().contains(FailingRepository.SECRET_DETAIL), "no internal details leak");
    }

    @Test
    void deleteHandlerRemovesOrder() {
        new DeleteOrderHandler(service).handleRequest(new OrderIdInput("ORD-100"), null);

        GatewayErrorException e = assertThrows(GatewayErrorException.class,
                () -> new GetOrderHandler(service).handleRequest(new OrderIdInput("ORD-100"), null));
        assertTrue(NOT_FOUND.matcher(e.getMessage()).matches());
    }

    @Test
    void deletingMissingOrderProducesNotFoundErrorMessage() {
        GatewayErrorException e = assertThrows(GatewayErrorException.class,
                () -> new DeleteOrderHandler(service).handleRequest(new OrderIdInput("ORD-999"), null));

        assertTrue(NOT_FOUND.matcher(e.getMessage()).matches());
    }
}
