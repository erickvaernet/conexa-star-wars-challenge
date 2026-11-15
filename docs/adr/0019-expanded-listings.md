# ADR-0019: Datos completos a pedido en los listados

| Campo      | Valor                                                                       |
|------------|-----------------------------------------------------------------------------|
| Estado     | Aceptado                                                                    |
| Fecha      | 15/11/2026                                                                  |
| Requisitos | REQ-24, REQ-05, REQ-32                                                      |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), sección 4 (decisiones postergadas) |

## Contexto

Un listado devuelve un resumen de cada elemento: su identificador y su nombre ([ADR-0004](0004-pagination-and-filters.md)). Para ver los datos completos hay que pedir cada elemento por separado. El REQ-24 pide poder recibirlos en el mismo listado, a pedido; por defecto el listado sigue devolviendo el resumen (PRD, pregunta 5).

Cómo responde SWAPI, comprobado con diez pedidos reales el 15/11/2026:

| Pedido a SWAPI                                              | Qué responde                                                                          |
|-------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `people?page=1&limit=2&expanded=true` (igual `starships` y `vehicles`) | La misma página y los mismos totales que sin `expanded`, pero cada elemento trae todos sus datos |
| `films?page=1&limit=2&expanded=true`                        | Lo mismo que sin `expanded`: las seis películas juntas, con todos sus datos            |
| `people?name=sky&page=1&limit=2&expanded=true`              | Lo mismo que sin `expanded`: todas las coincidencias, con todos sus datos              |
| `people?page=50&limit=10&expanded=true`                     | Repite la última página, igual que sin `expanded`                                      |
| `people?page=1&limit=100&expanded=true`                     | Los 82 personajes en 47,5 KB, en 1,3 segundos                                          |

- Cada elemento de una página con `expanded=true` tiene exactamente la misma forma que la respuesta al pedido de un elemento (`people/1`): el identificador y, al lado, sus datos.
- Se pidieron así los 157 elementos de People, Starships y Vehicles, y todos se pueden traducir: ninguno tiene una relación con una dirección mal formada ni le falta el nombre.
- La documentación de SWAPI sí nombra `expanded=true`, a diferencia de `page` y `limit`.

Qué dejó preparado el MVP, y qué no:

- La traducción del detalle de cada entidad ya existe en `swapi`, y es la que hace falta para cada elemento de una página completa.
- La fuente de datos responde un listado como una lista de resúmenes. Aun cuando SWAPI ya envía todos los datos (Films y las búsquedas), solo se lee el nombre.
- El núcleo no conoce los datos de detalle de ninguna entidad: de un elemento solo sabe cómo se ve en un listado ([ADR-0002](0002-code-organization.md), nota del 15/11/2026).
- La caché distingue un listado, una búsqueda y un elemento, cada uno con su clave ([ADR-0011](0011-swapi-response-cache.md)).

## Decisión

Los cuatro listados aceptan un parámetro nuevo, **`expanded`**. Con `expanded=true`, cada elemento de `content` es **el mismo objeto que devuelve el detalle** de esa entidad. Sin el parámetro, o con `expanded=false`, nada cambia.

| Tema                    | Decisión                                                                                                         |
|-------------------------|------------------------------------------------------------------------------------------------------------------|
| Valores                 | Exactamente `true` o `false`. Por defecto, `false`. Cualquier otro valor responde `400`                          |
| Forma de la respuesta   | La misma página (`content`, `page`, `size`, `totalElements`, `totalPages`). Solo cambia qué trae cada elemento   |
| Con los filtros         | Se combina con `id` y con `name` igual que el listado: los filtros deciden qué elementos van; `expanded`, cuántos datos trae cada uno |
| Cómo se obtienen        | **Con un solo pedido a SWAPI,** al que se le suma `expanded=true`. Se envía siempre, también a Films y a las búsquedas, que lo ignoran |
| En el código            | La fuente de datos suma dos pedidos: la página con los elementos completos, y la búsqueda con los elementos completos. El núcleo los pagina igual que a los resúmenes, sin mirar qué tienen adentro |
| Caché                   | Las respuestas con datos completos se guardan aparte de las de resúmenes. Además, cada elemento que llega en una de ellas se guarda como si se hubiera pedido por su identificador |
| Documentación de la API | `expanded` figura como booleano. Los elementos de `content` se documentan como "uno de dos": el resumen o el detalle de la entidad |

## Alternativas consideradas

Cómo se obtienen los datos completos. Es la decisión de fondo:

| Alternativa                                         | A favor                                                                                      | En contra                                                                                             |
|-----------------------------------------------------|----------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| **A. La fuente trae la página ya completa**         | Un solo pedido a SWAPI por página, que es lo que SWAPI ofrece para esto; el tiempo no depende del tamaño de la página | Toca la interfaz de la fuente, la lectura de las respuestas de SWAPI y la caché: es el cambio más grande del ciclo |
| B. El núcleo pide el resumen y después cada elemento | No toca `swapi` ni la caché: usa los dos pedidos que ya existen                             | Una página de 10 elementos son 11 pedidos a SWAPI y una de 100, 101: más que su cupo de 100 cada 15 minutos, y cada pedido es más lento que el anterior. Si falla uno, falla la página entera. Hace del lado del servidor justo lo que el requisito quiere evitar |

Nombre y valores del parámetro:

