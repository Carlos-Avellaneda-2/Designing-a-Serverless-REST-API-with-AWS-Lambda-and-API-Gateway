package com.orders.lambda.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ObjectMapper compartido. Se crea una sola vez por instancia de Lambda
 * (es costoso de construir y es thread-safe).
 *
 * FAIL_ON_UNKNOWN_PROPERTIES = false: si el cliente envía campos extra
 * (por ejemplo "id" en el POST) se ignoran, igual que hace el runtime de Lambda.
 */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize response", e);
        }
    }
}
