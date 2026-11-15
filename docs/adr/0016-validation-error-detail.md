# ADR-0016: Detalle campo por campo en los errores de validación

| Campo      | Valor                                                                       |
|------------|-----------------------------------------------------------------------------|
| Estado     | Aceptado                                                                    |
| Fecha      | 15/11/2026                                                                  |
| Requisitos | REQ-21                                                                      |
| Origen     | [ADR-0007](0007-error-format.md), que dejó este punto para el ciclo 2        |

## Contexto

El REQ-21 pide errores claros y con el mismo formato. El MVP ya responde todos sus errores en Problem Details, con textos fijos y sin detalles internos ([ADR-0007](0007-error-format.md)). Ese ADR dejó una sola cosa para este ciclo: "el detalle campo por campo en las validaciones".

Cómo está hoy cada error de validación:

| Pedido                               | Qué responde hoy                                                             | ¿Dice qué falló? |
|--------------------------------------|------------------------------------------------------------------------------|------------------|
| Un parámetro de un listado inválido  | `400` con `detail`: `Parameter 'size' must be an integer between 1 and 100.` | Sí, en el texto  |
| Un cuerpo de login inválido          | `400` con `detail`: `Invalid request content.`                               | No               |

Con el alta de usuarios (REQ-26) llega un segundo pedido con cuerpo, con más reglas que el login: sin el detalle por campo, quien la use no sabría si falló el nombre de usuario, la contraseña o el rol.

Qué condiciona la decisión:

- El estándar de Problem Details ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457#section-3.2)) permite sumar campos propios a la respuesta, y su propio ejemplo de un error de validación usa una lista llamada `errors`.
- Los textos de error de esta API son fijos, están en inglés y nunca repiten lo que envió el cliente ([ADR-0007](0007-error-format.md), notas del 15/11/2026).
- Los textos que trae por defecto la librería de validación cambian con el idioma de la máquina ([ADR-0013](0013-parameter-limits-and-invalid-values.md), nota del 15/11/2026): no sirven como parte de un contrato.

## Decisión

Todo `400` que sale de validar un campo o un parámetro lleva, además de los campos de siempre, una lista **`errors`** con un elemento por cada campo inválido:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/v1/users",
  "errors": [
    { "field": "password", "message": "must have 8 to 72 characters, and at most 72 bytes in UTF-8" },
    { "field": "username", "message": "must have 3 to 50 characters: lowercase letters, digits, '.', '_' or '-'" }
  ]
}
```

| Tema                  | Decisión                                                                                                             |
|-----------------------|----------------------------------------------------------------------------------------------------------------------|
| Forma de cada elemento | `field`, el nombre del campo o del parámetro tal como lo envía el cliente, y `message`, la regla que tiene que cumplir |
| Textos                | **Uno solo por campo, fijo y en inglés,** que dice la regla completa. No depende de qué parte de la regla falló ni repite el valor recibido |
| Orden                 | Por nombre de campo, para que la respuesta sea siempre la misma                                                       |
| Dónde aplica          | En el cuerpo del login y del alta de usuarios, y en los parámetros de los listados y del detalle                      |
| En los listados       | La lista trae **un** elemento: el primer parámetro inválido, en el orden que ya fijó el [ADR-0013](0013-parameter-limits-and-invalid-values.md) |
| `detail`              | No cambia. En el cuerpo sigue siendo `Invalid request content.`; en un parámetro, el texto que ya nombra el parámetro  |
| Cuándo no está        | En un `400` que no sale de un campo: un cuerpo que no es JSON, un parámetro mal codificado, una dirección rechazada    |

## Alternativas consideradas

Forma de cada elemento:

| Alternativa                              | A favor                                                                                   | En contra                                                                                     |
|------------------------------------------|-------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| **`field` y `message`**                  | Se entiende sin conocer ningún estándar; la misma forma sirve para un campo del cuerpo y para un parámetro de la dirección | Los nombres son una convención propia, aunque muy difundida |
| `pointer` y `detail`, como el ejemplo del RFC 9457 | Es la forma que muestra el estándar                                              | `pointer` es una ruta dentro de un cuerpo JSON (`#/username`): no existe para un parámetro como `size`, y obligaría a dos formas distintas |
| Nombrar el campo dentro de `detail`, sin lista | Sin campos nuevos; es lo que ya hacen los listados                                   | Con dos campos inválidos hay que elegir cuál se nombra, o armar un texto que un programa no puede leer |

