package com.orders.lambda.mapping;

/**
 * JSON que API Gateway construye con su mapping template para
 * GET /orders?customerId=CUST-42:
 *   { "customerId": "CUST-42" }
 */
public class CustomerQueryInput {

    private String customerId;

    public CustomerQueryInput() {
    }

    public CustomerQueryInput(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
}
