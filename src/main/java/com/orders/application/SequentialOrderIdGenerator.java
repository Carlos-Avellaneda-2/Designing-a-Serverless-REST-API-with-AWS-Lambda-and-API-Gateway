package com.orders.application;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Genera ORD-001, ORD-002, ...
 *
 * LIMITACIÓN CONOCIDA: el contador vive en memoria. Se reinicia con cada
 * "cold start" de Lambda y no se comparte entre instancias concurrentes,
 * por lo que en producción dos instancias podrían generar el mismo id.
 * Con una base de datos se usaría un UUID o un id generado por el almacén.
 */
public class SequentialOrderIdGenerator implements Supplier<String> {

    private final AtomicInteger counter;

    public SequentialOrderIdGenerator() {
        this(1);
    }

    public SequentialOrderIdGenerator(int start) {
        this.counter = new AtomicInteger(start);
    }

    @Override
    public String get() {
        return String.format("ORD-%03d", counter.getAndIncrement());
    }
}
