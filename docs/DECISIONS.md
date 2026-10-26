# Decisiones tomadas

### D1. Una Lambda por operación en A; una sola Lambda en B

- **Decisión:** en la arquitectura A hay cuatro funciones (`orders-create`, `orders-get`,
  `orders-find-by-customer`, `orders-delete`); en B, una sola (`orders-api`) con un router interno. Todas usan el mismo JAR.
- **Por qué:** en A, API Gateway ya enruta y transforma cada método hacia una entrada
  específica; tener un handler por tipo de entrada es lo natural. En B, el evento es el mismo
  para todas las rutas, así que enrutar en Java evita duplicar configuración.
- **Alternativas:** A con una sola Lambda y un campo `"operation"` puesto por la plantilla; B
  con una Lambda por ruta (cada una con su mini-adaptador).
- **Consecuencia no obvia:** en A cada función tiene su propia memoria, así que lo que crea
  `orders-create` no lo ve `orders-get` (ver D4).

### D2. La validación vive en `OrderService` (capa de aplicación)

- **Decisión:** campos obligatorios y `quantity >= 1` se validan en el servicio, que lanza
  `InvalidOrderException`. El adaptador solo traduce a 400.
- **Por qué:** son reglas de negocio; deben cumplirse sin importar si la llamada viene de
  HTTP, de una invocación directa o de un test.
- **Alternativas:** validar en API Gateway con *Request Validators* + JSON Schema (rechaza
  antes de invocar Lambda, más barato, pero duplica reglas en otro lenguaje y el formato de
  error es el de API Gateway); validar en el handler (mezcla HTTP con negocio).
- **Matiz:** errores de *formato* (JSON inválido, tipo incorrecto) **no** se validan en el
  servicio: son problemas del protocolo y los detecta el adaptador (`MALFORMED_JSON`) o el
  runtime de Lambda.

### D3. Errores en la arquitectura A: el `errorMessage` es el JSON de error completo

- **Decisión:** `GatewayErrorException` (paquete `lambda.mapping`) usa como mensaje
  `{"error":"ORDER_NOT_FOUND","message":"..."}`. API Gateway hace regex sobre el código y la
  plantilla devuelve el mensaje tal cual.
- **Por qué:** en no-proxy la Lambda no puede fijar el status code; la única señal que ve API
  Gateway es `errorMessage`.
- **Cambio respecto a la primera versión:** inicialmente se puso el prefijo
  (`ORDER_NOT_FOUND: ...`) **en las excepciones de dominio**. Se cambió para que el dominio
  no conozca ese detalle de integración: el handler captura la excepción de dominio y la
  relanza con el formato que API Gateway necesita.
- **Alternativas:** prefijo de texto + plantilla que arma el JSON en VTL; devolver un objeto
  "exitoso" con un campo `status` y usar `$context.responseOverride.status` en la plantilla
  (evita excepciones pero hace que todo error sea un "éxito" para Lambda y sus métricas).
- **Riesgo:** si un error no coincide con ningún regex, el cliente recibe **200**.

### D4. Datos de ejemplo precargados (`SampleOrders`)

- **Decisión:** cada instancia arranca con `ORD-100`, `ORD-101` (CUST-42) y `ORD-102` (CUST-7).
- **Por qué:** la memoria no es compartida ni persistente; sin datos semilla no se podría
  demostrar un GET 200 en la arquitectura A.
- **Alternativas:** DynamoDB (fuera del alcance del taller); una sola Lambda para todo (ver D1).

### D5. El `id` lo genera el servidor y se ignoran campos desconocidos

- **Decisión:** `CreateOrderRequest` no tiene `id`. Si el cliente envía `id` u otros campos,
  se ignoran (Jackson con `FAIL_ON_UNKNOWN_PROPERTIES = false`, igual que el runtime de Lambda).
- **Alternativa:** responder 400 si el cliente envía `id` o campos desconocidos (más
  estricto, detecta errores del cliente antes, pero rompe clientes al evolucionar el API).

