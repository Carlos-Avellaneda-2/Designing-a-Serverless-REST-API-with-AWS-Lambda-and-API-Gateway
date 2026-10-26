package com.orders;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de arquitectura: verifica automáticamente que la lógica de aplicación
 * (domain, application, persistence) no dependa de AWS, JSON ni HTTP.
 * Si alguien importa una clase de AWS en el servicio, este test falla.
 */
class ArchitectureTest {

    private static final List<String> PURE_JAVA_PACKAGES = List.of(
            "src/main/java/com/orders/domain",
            "src/main/java/com/orders/application",
            "src/main/java/com/orders/persistence");

    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "import com.amazonaws",
            "import com.fasterxml",
            "import com.orders.lambda",
            "import java.net.http",
            "import org.springframework");

    @Test
    void applicationLogicDoesNotDependOnInfrastructure() throws IOException {
        for (String dir : PURE_JAVA_PACKAGES) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(Path.of(dir))) {
                files = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
            }
            assertTrue(!files.isEmpty(), "Expected Java files in " + dir);
            for (Path file : files) {
                String source = Files.readString(file);
                for (String forbidden : FORBIDDEN_IMPORTS) {
                    assertTrue(!source.contains(forbidden),
                            file + " must not contain '" + forbidden + "'");
                }
            }
        }
    }
}
