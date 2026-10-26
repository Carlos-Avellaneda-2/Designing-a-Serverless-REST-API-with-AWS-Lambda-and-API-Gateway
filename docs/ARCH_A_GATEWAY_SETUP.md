# Arquitectura A — API Gateway con integración no-proxy (mapping templates)

> **Fuente de verdad:** `scripts/aws/deploy.sh arch-a` aplica exactamente esta configuración.
> Este documento la explica paso a paso para reproducirla en la consola.

API de tipo **REST API** (no HTTP API: las HTTP APIs no tienen mapping templates).
Nombre sugerido: `orders-api-mapping`.

## Recursos y métodos

```
/orders            GET  -> orders-find-by-customer
                   POST -> orders-create
/orders/{id}       GET    -> orders-get
                   DELETE -> orders-delete
```

En todos los métodos: *Integration type* = **Lambda function**, con **Lambda proxy integration DESACTIVADO**.

## Cómo devuelve errores la Lambda

Cuando el handler lanza una excepción, el runtime de Java responde a API Gateway:

```json
{
  "errorMessage": "{\"error\":\"ORDER_NOT_FOUND\",\"message\":\"Order ORD-999 does not exist\"}",
  "errorType": "com.orders.lambda.mapping.GatewayErrorException",
  "stackTrace": ["..."]
}
```

- API Gateway aplica los **regex** de las *Integration Responses* sobre `errorMessage`.
- La plantilla de respuesta devuelve solo `errorMessage` (que ya es nuestro JSON de error).
  El `stackTrace` nunca llega al cliente.
- Si un error **no** coincide con ningún regex, API Gateway usa la respuesta por defecto
  (**200**). Por eso hay un regex para 500. Es una debilidad conocida de esta arquitectura.

## 1. `GET /orders/{id}`

**Integration Request → Mapping templates** (Content-Type `application/json`):

```velocity
{
  "id": "$util.escapeJavaScript($input.params('id'))"
}
```

**Method Response:** declarar 200, 400, 404 y 500.

**Integration Responses:**

| Lambda error regex | Status | Mapping template (`application/json`) |
|---|---|---|
| *(vacío = por defecto)* | 200 | *(sin plantilla: pasa el JSON de la orden)* |
| `.*"ORDER_NOT_FOUND".*` | 404 | Plantilla de error (abajo) |
| `.*("INVALID_REQUEST"\|JSON).*` | 400 | Plantilla de error (abajo) |
| `.*("INTERNAL_ERROR"\|Task timed out).*` | 500 | Plantilla de error (abajo) |

## 2. `GET /orders?customerId=...`

**Method Request → URL query string parameters:** agregar `customerId`
(sin marcar *Required*, para que la validación la haga el servicio y responda 400 con nuestro formato).

**Integration Request → Mapping templates** (`application/json`):

```velocity
{
  "customerId": "$util.escapeJavaScript($input.params('customerId'))"
}
```

**Method Response:** 200, 400, 500.

**Integration Responses:**

| Lambda error regex | Status | Mapping template |
|---|---|---|
| *(por defecto)* | 200 | *(sin plantilla)* |
| `.*("INVALID_REQUEST"\|JSON).*` | 400 | Plantilla de error |
| `.*("INTERNAL_ERROR"\|Task timed out).*` | 500 | Plantilla de error |

## 3. `POST /orders`

**Integration Request → Mapping templates** (`application/json`):

```velocity
$input.json('$')
```

**Method Response:** 201 (con header `Location`), 400, 500.

**Integration Responses:**

| Lambda error regex | Status | Mapping template |
|---|---|---|
| *(por defecto)* | **201** | Plantilla de creación (abajo) |
| `.*("INVALID_REQUEST"\|JSON).*` | 400 | Plantilla de error |
| `.*("INTERNAL_ERROR"\|Task timed out).*` | 500 | Plantilla de error |

**Plantilla de creación (201)** — devuelve la orden y agrega el header `Location`:

```velocity
#set($id = $input.path('$.id'))
#set($context.responseOverride.header.Location = "/orders/$id")
$input.json('$')
```

## 4. `DELETE /orders/{id}`

**Integration Request:** la misma plantilla que `GET /orders/{id}` (`{"id": ...}`).

**Method Response:** 204, 400, 404, 500.

**Integration Responses:**

| Lambda error regex | Status | Mapping template |
|---|---|---|
| *(por defecto)* | **204** | Plantilla vacía: `#set($empty = "")` (sin ella se devolvería el `null` de la Lambda) |
| `.*("INVALID_REQUEST"\|JSON).*` | 400 | Plantilla de error |
| `.*"ORDER_NOT_FOUND".*` | 404 | Plantilla de error |
| `.*("INTERNAL_ERROR"\|Task timed out).*` | 500 | Plantilla de error |

## Plantilla de error (400 / 404 / 500)

```velocity
#set($msg = $input.path('$.errorMessage'))
#if($msg.startsWith('{'))
$msg
#else
{"error":"MALFORMED_JSON","message":"Request body could not be parsed"}
#end
```

- Si el error vino de nuestro código, `errorMessage` ya es el JSON de error y se devuelve tal cual.
- Si vino del runtime (p. ej. `quantity: "abc"` no se pudo convertir a `Integer`), el mensaje
  es texto plano del runtime; la plantilla lo reemplaza por un error con nuestro formato.
  Para 500 puedes usar la misma plantilla cambiando el `#else` por
  `{"error":"INTERNAL_ERROR","message":"Unexpected internal error"}`.

> **Verificado en AWS:** invocando `orders-create` con `events/create-order-wrong-type.json`, el
> runtime responde `errorMessage` = `An error occurred during JSON parsing` y `errorType` =
> `java.lang.RuntimeException` (ver `docs/img/lambda/12-tipo-incorrecto-json-parsing.jpg`).
> Por eso el regex `JSON` del 400 captura este caso.

## Deploy

*Deploy API* → stage `prod`. La URL queda como
`https://<api-id>.execute-api.<región>.amazonaws.com/prod`.

Probar con `API_URL=<esa URL> ./scripts/test-arch-a.sh`.