Textos:

| Alternativa                              | A favor                                                                                   | En contra                                                                                     |
|------------------------------------------|-------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| **Un texto fijo por campo, con la regla completa** | Es el criterio que ya usan los parámetros de los listados; un campo nunca aparece dos veces; se puede comprobar con un test, letra por letra | No dice cuál de las partes de la regla falló |
| Un texto por cada regla que falla        | Más preciso: "falta" no es lo mismo que "es muy largo"                                    | Un campo vacío rompe varias reglas a la vez y aparecería repetido; más textos que fijar y probar |
| Los textos por defecto de la librería    | Nada que escribir                                                                         | Cambian con el idioma de la máquina; no son parte de ningún contrato                           |

Parámetros de los listados:

| Alternativa                              | A favor                                                                                   | En contra                                                                                     |
|------------------------------------------|-------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| **Sumarles `errors`, con el primer parámetro inválido** | Un cliente lee todos los errores de validación de la misma forma; no cambia la validación, que sigue en un solo lugar | Si hay dos parámetros inválidos, se entera del segundo recién al corregir el primero |
| Informar todos los parámetros inválidos a la vez | Más completo                                                                      | Obliga a rehacer la validación de los cuatro parámetros para que junte los errores en vez de cortar en el primero; poco valor para pedidos que se arman a mano |
| Dejarlos como están, sin `errors`        | Ningún cambio                                                                             | Dos formas de leer un error de validación en la misma API                                      |

## Consecuencias

- Quien consume la API sabe qué campo corregir sin leer documentación, y un programa puede leerlo sin interpretar un texto.
- Es un cambio compatible: se suma un campo a una respuesta que ya existía. `detail`, `title` y `status` no cambian, así que los tests del MVP siguen pasando sin tocarse.
- El login gana el detalle por campo aunque es público. No revela nada: las reglas que informa (que el campo no esté vacío, su largo) se comprueban antes de mirar ninguna credencial y son las mismas para cualquier usuario.
- Un campo con varias reglas tiene un solo texto. Agregar una regla a un campo obliga a reescribir ese texto; es deliberado, para que el texto siempre diga la regla entera.
- En los listados la lista tiene siempre un elemento. Es un límite declarado: informar todos a la vez queda como mejora posible, y sería compatible.
- El resto de la tabla de errores no cambia de formato. Se le suman los códigos que llegan con el rol de administrador y el alta de usuarios, `403` y `409` ([ADR-0017](0017-role-based-access.md) y [ADR-0018](0018-user-creation.md)), y dos que el MVP ya respondía y la especificación no listaba: `406` y `415`.
- **El `415` pasa a tener un texto fijo.** El que Spring trae por defecto repite el tipo de contenido que envió el cliente (`Content-Type 'text/plain' is not supported.`), contra el criterio del [ADR-0007](0007-error-format.md) de no devolver nada que haya escrito el cliente. Se comprobó con la aplicación levantada el 15/11/2026.
- La documentación de la API muestra `errors` en los `400` de los pedidos con validación, con un esquema propio que extiende al de Problem Details.
- **Una parte de la historia del REQ-21 queda sin cubrir: la sesión vencida.** La historia la nombra entre lo que quien consume quiere entender. Un token vencido sigue recibiendo exactamente el mismo `401` que un pedido sin token o con un token inválido: la respuesta dice que hay que autenticarse, no que la sesión venció.
  - Es así por una decisión ya tomada, que no se reabre ([ADR-0005](0005-authentication.md), nota del 15/11/2026): quien consume tiene que hacer lo mismo en todos los casos, iniciar sesión de nuevo, y una sola respuesta no le cuenta nada a quien prueba tokens.
  - Distinguirlo es posible y de bajo riesgo: solo se le diría a un token que la aplicación emitió. Pero cambia esa decisión, y haría falta un ADR nuevo. Queda anotado como mejora posible.
