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
