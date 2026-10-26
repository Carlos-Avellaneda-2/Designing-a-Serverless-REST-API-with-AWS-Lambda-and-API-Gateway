package com.orders.lambda.mapping;

import com.orders.lambda.common.ErrorResponse;
import com.orders.lambda.common.Json;

/**
 * ARQUITECTURA A (no-proxy).
 *
 * En la integración no-proxy la Lambda NO puede elegir el status code.
 * Cuando una Lambda Java lanza una excepción, el runtime devuelve:
 *
 *   { "errorMessage": "...", "errorType": "...", "stackTrace": [...] }
 *
 * y API Gateway elige el status aplicando un regex sobre "errorMessage".
 *
 * Por eso esta excepción usa como mensaje el JSON de error completo, p. ej.
 *   {"error":"ORDER_NOT_FOUND","message":"Order ORD-999 does not exist"}
 *
 * - El regex de API Gateway detecta el código (".*\"ORDER_NOT_FOUND\".*" → 404).
 * - La plantilla de respuesta devuelve errorMessage tal cual al cliente.
 *
 * Este acoplamiento con API Gateway vive SOLO en el paquete lambda.mapping;
 * las excepciones de dominio no saben nada de él.
 */
public class GatewayErrorException extends RuntimeException {

    public GatewayErrorException(String errorCode, String message) {
        super(Json.write(new ErrorResponse(errorCode, message)));
    }
}
