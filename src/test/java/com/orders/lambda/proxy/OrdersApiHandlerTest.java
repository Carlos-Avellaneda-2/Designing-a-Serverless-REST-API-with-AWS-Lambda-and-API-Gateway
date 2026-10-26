package com.orders.lambda.proxy;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.orders.application.OrderService;
import com.orders.application.SampleOrders;
import com.orders.application.SequentialOrderIdGenerator;
import com.orders.lambda.common.Json;
import com.orders.persistence.InMemoryOrderRepository;
import com.orders.testsupport.FailingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arquitectura B: el adaptador HTTP se prueba localmente con eventos de
 * API Gateway simulados. No hace falta AWS ni un servidor web.
 */
class OrdersApiHandlerTest {

    private OrdersApiHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrdersApiHandler(new OrderService(
                new InMemoryOrderRepository(SampleOrders.all()),
                new SequentialOrderIdGenerator()));
    }

    // ------------------------------------------------------------ POST /orders

    @Test
    void postCreatesOrderAndReturns201WithLocation() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                post("{\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}"), null);

        assertEquals(201, response.getStatusCode());
        assertEquals("/orders/ORD-001", response.getHeaders().get("Location"));
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        JsonNode body = Json.MAPPER.readTree(response.getBody());
        assertEquals("ORD-001", body.get("id").asText());
        assertEquals("CUST-42", body.get("customerId").asText());
        assertEquals("MacBook Air", body.get("product").asText());
        assertEquals(1, body.get("quantity").asInt());
    }

    @Test
    void postIgnoresClientSuppliedId() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                post("{\"id\":\"ORD-HACK\",\"customerId\":\"CUST-1\",\"product\":\"X\",\"quantity\":1}"), null);

        assertEquals(201, response.getStatusCode());
        assertEquals("ORD-001", Json.MAPPER.readTree(response.getBody()).get("id").asText());
    }

    @Test
    void postWithInvalidDataReturns400() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                post("{\"customerId\":\"CUST-42\",\"product\":\"\",\"quantity\":1}"), null);

        assertError(response, 400, "INVALID_REQUEST");
    }

    @Test
    void postWithMalformedJsonReturns400() throws Exception {
        assertError(handler.handleRequest(post("{not json"), null), 400, "MALFORMED_JSON");
    }

    @Test
    void postWithWrongTypeReturns400() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(
                post("{\"customerId\":\"CUST-42\",\"product\":\"X\",\"quantity\":\"abc\"}"), null);

        assertError(response, 400, "MALFORMED_JSON");
    }

    @Test
    void postWithoutBodyReturns400() throws Exception {
        assertError(handler.handleRequest(post(null), null), 400, "MALFORMED_JSON");
    }

    // ------------------------------------------------------- GET /orders/{id}

    @Test
    void getExistingOrderReturns200() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(getById("ORD-102"), null);

        assertEquals(200, response.getStatusCode());
        JsonNode body = Json.MAPPER.readTree(response.getBody());
        assertEquals("ORD-102", body.get("id").asText());
        assertEquals("Keyboard", body.get("product").asText());
    }

    @Test
    void getMissingOrderReturns404() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(getById("ORD-999"), null);

        JsonNode body = assertError(response, 404, "ORDER_NOT_FOUND");
        assertEquals("Order ORD-999 does not exist", body.get("message").asText());
    }

    @Test
    void orderCreatedWithPostCanBeRetrievedWithGet() {
        handler.handleRequest(post("{\"customerId\":\"CUST-5\",\"product\":\"Pen\",\"quantity\":3}"), null);

        assertEquals(200, handler.handleRequest(getById("ORD-001"), null).getStatusCode());
    }

    // ------------------------------------------- GET /orders?customerId=...

    @Test
    void searchByCustomerReturns200WithArray() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(search("CUST-42"), null);

        assertEquals(200, response.getStatusCode());
        JsonNode body = Json.MAPPER.readTree(response.getBody());
        assertTrue(body.isArray());
        assertEquals(2, body.size());
    }

    @Test
    void searchForCustomerWithoutOrdersReturnsEmptyArray() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(search("CUST-100"), null);

        assertEquals(200, response.getStatusCode());
        assertEquals("[]", response.getBody());
    }

    @Test
    void searchWithoutCustomerIdReturns400() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET").withResource("/orders").withPath("/orders");

        assertError(handler.handleRequest(event, null), 400, "INVALID_REQUEST");
    }

    // ---------------------------------------------------- DELETE /orders/{id}

    @Test
    void deleteExistingOrderReturns204WithoutBody() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(delete("ORD-100"), null);

        assertEquals(204, response.getStatusCode());
        assertTrue(response.getBody() == null || response.getBody().isEmpty());
    }

    @Test
    void deleteMissingOrderReturns404() throws Exception {
        assertError(handler.handleRequest(delete("ORD-999"), null), 404, "ORDER_NOT_FOUND");
    }

    @Test
    void getAfterDeleteReturns404() throws Exception {
        handler.handleRequest(delete("ORD-102"), null);

        assertError(handler.handleRequest(getById("ORD-102"), null), 404, "ORDER_NOT_FOUND");
    }

    @Test
    void deleteOnCollectionReturns405() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("DELETE").withResource("/orders").withPath("/orders");

        assertError(handler.handleRequest(event, null), 405, "METHOD_NOT_ALLOWED");
    }

    // ------------------------------------------------------------ otros casos

    @Test
    void unsupportedMethodReturns405() throws Exception {
        APIGatewayProxyRequestEvent event = getById("ORD-100").withHttpMethod("PUT");

        assertError(handler.handleRequest(event, null), 405, "METHOD_NOT_ALLOWED");
    }

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() throws Exception {
        OrdersApiHandler broken = new OrdersApiHandler(new OrderService(
                new FailingRepository(), new SequentialOrderIdGenerator()));

        APIGatewayProxyResponseEvent response = broken.handleRequest(getById("ORD-100"), null);

        assertError(response, 500, "INTERNAL_ERROR");
        assertFalse(response.getBody().contains(FailingRepository.SECRET_DETAIL));
    }

    @Test
    void handlesTheSavedExampleEventFromApiGateway() throws Exception {
        String raw = Files.readString(Path.of("events/proxy-event-example.json"));
        APIGatewayProxyRequestEvent event = Json.MAPPER.readValue(raw, APIGatewayProxyRequestEvent.class);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertEquals(200, response.getStatusCode());
        assertEquals("ORD-100", Json.MAPPER.readTree(response.getBody()).get("id").asText());
    }

    @Test
    void handlesRealPostEventCapturedInCloudWatch() throws Exception {
        String raw = Files.readString(Path.of("events/proxy-event-real-post.json"));
        APIGatewayProxyRequestEvent event = Json.MAPPER.readValue(raw, APIGatewayProxyRequestEvent.class);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertEquals(201, response.getStatusCode());
        assertEquals("Mouse", Json.MAPPER.readTree(response.getBody()).get("product").asText());
    }

    // ---------------------------------------------------------------- helpers

    private static APIGatewayProxyRequestEvent post(String body) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST").withResource("/orders").withPath("/orders")
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(body);
    }

    private static APIGatewayProxyRequestEvent getById(String id) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET").withResource("/orders/{id}").withPath("/orders/" + id)
                .withPathParameters(Map.of("id", id));
    }

    private static APIGatewayProxyRequestEvent delete(String id) {
        return getById(id).withHttpMethod("DELETE");
    }

    private static APIGatewayProxyRequestEvent search(String customerId) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET").withResource("/orders").withPath("/orders")
                .withQueryStringParameters(Map.of("customerId", customerId));
    }

    private static JsonNode assertError(APIGatewayProxyResponseEvent response, int status, String code)
            throws Exception {
        assertEquals(status, response.getStatusCode());
        JsonNode body = Json.MAPPER.readTree(response.getBody());
        assertEquals(code, body.get("error").asText());
        assertTrue(body.hasNonNull("message"));
        return body;
    }
}
