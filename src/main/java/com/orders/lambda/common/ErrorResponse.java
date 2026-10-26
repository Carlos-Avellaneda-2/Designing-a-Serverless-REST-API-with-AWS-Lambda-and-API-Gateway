package com.orders.lambda.common;

/**
 * Formato JSON único para todos los errores del API:
 * { "error": "ORDER_NOT_FOUND", "message": "Order ORD-999 does not exist" }
 *
 * Pertenece a la capa de infraestructura (contrato HTTP), no al dominio.
 */
public class ErrorResponse {

    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String MALFORMED_JSON = "MALFORMED_JSON";
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private String error;
    private String message;

    public ErrorResponse() {
    }

    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
