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
