package com.orders.application;

import com.orders.domain.CreateOrderRequest;
import com.orders.domain.InvalidOrderException;
import com.orders.domain.Order;
import com.orders.domain.OrderNotFoundException;
import com.orders.persistence.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la lógica de aplicación en Java puro: sin AWS, sin HTTP, sin servidor.
 */
class OrderServiceTest {

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(
                new InMemoryOrderRepository(SampleOrders.all()),
                new SequentialOrderIdGenerator());
    }

    // ------------------------------------------------------------ creación

    @Test
    void createsValidOrderWithGeneratedId() {
        Order order = service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", 1));

        assertEquals("ORD-001", order.getId());
        assertEquals("CUST-42", order.getCustomerId());
        assertEquals("MacBook Air", order.getProduct());
        assertEquals(1, order.getQuantity());
    }

    @Test
    void createdOrderCanBeRetrieved() {
        Order created = service.createOrder(new CreateOrderRequest("CUST-9", "Mouse", 2));

        assertEquals(created, service.getOrder(created.getId()));
    }

    @Test
    void generatesDifferentIdsForEachOrder() {
        Order a = service.createOrder(new CreateOrderRequest("CUST-1", "A", 1));
        Order b = service.createOrder(new CreateOrderRequest("CUST-1", "B", 1));

        assertNotEquals(a.getId(), b.getId());
    }

    @Test
    void skipsIdsThatAlreadyExist() {
        OrderService startingAtSampleId = new OrderService(
                new InMemoryOrderRepository(SampleOrders.all()),
                new SequentialOrderIdGenerator(100)); // ORD-100 ya existe

        Order order = startingAtSampleId.createOrder(new CreateOrderRequest("CUST-1", "A", 1));

        assertEquals("ORD-103", order.getId());
    }

    @Test
    void trimsTextFields() {
        Order order = service.createOrder(new CreateOrderRequest("  CUST-42 ", " Keyboard ", 1));

        assertEquals("CUST-42", order.getCustomerId());
        assertEquals("Keyboard", order.getProduct());
    }

    // ------------------------------------------------------- entrada inválida

    @Test
    void rejectsNullRequest() {
        assertThrows(InvalidOrderException.class, () -> service.createOrder(null));
    }

    @Test
    void rejectsMissingCustomerId() {
        InvalidOrderException e = assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest(null, "MacBook Air", 1)));
        assertEquals("customerId is required", e.getMessage());
    }

    @Test
    void rejectsBlankProduct() {
        InvalidOrderException e = assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-42", "   ", 1)));
        assertEquals("product is required", e.getMessage());
    }

    @Test
    void rejectsMissingQuantity() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", null)));
    }

    @Test
    void rejectsZeroOrNegativeQuantity() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", 0)));
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", -5)));
    }

    @Test
    void invalidOrderIsNotStored() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-NEW", "", 1)));

        assertTrue(service.getOrdersByCustomer("CUST-NEW").isEmpty());
    }

    // ------------------------------------------------------------- consulta

    @Test
    void retrievesExistingOrder() {
        Order order = service.getOrder("ORD-102");

        assertEquals(new Order("ORD-102", "CUST-7", "Keyboard", 2), order);
    }

    @Test
    void throwsWhenOrderDoesNotExist() {
        OrderNotFoundException e = assertThrows(OrderNotFoundException.class,
                () -> service.getOrder("ORD-999"));

        assertEquals("ORD-999", e.getOrderId());
        assertEquals("Order ORD-999 does not exist", e.getMessage());
    }

    @Test
    void rejectsBlankOrderId() {
        assertThrows(InvalidOrderException.class, () -> service.getOrder(" "));
    }

    // ------------------------------------------------------ búsqueda cliente

    @Test
    void findsOnlyOrdersOfTheCustomer() {
        service.createOrder(new CreateOrderRequest("CUST-42", "Charger", 1));
        service.createOrder(new CreateOrderRequest("CUST-7", "Mouse", 1));

        List<Order> orders = service.getOrdersByCustomer("CUST-42");

        assertEquals(3, orders.size());
        assertTrue(orders.stream().allMatch(o -> o.getCustomerId().equals("CUST-42")));
    }

    @Test
    void returnsEmptyListForCustomerWithoutOrders() {
        assertTrue(service.getOrdersByCustomer("CUST-100").isEmpty());
    }

    @Test
    void rejectsMissingCustomerIdInSearch() {
        assertThrows(InvalidOrderException.class, () -> service.getOrdersByCustomer(null));
        assertThrows(InvalidOrderException.class, () -> service.getOrdersByCustomer(""));
    }

    // ------------------------------------------------------------- borrado

    @Test
    void deletesExistingOrder() {
        service.deleteOrder("ORD-100");

        assertThrows(OrderNotFoundException.class, () -> service.getOrder("ORD-100"));
    }

    @Test
    void deletingMissingOrderThrowsNotFound() {
        OrderNotFoundException e = assertThrows(OrderNotFoundException.class,
                () -> service.deleteOrder("ORD-999"));

        assertEquals("ORD-999", e.getOrderId());
    }

    @Test
    void deletingTwiceFailsTheSecondTime() {
        service.deleteOrder("ORD-101");

        assertThrows(OrderNotFoundException.class, () -> service.deleteOrder("ORD-101"));
    }

    @Test
    void deletingDoesNotAffectOtherOrdersOfTheCustomer() {
        service.deleteOrder("ORD-100");

        List<Order> remaining = service.getOrdersByCustomer("CUST-42");
        assertEquals(1, remaining.size());
        assertEquals("ORD-101", remaining.get(0).getId());
    }

    @Test
    void rejectsBlankIdOnDelete() {
        assertThrows(InvalidOrderException.class, () -> service.deleteOrder(" "));
    }
}