### D6. `CreateOrderRequest` es un POJO mutable dentro del paquete `domain`

- **Decisión:** constructor vacío + setters, sin anotaciones.
- **Por qué:** es lo que necesita el serializador del runtime de Lambda para la invocación
  directa (Parte II) sin agregar clases intermedias.
- **Alternativa:** un `record` inmutable en el dominio y un DTO mutable en la capa Lambda que
  se convierte al record. Más limpio, pero una clase más por operación.

### D7. Un solo módulo Maven + `ArchitectureTest`

- **Decisión:** la separación se hace por paquetes; un test falla si `domain`, `application` o
  `persistence` importan AWS, Jackson o `com.orders.lambda`.
- **Alternativa:** proyecto multi-módulo (`orders-core` sin dependencias de AWS +
  `orders-lambda`). El compilador garantizaría la separación, pero complica el build y el
  empaquetado para un taller.

### D8. Router manual en el adaptador de la arquitectura B

- **Decisión:** `switch` sobre `resource` + `httpMethod`.
- **Alternativas:** `aws-serverless-java-container` (permite usar JAX-RS o Spring con
  anotaciones), o Micronaut/Quarkus. Escalan mejor a 30 rutas, pero agregan dependencias,
  tiempo de *cold start* y esconden justamente lo que el taller quiere que se vea.

### D9. Errores inesperados → 500 genérico, detalle solo en logs

- **Decisión:** cualquier `RuntimeException` no prevista se registra en CloudWatch
  (`System.err`) y el cliente recibe `{"error":"INTERNAL_ERROR","message":"Unexpected internal error"}`.
- **Por qué:** no filtrar stack traces ni nombres de clases (riesgo de seguridad).
- **Alternativa:** incluir un `requestId` en la respuesta para correlacionar con los logs.

### D10. Respuesta de búsqueda como array plano y 200 `[]` si no hay resultados

- **Decisión:** `GET /orders?customerId=CUST-100` → `200 []`, no 404.
- **Por qué:** es un filtro sobre la colección; una colección vacía es un resultado válido.
- **Alternativa:** envolver en `{"orders": [...]}` para poder agregar paginación sin romper el contrato.

### D11. DELETE: el servicio decide el hecho, la capa HTTP el código

- **Decisión:** `OrderService.deleteOrder` no devuelve nada y lanza `OrderNotFoundException` si la
  orden no existe; el repositorio devuelve `boolean`.
- **Alternativa:** que el servicio devuelva `boolean` y el adaptador decida 204/404 con un `if`.
  Es más explícito, pero en la arquitectura A obligaría al handler a lanzar el error él mismo.

### D12. En la arquitectura A, el handler de DELETE devuelve `Void`

- **Decisión:** `RequestHandler<OrderIdInput, Void>`; API Gateway usa una plantilla vacía para el 204.
- **Alternativa:** devolver la orden borrada y responder 200 con ella. Válido en REST, pero el
  taller pide 204.

### D13. Despliegue con un script de AWS CLI en CloudShell (`scripts/aws/deploy.sh`)

- **Decisión:** toda la configuración de Lambda y API Gateway está en un script idempotente.
- **Por qué:** la configuración de la arquitectura A en la consola son decenas de clics por
  método; el script la hace reproducible y versionada, que es lo que pide el entregable.
- **Alternativas:** consola web (más didáctica, nada reproducible), AWS SAM/CloudFormation
  (lo estándar en la industria, pero con más conceptos nuevos), Terraform.

### D14. Variable `LOG_EVENTS` para capturar el evento real del proxy

- **Decisión:** el adaptador imprime el evento solo si `LOG_EVENTS=true`; se activó unos
  segundos para capturar `events/proxy-event-real-*.json` y se desactivó.
- **Por qué:** registrar siempre el evento completo llena los logs y expone headers e IPs.
- **Alternativa:** un `RequestStreamHandler` temporal que registre los bytes crudos (mostraría el
  JSON exacto de API Gateway, sin los `null` que añade la serialización del objeto Java).
