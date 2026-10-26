# Arquitectura B — API Gateway con Lambda proxy integration

API de tipo **REST API**. Nombre sugerido: `orders-api-proxy`.
Una sola Lambda: `orders-api` (`com.orders.lambda.proxy.OrdersApiHandler::handleRequest`).

## Recursos y métodos

```
/orders            GET  -> orders-api  (proxy)
                   POST -> orders-api  (proxy)
/orders/{id}       GET    -> orders-api  (proxy)
                   DELETE -> orders-api  (proxy)
```

> Se despliega con `scripts/aws/deploy.sh arch-b`.

En cada método: *Integration type* = **Lambda function**, con **Lambda proxy integration ACTIVADO**.

No hay mapping templates, ni regex, ni Method/Integration Responses que configurar:
la Lambda devuelve `statusCode`, `headers` y `body` y API Gateway los usa tal cual.

> Alternativa: un solo recurso `/{proxy+}` con método `ANY`. Configuración mínima, pero
> API Gateway deja de "conocer" las rutas (el campo `resource` sería `/{proxy+}`) y el
> router del handler tendría que trabajar con `path`. Se eligieron rutas explícitas para
> que el contrato siga visible en API Gateway.

## Deploy

*Deploy API* → stage `prod`. Probar con `API_URL=<URL> ./scripts/test-arch-b.sh`.

## Capturar un evento real (sección 11)

`events/proxy-event-example.json` es un ejemplo escrito a mano con el formato de API Gateway.
Para capturar uno real, el handler registra el evento si la variable `LOG_EVENTS` vale `true`
(desactivada por defecto):

```bash
aws lambda update-function-configuration --function-name orders-api \
  --environment 'Variables={LOG_EVENTS=true}'
# ... hacer peticiones y buscar "API Gateway event:" en CloudWatch Logs ...
aws lambda update-function-configuration --function-name orders-api --environment 'Variables={}'
```

Así se obtuvieron `events/proxy-event-real-post.json` y `events/proxy-event-real-get.json`.

## Dónde aparece cada dato en el evento

| Dato | Campo del evento | En el handler |
|---|---|---|
| Método HTTP | `httpMethod` | `event.getHttpMethod()` |
| Ruta real | `path` (`/orders/ORD-100`) | `event.getPath()` |
| Plantilla de ruta | `resource` (`/orders/{id}`) | `event.getResource()` — usado para enrutar |
| Path params | `pathParameters` (`{"id":"ORD-100"}`) | `event.getPathParameters()` |
| Query params | `queryStringParameters` (`{"customerId":"CUST-42"}`) | `event.getQueryStringParameters()` |
| Headers | `headers` / `multiValueHeaders` | `event.getHeaders()` |
| Body | `body` (**string**, no objeto) | `event.getBody()` → se parsea con Jackson |
| Stage, request id, IP… | `requestContext` | `event.getRequestContext()` |