| Alternativa                          | A favor                                                                                      | En contra                                                                                   |
|--------------------------------------|----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **`expanded=true`**                  | Es un sí o un no, y así se documenta; es el mismo nombre que usa SWAPI, conocido para quien viene de ahí | Si más adelante hubiera un tercer nivel de detalle, un booleano no alcanza          |
| `view=summary` o `view=full`         | Admite sumar otros valores sin cambiar el parámetro                                          | Hoy no hay un tercer valor; es más para explicar y para validar                              |
| `fields=name,height,…`               | Quien consume elige cada campo                                                               | Es otro requisito, y más grande: la consulta flexible del REQ-30                             |

Forma de la respuesta:

| Alternativa                                   | A favor                                                                              | En contra                                                                                   |
|-----------------------------------------------|--------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **Los mismos objetos que el detalle**         | Un solo contrato por entidad: lo que se aprende del detalle vale para el listado; no hay tipos nuevos | En Films, el elemento completo tiene `title` y no `name`, como su detalle; el tipo de `content` depende de un parámetro |
| El resumen, con los datos completos adentro (`{id, name, detail: {…}}`) | Todos los elementos tienen siempre `id` y `name`           | Repite el identificador y el nombre; es una tercera forma de ver un elemento                 |
| Una ruta aparte (`/people/expanded`)          | Cada ruta tiene un solo tipo de respuesta, más simple de documentar                  | Duplica los cuatro listados y sus parámetros; choca con la ruta del detalle (`/people/{id}`) |

Caché:

| Alternativa                                                    | A favor                                                                    | En contra                                                                                   |
|----------------------------------------------------------------|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **Entradas aparte, y además guardar cada elemento por su identificador** | Repetir el pedido no vuelve a SWAPI; después de un listado completo, el detalle de cualquiera de sus elementos tampoco | Un recorrido más al guardar; el mismo listado pedido de las dos formas son dos pedidos a SWAPI |
| Entradas aparte, sin guardar cada elemento                     | La más simple                                                              | Pedir el detalle de un elemento que ya llegó completo vuelve a SWAPI                         |
| Una sola entrada por listado, siempre con los datos completos, de la que también sale el resumen | Menos pedidos a SWAPI cuando se piden las dos formas | Un listado común pasaría a traducir todos los datos de cada elemento: una relación mal formada en uno solo haría fallar un listado que hoy funciona. Cambia el comportamiento del MVP |

## Consecuencias

- Quien consume obtiene una página con todos los datos en un pedido, en vez de uno por elemento. Para la aplicación también es un solo pedido a SWAPI.
- **El tipo de los elementos de `content` depende de un parámetro.** Es el costo principal de la decisión. La documentación lo muestra como "uno de dos" y la descripción del parámetro dice cuál llega en cada caso; un cliente generado a partir de la documentación tiene que distinguirlos.
- En Films, un elemento completo se identifica por `title` y uno resumido por `name`. Es el contrato del detalle, sin cambios ([ADR-0012](0012-response-data-shape.md)).
- `expanded=true` no cambia qué elementos se devuelven, ni el orden, ni los totales: las reglas de paginación y de filtros son las mismas.
- Se envía `expanded=true` a SWAPI siempre que se pide, sin preguntar qué entidad es ni si es una búsqueda. Es el criterio del [ADR-0004](0004-pagination-and-filters.md): pedir siempre lo mismo y decidir por lo que llega.
- **El núcleo sigue sin conocer los datos de ninguna entidad.** Recibe de la fuente elementos completos como ya los recibe en el detalle, sin mirar adentro, y los pagina con el mismo código que a los resúmenes. El controlador de cada entidad sigue declarando solo lo propio. Los tests de arquitectura no cambian.
- `swapi` lee los elementos completos de una lista con la traducción del detalle de cada entidad, que ya existe. La tabla que dice con qué traducción se lee cada entidad pasa a usarse también para las listas.
- **Una relación mal formada en un solo elemento hace fallar toda la página completa,** con `502`. Es la regla del [ADR-0012](0012-response-data-shape.md): un dato que falta sin aviso es peor que un error. Hoy ningún elemento de SWAPI está en ese caso. El listado común no corre ese riesgo, porque no lee las relaciones.
- Una página completa de 100 personajes pesa unos 50 KB, contra 6,6 KB de la común. El tope de la caché cuenta respuestas, no bytes ([ADR-0011](0011-swapi-response-cache.md)). El peor caso sigue acotado por el propio cupo de SWAPI: en la hora que dura una entrada no pueden entrar más de 400 respuestas nuevas, que aun siendo todas de las más grandes son algunas decenas de megabytes. El riesgo de una avalancha de pedidos distintos sigue siendo el del REQ-35.
- Pedir un listado completo y después el mismo listado común son dos pedidos a SWAPI: las entradas no se comparten. Se prefiere eso a cambiar cómo se lee el listado común.
- El tiempo máximo de lectura de SWAPI no cambia: la página completa más grande tardó 1,3 segundos.
- Si SWAPI dejara de aceptar `expanded=true`, respondería una página de resúmenes y la aplicación lo vería como elementos sin datos: `502`. No hay un camino alternativo; pedir cada elemento por separado es la alternativa B, descartada.
- El REQ-30 (GraphQL, ciclo 3) resuelve la misma necesidad de otra forma. Esta decisión no lo condiciona: los dos pedidos nuevos de la fuente le sirven igual.
