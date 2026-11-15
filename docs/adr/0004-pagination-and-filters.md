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
- **15/11/2026.** Precisiones que aparecieron al implementar el filtro por nombre y su combinación con el ID (ticket #11):
  - **Cómo responde SWAPI a una búsqueda** (pedidos reales del 15/11/2026). Devuelve todas las coincidencias juntas en un campo `result`, sin totales; cada una trae su identificador y todos sus datos, y el nombre está dentro de esos datos. Ignora la página y el tamaño: `people?name=SKY&page=2&limit=2` devolvió lo mismo que `people?name=sky&page=1&limit=10`. No distingue mayúsculas. Sin coincidencias responde `200` con la lista vacía, no un `404`.
  - **Cómo quedó en el código.** La fuente de datos tiene un tercer pedido, "los elementos cuyo nombre contiene un texto", que responde con las mismas dos formas que un listado. La traducción de `swapi` reconoce las dos mirando lo que llegó: si vienen la página y el total, es una página; si viene la lista completa, es la lista completa; si no viene ninguna, `502`. Vale igual para un listado y para una búsqueda, así que el listado de Films (ticket #12), que SWAPI no pagina, ya se lee sin tocar nada. Un elemento sin identificador o sin nombre es `502` en las dos formas.
  - **El campo de búsqueda depende de la entidad.** En la lista completa el nombre de cada elemento está en un campo de sus datos, y ese campo es también el parámetro por el que SWAPI busca: `name`, y en Films `title`. Cada entidad lo declara junto a su nombre en SWAPI; el núcleo no lo interpreta, se lo pasa a la fuente. La alternativa era una tabla en `swapi`, del nombre de la entidad a su campo. Se descartó porque la entidad ya declara su nombre en SWAPI de la misma forma, y así los dos datos que la fuente necesita de una entidad están en un solo lugar.
  - **ID y nombre juntos.** Se pide el elemento por su identificador, como fijaba este ADR, y se comprueba en `catalog` que su nombre contenga el texto. No se le hace a SWAPI ninguna búsqueda: los tests de aceptación lo comprueban mirando qué pedidos recibe. Es el mismo pedido que el del filtro por ID y el del detalle, así que comparte su respuesta guardada. Si el nombre no coincide o el elemento no existe, la lista queda vacía, con los totales en 0.
  - **Mayúsculas.** El texto de búsqueda se guarda en minúsculas apenas se valida, y así se le envía a SWAPI y se compara. `name=SKY` y `name=sky` no son dos pedidos que dan lo mismo: son el mismo pedido. De SWAPI solo se depende en que encuentre "Skywalker" buscando `sky`, que es lo grabado. Las minúsculas se calculan con una regla fija y no con el idioma de la máquina: en una configurada en turco, la minúscula de "I" no es "i".
  - **SWAPI interpreta el texto como una expresión regular.** No estaba previsto y se vio al grabar: `people?name=l.ke` devolvió a Luke Skywalker, porque el punto coincide con cualquier carácter, y `people?name=(` respondió `400` con un error de su base de datos ("Regular expression is invalid"), que para esta API sería un `502`. La especificación dice "cuyo nombre contiene el texto", así que el texto se busca tal cual: antes de enviarlo, `swapi` le antepone una barra invertida a cada carácter que significa algo en un patrón (`\ ^ $ . | ? * + ( ) [ ] { }`). Comprobado con la SWAPI real: con la barra, `l\.ke` ya no devuelve a Luke y `\(` responde `200` con la lista vacía.
    - Límite de esa comprobación: ningún nombre real de SWAPI tiene uno de esos caracteres, así que la prueba es por la negativa (dejan de aparecer el resultado de más y el error). No hay un caso real que muestre que un nombre con un punto se encuentra buscando ese punto.
    - *No hacer nada* era la alternativa más simple: `name=.` devolvería los 82 personajes y `name=(` un `502`. Se descartó porque no cumple "contiene el texto".
    - *Volver a filtrar en `catalog` lo que devuelve la búsqueda* quitaría los resultados de más, pero no el error del paréntesis. Con el escape no hace falta, y el núcleo usa las coincidencias de la fuente como llegan.
    - Vive en `swapi` y no en `catalog` porque es una particularidad de SWAPI: otra fuente no la tendría.
  - **Qué no decide la aplicación.** Fuera de las mayúsculas y de esos caracteres, qué cuenta como coincidencia lo decide SWAPI. No se probó, por ejemplo, cómo trata los acentos.
- **15/11/2026.** Confirmado con la respuesta real al implementar Films (ticket #12): `films?page=2&limit=2` devolvió las seis películas juntas en `result`, sin totales, en el orden de sus identificadores, que es el de estreno y no el de los episodios. Se leyó como lista completa y la página se recortó en `catalog`, sin tocar la paginación ni el cliente de SWAPI. La búsqueda `films?title=hope` respondió con la misma forma.
