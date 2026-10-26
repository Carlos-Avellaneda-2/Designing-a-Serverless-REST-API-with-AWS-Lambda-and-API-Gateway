# Predicción: DELETE /orders/{id}

> Sección 17 del taller. Escrita **antes** de implementar el cambio (ver historial de git).
> Redactada por Carlos Avellaneda a partir del diseño existente.

Comportamiento esperado:

- `DELETE /orders/ORD-100` (existe) → `204 No Content`
- `DELETE /orders/ORD-999` (no existe) → `404 Not Found` con el JSON de error

## Mi predicción

**¿Qué clases Java deben cambiar?**

- `OrderRepository`: nuevo método para eliminar por id que indique si existía (p. ej. `boolean deleteById(String id)`).
- `InMemoryOrderRepository`: implementación de ese método.
- `OrderService`: nueva operación `deleteOrder(String id)` que lanza `OrderNotFoundException` si la orden no existe.
- `OrdersApiHandler` (arquitectura B): nueva ruta `DELETE` sobre `/orders/{id}`.
- Arquitectura A: una clase nueva, `DeleteOrderHandler`, siguiendo el patrón de un handler por operación.
- **No** cambian: `Order`, `CreateOrderRequest`, las excepciones de dominio (se reutiliza
  `OrderNotFoundException`) ni el formato de error.

**¿Se necesita una nueva función Lambda?**

- Arquitectura B: **no**. La misma Lambda `orders-api` atiende la nueva ruta; solo hay que subir el JAR nuevo.
- Arquitectura A: **sí** (`orders-delete`), porque allí cada operación tiene su propia Lambda.
  Consecuencia: `orders-delete` tiene su propia memoria, así que solo puede borrar los datos de
  ejemplo o lo que ella misma "conozca".

**¿Cambia la configuración de API Gateway?**

Sí, en ambas arquitecturas: hay que agregar el método `DELETE` al recurso `/orders/{id}` y volver a
desplegar el stage. En B basta con marcar *Lambda proxy integration*. En A además hay que escribir
la plantilla de request (`{"id": ...}`), declarar los method responses 204/404/500 y configurar los
integration responses con sus regex. Esto confirma que en A cada operación nueva cuesta bastante
configuración.

**¿Dónde debe vivir la lógica de borrado?**

En `OrderService` (decide si la orden existe y si se puede borrar) apoyado en el repositorio
(que hace la eliminación física). No en el handler.

**¿Dónde debe vivir la decisión de responder 204 o 404?**

- El **servicio** decide el hecho de negocio: "la orden existía y se borró" o "no existe"
  (lanzando `OrderNotFoundException`). No sabe nada de códigos HTTP.
- La traducción a **204/404** es del adaptador HTTP en la arquitectura B (`OrdersApiHandler`), y de
  API Gateway (regex sobre `errorMessage`) en la arquitectura A.

**¿Qué tests hay que agregar?**

- `OrderServiceTest`: borrar una orden existente y comprobar que luego `getOrder` falla; borrar una
  inexistente lanza `OrderNotFoundException`; borrar dos veces la misma orden falla la segunda vez;
  borrar no afecta a las demás órdenes del cliente.
- `OrdersApiHandlerTest`: `DELETE` existente → 204 sin body; `DELETE` inexistente → 404 con
  `ORDER_NOT_FOUND`; `GET` después del `DELETE` → 404; `DELETE /orders` (colección) → 405.
- `MappingHandlersTest`: el handler de borrado produce el `errorMessage` que coincide con el regex de 404.
- `ArchitectureTest` no cambia y debe seguir pasando.
