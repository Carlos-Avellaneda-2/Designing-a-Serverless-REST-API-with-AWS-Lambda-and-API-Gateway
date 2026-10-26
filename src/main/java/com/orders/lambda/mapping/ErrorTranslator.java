package com.orders.lambda.mapping;

import com.orders.domain.InvalidOrderException;
import com.orders.domain.OrderNotFoundException;
import com.orders.lambda.common.ErrorResponse;

import java.util.function.Supplier;

/**
 * ARQUITECTURA A: ejecuta una operación del servicio y convierte las
 * excepciones de dominio en GatewayErrorException con el código adecuado.
 *
 * Cualquier error inesperado se convierte en INTERNAL_ERROR con un mensaje
 * genérico: el detalle (y el stack trace) solo va a CloudWatch Logs.
 */
final class ErrorTranslator {

    private ErrorTranslator() {
    }

    static <T> T run(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (InvalidOrderException e) {
            throw new GatewayErrorException(ErrorResponse.INVALID_REQUEST, e.getMessage());
        } catch (OrderNotFoundException e) {
            throw new GatewayErrorException(ErrorResponse.ORDER_NOT_FOUND, e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Unexpected error: " + e);
            e.printStackTrace();
            throw new GatewayErrorException(ErrorResponse.INTERNAL_ERROR, "Unexpected internal error");
        }
    }
}
