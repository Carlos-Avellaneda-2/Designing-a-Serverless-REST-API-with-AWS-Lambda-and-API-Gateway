package com.orders.lambda.mapping;

/**
 * JSON que API Gateway construye con su mapping template para GET /orders/{id}:
 *   { "id": "ORD-100" }
 */
public class OrderIdInput {

    private String id;

    public OrderIdInput() {
    }

    public OrderIdInput(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
