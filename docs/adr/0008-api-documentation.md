# ADR-0008: Documentación de la API

| Campo      | Valor                                                        |
|------------|--------------------------------------------------------------|
| Estado     | Aceptado                                                     |
| Fecha      | 14/11/2026                                                   |
| Requisitos | REQ-12                                                       |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), decisión D-10     |

## Contexto

El equipo técnico de Conexa indicó que no hace falta interfaz gráfica: alcanza con la API documentada con Swagger. Esa documentación es la forma en que quien corrige va a probar la aplicación, incluido el login.

## Decisión

La documentación se **genera desde el código con springdoc-openapi**, que también publica la página de Swagger UI.

- Incluye el esquema de seguridad, para cargar el token con el botón "Authorize".
- Cada pedido documenta sus parámetros, sus respuestas y sus errores.
- La documentación y Swagger UI son públicas: no exigen sesión.

## Alternativas consideradas

| Alternativa                          | A favor                                              | En contra                                                  |
|--------------------------------------|------------------------------------------------------|------------------------------------------------------------|
| **Generada desde el código**         | Nunca queda desactualizada; incluye Swagger UI       | Hay que anotar el código para que quede completa            |
| Escrita a mano en un archivo OpenAPI | Control total del contrato                           | Puede desviarse del código sin que nadie lo note            |

## Consecuencias

- La especificación técnica define el contrato antes de programar. Una vez implementado, la documentación generada es la referencia.
- Mantener cada entidad con su propio controlador (ver [ADR-0002](0002-code-organization.md)) permite que Swagger muestre los datos propios de cada una.
- Las anotaciones de documentación quedan en los controladores, junto al código que describen.

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **15/11/2026.** Precisiones que aparecieron al documentar el primer listado (ticket #8):
  - **`page` y `size` figuran como enteros.** La aplicación los recibe como texto para validarlos en un solo lugar ([ADR-0013](0013-parameter-limits-and-invalid-values.md)), pero la documentación los declara como lo que son: enteros opcionales, con su mínimo, su máximo y su valor por defecto. Un test lo comprueba sobre la documentación generada.
  - **Los parámetros y los errores comunes se documentan una sola vez.** Están en `catalog`, y el controlador de cada entidad los toma de ahí en vez de repetirlos.
  - **El esquema de seguridad** se suma con el ticket de la documentación interactiva (REQ-12).
