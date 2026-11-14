# ADR-0004: Paginación y filtros

| Campo      | Valor                                                        |
|------------|--------------------------------------------------------------|
| Estado     | Aceptado                                                     |
| Fecha      | 14/11/2026                                                   |
| Requisitos | REQ-01 a REQ-08                                              |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), decisión D-06     |

## Contexto

El PRD pide que las cuatro entidades se paginen y se filtren de la misma forma. SWAPI no lo hace (verificado con pedidos reales el 14/11/2026):

- Pagina los listados de People, Starships y Vehicles, e informa los totales.
- Ignora la página y el tamaño en el listado de Films y en las búsquedas por nombre: devuelve la lista completa, sin totales.
- Su documentación no menciona los parámetros `page` y `limit`. Solo aparecen en los enlaces `next` y `previous` de sus respuestas, así que pueden cambiar sin aviso.

## Decisión

**Siempre se le envía a SWAPI la página y el tamaño pedidos**, para las cuatro entidades y también en las búsquedas. Después **se decide según cómo respondió**:

| SWAPI responde                        | Qué hace la aplicación                                             |
|---------------------------------------|--------------------------------------------------------------------|
| Con totales de registros y de páginas | Usa esa página tal cual                                            |
| Con la lista completa, sin totales    | Recorta la página pedida en memoria y calcula los totales          |

Los filtros por ID no pasan por la paginación de SWAPI:

| Pedido                       | Cómo se resuelve                                                              |
|------------------------------|-------------------------------------------------------------------------------|
| Filtro por ID                | Se pide ese elemento y se devuelve una página con uno o ningún elemento        |
| Filtro por ID y nombre       | Se pide el elemento por ID y se comprueba que su nombre contenga el texto      |
| Detalle por ID               | Se pide ese elemento; si no existe, se responde 404                            |

Reglas del contrato:

- **Las páginas empiezan en 1.** El tamaño por defecto es 10 y el máximo, 100.
- Todas las respuestas de listado tienen la misma forma: los elementos, la página, el tamaño, el total de elementos y el total de páginas.
- Una página posterior a la última devuelve una lista vacía con los totales correctos, no un error.
- Cada elemento de un listado es un resumen: su ID y su nombre (en Films, el título).
- Las rutas llevan el prefijo `/api/v1`.

## Alternativas consideradas

| Alternativa                       | A favor                                                             | En contra                                                           |
|-----------------------------------|---------------------------------------------------------------------|---------------------------------------------------------------------|
| **Decidir según la respuesta**    | Sin casos especiales por entidad; si SWAPI empieza a paginar Films o las búsquedas, funciona sin tocar el código | Hay que reconocer las dos formas de respuesta |
| Regla fija por entidad            | Más explícita                                                       | Si SWAPI cambia su comportamiento, se rompe                          |
| Traer siempre todo y recortar     | La más simple                                                       | Pide a SWAPI datos que no se muestran                                |
| **Páginas desde 1**               | Como pagina SWAPI y como cuenta una persona                         | Difiere de lo habitual en Spring Data                                |
| Páginas desde 0                   | Lo habitual en Spring Data                                          | Los listados no usan Spring Data; obligaría a convertir el número en cada pedido |

## Consecuencias

- El código de paginación no pregunta qué entidad es: una sola regla vale para las cuatro.
- Hoy, People, Starships y Vehicles sin filtros usan la página de SWAPI; Films y las búsquedas se recortan en memoria.
- La paginación en memoria trae la lista completa. Con decenas de elementos por entidad no es un problema; si una entidad creciera mucho, habría que revisarlo. La caché evita repetir ese pedido en cada página (ver [ADR-0011](0011-swapi-response-cache.md)).
- Límite conocido: si SWAPI empezara a respetar el tamaño de página sin informar los totales, la aplicación tomaría una lista parcial como completa. No se puede distinguir desde afuera.
- El contrato exacto (nombres de parámetros, validaciones y códigos de error) se define en la especificación técnica.
