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

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **14/11/2026.** Los motivos del tamaño por defecto y del máximo, y qué pasa cuando se pide un valor fuera de rango, están en el [ADR-0013](0013-parameter-limits-and-invalid-values.md). Los valores no cambian.
- **14/11/2026.** Al revisar la especificación técnica se confirmó que un ID inexistente responde distinto según cómo se pida: lista vacía en el filtro (`/people?id=9999`) y 404 en el detalle (`/people/9999`). El motivo, que este ADR no explicaba: el filtro es una búsqueda, y una búsqueda sin resultados no es un error, igual que un nombre sin coincidencias; el detalle es la dirección de un elemento, y si ese elemento no existe corresponde 404. Así el listado responde siempre con la misma forma, cualquiera sea el filtro. La alternativa, 404 en los dos casos, haría que el filtro por ID se comporte distinto que el filtro por nombre.
- **15/11/2026.** Precisiones que aparecieron al implementar el primer listado (ticket #8):
  - **SWAPI no devuelve una lista vacía para una página posterior a la última: repite su última página.** Se vio al grabar `people?page=50&limit=10`, que trajo los dos personajes de la página 9 con `total_pages` 9. Por eso "usar esa página tal cual" tiene una excepción: la aplicación calcula el total de páginas con el total de elementos y el tamaño, y si la página pedida es mayor devuelve la lista vacía, sin usar lo que envió SWAPI. Es la regla que este ADR ya fijaba para el contrato; lo nuevo es que no se puede delegar en SWAPI. La alternativa era comparar con el `total_pages` de SWAPI; se descartó porque el resultado es el mismo y la especificación ya define el total de páginas con una cuenta propia.
  - **Cómo quedó en el código.** La fuente de datos responde de una de dos formas, una página con su total o la lista completa, y el núcleo de `catalog` decide qué hacer según cuál llegó, sin preguntar qué entidad es. Cada entidad declara solo su ruta y su nombre en la fuente.
  - **Qué reconoce `swapi` hoy.** En este ticket traduce la forma con totales, que es la del listado de People. La forma de lista completa se suma cuando aparece: con el filtro por nombre (ticket #11) y con Films (ticket #12). El recorte en memoria ya está en `catalog`, con sus tests unitarios.
- **15/11/2026.** Precisiones que aparecieron al implementar el filtro por ID y el detalle (ticket #10):
  - **Cómo quedó en el código.** La fuente de datos tiene un segundo pedido, "un elemento por su identificador", y que el elemento no exista es una respuesta normal de ese pedido, no una falla. El filtro y el detalle usan ese mismo pedido: el filtro arma con la respuesta una lista de uno o ningún elemento, y el detalle responde `404` si no hay elemento.
  - **El filtro por ID con una página mayor que 1.** La lista de uno o ningún elemento se recorta igual que cualquier lista completa. Por eso `?id=1&page=2` devuelve `content` vacío con `totalElements` 1: es el caso límite de la especificación, y sale de la regla general, sin código propio.
  - **El filtro por ID no recorre el listado.** Los tests de aceptación lo comprueban mirando qué pedidos recibe SWAPI.
  - **Dónde entran los filtros.** Los parámetros del listado llegan al núcleo juntos, la página y los filtros, y el núcleo decide cómo resolverlos. El controlador de cada entidad no decide nada. El filtro por nombre (ticket #11) entra por el mismo lugar.
  - **El filtro por ID de una entidad necesita la traducción de su detalle,** porque el nombre que muestra el listado sale del elemento ya traducido. El código del filtro es uno solo; cada entidad suma su traducción en su ticket ([ADR-0002](0002-code-organization.md), nota del 15/11/2026).
