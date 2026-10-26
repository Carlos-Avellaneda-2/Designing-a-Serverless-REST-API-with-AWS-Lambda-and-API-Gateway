# Notas de despliegue de las Lambdas

> Todo lo de este documento lo automatiza `scripts/aws/deploy.sh` (ver más abajo).

Todas las funciones usan **el mismo JAR**: `target/orders-api-1.0.0.jar` (generado con `mvn package`).
Lo único que cambia entre ellas es el *handler*.

| Función Lambda (nombre sugerido) | Handler | Se usa en |
|---|---|---|
| `orders-create` | `com.orders.lambda.mapping.CreateOrderHandler::handleRequest` | Parte II (invocación directa) y Arquitectura A: `POST /orders` |
| `orders-get` | `com.orders.lambda.mapping.GetOrderHandler::handleRequest` | Arquitectura A: `GET /orders/{id}` |
| `orders-find-by-customer` | `com.orders.lambda.mapping.FindOrdersByCustomerHandler::handleRequest` | Arquitectura A: `GET /orders?customerId=` |
| `orders-delete` | `com.orders.lambda.mapping.DeleteOrderHandler::handleRequest` | Arquitectura A: `DELETE /orders/{id}` |
| `orders-api` | `com.orders.lambda.proxy.OrdersApiHandler::handleRequest` | Arquitectura B: todas las rutas |

Configuración común:

- **Runtime:** Java 17
- **Arquitectura:** x86_64 o arm64 (cualquiera sirve)
- **Memoria:** 512 MB (Java arranca más rápido con más memoria)
- **Timeout:** 15 s (el primer *cold start* de Java puede tardar varios segundos)
- **Rol:** `LabRole` en AWS Academy (no permite crear roles); en una cuenta normal, el rol
  básico que Lambda crea por defecto (permisos para CloudWatch Logs)

## Despliegue automatizado (`scripts/aws/deploy.sh`)

Se ejecuta en **AWS CloudShell** (ya trae `aws`, `jq` y `curl`):

1. Subir el JAR a una Lambda (por ejemplo `orders-create`) desde la consola, o copiarlo a CloudShell.
   Si no hay JAR local, el script descarga el código de `orders-create`.
2. `./deploy.sh arch-a` → crea/actualiza `orders-create`, `orders-get`, `orders-find-by-customer`,
   `orders-delete` y el REST API `orders-api-mapping` (plantillas, regex, method/integration
   responses, permisos de invocación y stage `prod`).
3. `./deploy.sh arch-b` → crea/actualiza `orders-api` y el REST API `orders-api-proxy`.
4. `./deploy.sh update-code` → sube un JAR nuevo a todas las Lambdas existentes.

Tras desplegar, API Gateway puede tardar unos segundos en reconocer métodos nuevos (responde
`403 Missing Authentication Token` mientras tanto).

## Eventos de prueba (invocación directa, Parte II)

Usar con `orders-create`:

| Archivo | Qué demuestra | Resultado esperado |
|---|---|---|
| `events/create-order.json` | Caso normal | JSON de la orden con `id` generado (`ORD-001`) |
| `events/create-order-2.json` | Campos extra (`id`, `color`) | Se ignoran: el `id` lo asigna el sistema |
| `events/create-order-invalid.json` | Validación de negocio | Error con `errorMessage` = JSON `INVALID_REQUEST` |
| `events/create-order-wrong-type.json` | `quantity: "abc"` | Falla **antes** de llegar a nuestro código: error de parseo del runtime |

> Ejecuta `create-order.json` dos veces seguidas: la segunda devuelve `ORD-002` porque la
> instancia sigue "warm" y conserva la memoria. Si esperas unos minutos (o cambias la
> configuración de la función) vuelve a empezar en `ORD-001`: memoria no persistente.

## Almacenamiento en memoria: consecuencias

- Cada **función** tiene su propia memoria. En la arquitectura A, una orden creada con
  `orders-create` **no** es visible para `orders-get`. Por eso existen datos de ejemplo
  (`ORD-100`, `ORD-101`, `ORD-102`) que cada instancia carga al arrancar.
- En la arquitectura B, todas las rutas comparten la Lambda `orders-api`, así que un POST
  seguido de un GET suele funcionar… mientras la misma instancia siga viva.
- Ninguno de los dos casos es persistencia real. Con una base de datos (p. ej. DynamoDB)
  solo habría que escribir otra implementación de `OrderRepository`.
