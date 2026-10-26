package com.orders.domain;

/**
 * Datos necesarios para crear una orden. No incluye id: lo asigna el sistema.
 *
 * Es un POJO "mutable" (constructor vacío + setters) a propósito:
 * es lo que necesita el serializador del runtime de AWS Lambda (y Jackson)
 * para construir el objeto a partir de JSON. No importa ninguna clase de AWS.
 *
 * quantity es Integer (no int) para poder distinguir "no enviado" (null) de 0.
 */
public class CreateOrderRequest {

    private String customerId;
    private String product;
    private Integer quantity;

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(String customerId, String product, Integer quantity) {
        this.customerId = customerId;
        this.product = product;
        this.quantity = quantity;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
