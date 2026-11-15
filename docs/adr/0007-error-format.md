# ADR-0007: Formato de los errores

| Campo      | Valor                                                        |
|------------|--------------------------------------------------------------|
| Estado     | Aceptado                                                     |
| Fecha      | 14/11/2026                                                   |
| Requisitos | REQ-06, REQ-10, REQ-21                                       |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), decisión D-09     |

## Contexto

La API tiene que avisar con claridad cuando algo sale mal: un dato inválido, un pedido sin sesión, un elemento que no existe o una falla de SWAPI. Quien la consume necesita poder leer esos errores siempre de la misma forma.

## Decisión

Todos los errores se devuelven en formato **Problem Details ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457))**, que Spring trae incorporado.

Errores que cubre el MVP:

| Situación                         | Código      |
|-----------------------------------|-------------|
| Dato inválido en el pedido        | 400         |
| Sin sesión, o sesión vencida      | 401         |
| Elemento inexistente              | 404         |
| SWAPI responde con un error       | 502         |
| SWAPI no responde a tiempo        | 504         |

## Alternativas consideradas

| Alternativa         | A favor                                              | En contra                                       |
|---------------------|------------------------------------------------------|-------------------------------------------------|
| **Problem Details** | Es un estándar; Spring lo trae incorporado           | —                                               |
| Formato propio      | Libertad total                                       | Hay que inventarlo, documentarlo y mantenerlo    |

## Consecuencias

- Un cliente que ya conoce el estándar sabe leer los errores sin documentación adicional.
- Los errores de seguridad usan el mismo formato que el resto.
- Ninguna respuesta de error muestra detalles internos, como nombres de clases o trazas.
- El MVP cubre los errores básicos. El pulido completo (por ejemplo, el detalle campo por campo en las validaciones) es el REQ-21, del ciclo 2.

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **15/11/2026.** Precisiones que aparecieron al implementar los primeros errores (ticket #6):
  - **Un solo lugar da formato a los errores,** en `shared`. También pasan por ahí los `401` de las reglas de acceso, que Spring Security resuelve fuera de los controladores.
  - **El campo `type` se escribe siempre.** Spring lo omite cuando el error no tiene un tipo propio, y el estándar lo permite; se escribe igual (`about:blank`) para que todos los errores tengan los mismos campos, como muestra la especificación técnica.
  - **Los textos de `detail` son fijos y están en inglés,** igual que los títulos y los mensajes que genera Spring, para no mezclar idiomas en una misma respuesta. El mensaje de la excepción nunca llega al cliente. El ejemplo en español de la especificación técnica muestra la forma de la respuesta, no el texto.
  - **Un error inesperado responde `500`** con un texto genérico, y la causa queda en el registro de la aplicación. Se sumó junto con el login porque, con todo cerrado por defecto, sin esto una falla interna del login se veía desde afuera como un `401`.
- **15/11/2026.** Errores que se sumaron con el primer listado (ticket #8):
  - **`400` por un parámetro inválido.** El `detail` nombra el parámetro y la regla que tiene que cumplir: `Parameter 'size' must be an integer between 1 and 100.` Es un texto fijo por parámetro y nunca repite el valor recibido, para no devolver nada que haya escrito el cliente.
  - **`502` y `504`.** La causa (qué se le pidió a SWAPI y qué pasó) queda en el registro de la aplicación; la respuesta lleva un texto fijo que no nombra direcciones ni clases.
  - **Dónde viven esos tres errores.** Como excepciones de `shared`, porque el lugar que da formato a los errores no puede depender de `catalog` ni de `swapi` ([ADR-0002](0002-code-organization.md)).
  - **`404` de una ruta que no existe.** Spring escribía su propio texto, que nombraba un detalle interno (`No static resource api/v1/people.`). Se reemplazó por uno fijo: `The requested resource was not found.`
- **15/11/2026.** Errores que se sumaron con el filtro por ID y el detalle (ticket #10):
  - **`404` de un elemento que no existe.** Lleva el mismo texto fijo que una ruta que no existe: `The requested resource was not found.` Para quien consume la API son lo mismo, una dirección sin nada detrás, y un texto distinto no le serviría para hacer algo diferente. Vive en `shared`, como los demás errores.
  - **`400` por un identificador inválido.** Sigue la forma de los otros parámetros: `Parameter 'id' must be a number of 1 to 10 digits, without leading zeros.` Es el mismo texto en el parámetro del listado y en la ruta del detalle, porque la regla es una sola.
  - **`502` por una relación con una dirección mal formada.** Usa el texto fijo de cualquier falla de la fuente de datos. La dirección que no se pudo leer queda en el registro de la aplicación y no llega a la respuesta.
- **15/11/2026.** La nota del ticket #6 habla del "ejemplo en español de la especificación técnica". Ese ejemplo cambió en el ticket #8: la sección 5 de la especificación muestra desde entonces el texto real, `Parameter 'size' must be an integer between 1 and 100.`
- **15/11/2026.** Errores que se corrigieron después de la revisión final del MVP, que probó entradas mal formadas contra la aplicación levantada:
  - **`400` por un parámetro mal codificado.** Un `%` suelto (`name=100%`) o bytes que no son texto UTF-8 respondían `500`. El servidor web descubre el problema recién cuando se leen los parámetros, ya dentro del pedido, y el error caía en el caso inesperado. Pasaba con cualquier parámetro, también con los que la aplicación ignora, y en el control de estado, sin sesión. Ahora responde `400` con un texto fijo: `The request has a parameter that is not correctly encoded.` El mensaje original no se usa, porque repite lo que envió el cliente, y no queda nada en el registro de la aplicación: es un error del cliente, como cualquier otro parámetro inválido.
  - **Los pedidos que se rechazan antes de llegar a un controlador.** El servidor web y las reglas de acceso rechazan por su cuenta algunos pedidos: una dirección con `;`, con `//` o con `..`, un método apagado (`TRACE`) o que no es de HTTP. No los responden: los reenvían a una dirección interna, `/error`, con el código que eligieron. Ese reenvío no lleva el token, así que las reglas de acceso lo respondían con `401`, aun con un token válido, y la respuesta nombraba `/error` y no la dirección pedida. Ahora el reenvío está permitido ([ADR-0005](0005-authentication.md), nota de esta fecha) y lo responde una clase de `shared`, en Problem Details, con el código que eligió quien rechazó el pedido y la dirección que pidió el cliente. Reemplaza a la respuesta que trae Spring Boot para `/error`, que tiene otro formato.
  - **Qué responde cada caso.** Una dirección rechazada, `400` con `The request was rejected.` Un método apagado, `405` con `The method of the request is not supported.`; no nombra el método, como sí hace el `405` de una ruta que no lo tiene, porque cuando el pedido llega a `/error` el servidor web ya lo reemplazó por `GET`. Pedir `/error` de forma directa no es un reenvío: con sesión responde `404`, como cualquier ruta que no existe.
  - **Un método que no es de HTTP responde `400`, no `501`.** Las reglas de acceso lo rechazan igual que a una dirección con `;`, y recibe la misma respuesta. El estándar de HTTP sugiere `501` para un método que el servidor no reconoce. Se descartó porque pedía código propio en las reglas de acceso para distinguir un rechazo de otro, y porque responde un error del servidor a algo que escribió el cliente.
  - **Alternativa descartada.** Declarar a mano qué responden las reglas de acceso ante cada rechazo cubría las direcciones, pero no lo que rechaza el servidor web. La respuesta en `/error` cubre los dos casos en un solo lugar.
  - **Lo que sigue fuera.** Lo que el servidor web rechaza antes de armar el pedido (un carácter prohibido sin codificar, como `|` o `[`, o una cabecera demasiado grande) responde su propio `400`, sin pasar por la aplicación y sin este formato.
  - **Un error que se quitó.** El manejo de "no hay nada que atienda esta ruta" no se podía alcanzar: Spring Boot asigna a toda ruta un manejador de archivos estáticos, que es el que responde que la ruta no existe. Ningún test lo ejecutaba y 231 pedidos a rutas inexistentes, con siete métodos, tampoco.
- **15/11/2026.** Lo que cambió con el detalle campo por campo (ticket #35, [ADR-0016](0016-validation-error-detail.md)):
  - **`400` de un cuerpo con campos inválidos.** El `detail` sigue siendo `Invalid request content.`, y la respuesta suma la lista `errors`. Ese texto era el que trae Spring; ahora está escrito en el mismo lugar que los demás, para que no dependa de la versión de Spring.
  - **`400` de un parámetro inválido.** El `detail` no cambia y la respuesta suma `errors`, con ese parámetro. La excepción de `shared` ya traía por separado el nombre y la regla, así que la validación no se tocó.
  - **`415` con texto fijo.** Un cuerpo que no se envía como JSON respondía con el texto de Spring, que repite el `Content-Type` recibido (`Content-Type 'text/plain;charset=UTF-8' is not supported.`). Ahora responde `The content type of the request is not supported.` Un `Content-Type` que ni siquiera se puede leer recibe el mismo texto: antes tenía otro, también de Spring. La respuesta conserva la cabecera `Accept`, que escribe el servidor con los tipos que sí acepta.
  - **Lo que no cambia.** Un `400` que no sale de un campo (un cuerpo que no es JSON, un parámetro mal codificado, una dirección rechazada) no lleva `errors`, y el resto de los errores tampoco.
  - **Lo que sigue repitiendo algo del pedido.** El `405` de una ruta que no tiene ese método nombra el método recibido (`Method 'POST' is not supported.`). Es uno de los métodos de HTTP, no un texto libre; no se cambió en este ticket.
