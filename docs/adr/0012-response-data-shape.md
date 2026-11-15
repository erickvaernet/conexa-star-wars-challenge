# ADR-0012: Forma de los datos en las respuestas

| Campo      | Valor                                                        |
|------------|--------------------------------------------------------------|
| Estado     | Aceptado                                                     |
| Fecha      | 14/11/2026                                                   |
| Requisitos | REQ-01 a REQ-08                                              |
| Origen     | Revisión de la [especificación técnica](../spec/SPEC.md)     |

## Contexto

Los datos que devuelve la API no son propios: salen de SWAPI. La especificación técnica tiene que fijar con qué forma se publican. Cada elección pasa a ser parte del contrato, y cambiarla después rompe a quien ya consume la API.

La traducción se hace en un solo lugar. El [ADR-0002](0002-code-organization.md) definió una frontera hacia SWAPI, la carpeta `swapi`, que funciona como **capa anticorrupción** (*anti-corruption layer*): traduce el modelo de SWAPI al propio y evita que su formato llegue al resto del código. Ese ADR decidió **dónde** se traduce. Este decide **a qué** se traduce.

Sobre los identificadores, verificado con respuestas reales del 14/11/2026:

- SWAPI entrega el identificador como texto (`"uid": "1"`), aunque hoy todos son números.
- No escribe así todos los números: el episodio de una película llega como número (`"episode_id": 4`).
- Todas sus direcciones usan ese identificador: `/people/1`.

Sobre las relaciones entre entidades:

- En el detalle de un elemento, SWAPI entrega sus relaciones como listas de direcciones propias: `"films": ["https://www.swapi.tech/api/films/1", …]`.
- Cada dirección termina en el identificador del elemento relacionado.
- Algunas relaciones apuntan a entidades que esta API no expone: el planeta de origen de un personaje (`homeworld`) y los planetas y las especies de una película (`planets` y `species`). La consigna pide cuatro entidades: People, Films, Starships y Vehicles.

Sobre los valores numéricos, en las mismas respuestas reales:

- SWAPI entrega como texto casi todos los números: la altura de un personaje es `"172"`.
- Muchos no se pueden leer como número sin interpretarlos: `"342,953"` (coma de miles), `"4.0"` (decimales), `"36.8 "` (espacio al final) o `"3 years"` (cantidad con unidad).
- Algunos no son números: `"n/a"` significa que el dato no aplica, y otros elementos traen `"unknown"`, que significa que no se conoce.

## Decisión

| Tema            | Decisión                                                                                                          |
|-----------------|-------------------------------------------------------------------------------------------------------------------|
| Identificadores | **Se devuelven como texto**, tal como los entrega SWAPI (`"id": "1"`). **En la entrada solo se aceptan dígitos.** |
| Relaciones      | **Se devuelven como listas de identificadores** (`"filmIds": ["1", "2"]`), tomados del final de cada dirección de SWAPI. Si una dirección no permite obtenerlo, la respuesta es un error `502` |
| Planetas y especies | **No se devuelven** las relaciones con entidades que esta API no expone                                       |
| Valores numéricos | **Se devuelven como texto**, tal como los entrega SWAPI (`"height": "172"`). No se convierten. A los textos solo se les quitan los espacios del inicio y del final |

## Alternativas consideradas

Identificadores:

| Alternativa                      | A favor                                                                                                   | En contra                                                                                                  |
|----------------------------------|-----------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| **Identificador como texto**     | No promete nada que SWAPI no prometa; si SWAPI cambia el formato, las respuestas no cambian de forma; se pasa tal cual, sin conversión | Quien consume recibe texto aunque hoy todos sean números |
| Identificador como número        | Describe mejor los datos de hoy                                                                           | Supone que SWAPI siempre va a usar números; si deja de hacerlo, cambia el tipo de un campo ya publicado; suma una conversión que puede fallar |
| **Entrada: solo dígitos**        | Rechaza un valor inválido sin llamar a SWAPI; nada inesperado llega a la dirección que se le pide a SWAPI  | Si SWAPI cambia el formato, hay que aflojar la regla                                                       |
| Entrada: cualquier texto         | No habría que tocar nada si SWAPI cambia el formato                                                       | Cada valor inválido gasta un pedido a SWAPI; hay que controlar qué caracteres llegan a su dirección        |

Relaciones:

| Alternativa                          | A favor                                                                                     | En contra                                                                                             |
|--------------------------------------|---------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| **Listas de identificadores**        | Los datos quedan completos; quien consume sigue la relación dentro de esta API, con su login y su formato | Supone que las direcciones de SWAPI terminan en el identificador                         |
| Direcciones de SWAPI, tal cual       | Sin trabajo ni suposiciones                                                                 | Saca el formato de SWAPI fuera de la frontera; quien las sigue sale de esta API, sin login y con otro formato |
| Omitir las relaciones                | La más simple                                                                               | El detalle deja de tener los datos completos (REQ-06)                                                  |
| Direcciones de esta API              | La más cómoda para quien consume                                                            | La misma suposición que los identificadores, más rutas para armar y probar                             |

Planetas y especies:

| Alternativa                          | A favor                                                                                     | En contra                                                                                             |
|--------------------------------------|---------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| **No devolverlas**                   | Todo identificador que devuelve la API se puede consultar en la API; el contrato no carga con campos que hoy no sirven | El detalle no dice de qué planeta es un personaje                          |
| Devolver sus identificadores         | Los datos quedan más completos, con la misma extracción que las otras relaciones             | Son referencias que no llevan a ningún lado dentro de la API: solo sirven a quien vaya a buscarlas a SWAPI |

Valores numéricos:

| Alternativa                          | A favor                                                                                     | En contra                                                                                             |
|--------------------------------------|---------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| **Como texto, sin convertir**        | No se pierde información: "no se conoce" y "no aplica" siguen siendo distintos; no hay reglas de conversión que escribir ni probar | Quien consume recibe texto; si quiere ordenar o comparar, convierte por su cuenta |
| Convertir a número, con `null` si no se puede | Tipos más útiles para quien consume                                                | `"unknown"` y `"n/a"` pasan a ser el mismo `null`; hace falta una regla por cada rareza (comas, decimales, unidades) y cada una puede fallar |

## Consecuencias

- El identificador es de SWAPI, no de esta aplicación. Devolverlo como número sería prometer a quien consume la API algo que SWAPI no promete.
- Si SWAPI empezara a usar identificadores con letras, alcanza con aflojar la validación de la entrada. Es un cambio compatible: las respuestas conservan su forma.
- La validación de la entrada sí supone que hoy los identificadores son numéricos. Es una suposición puesta en un solo lugar y barata de cambiar.
- `episodeId` sigue siendo un número: es un dato de la película, no un identificador, y SWAPI lo entrega como número.
- El borrador de la especificación proponía devolver el identificador como número. Se descartó en la revisión: su ventaja principal, rechazar un identificador inválido sin llamar a SWAPI, no depende del tipo que se devuelve sino de la validación de la entrada, que se mantiene.
- Las relaciones dependen de la forma de las direcciones de SWAPI. No hay una alternativa aceptable que lo evite: las dos que no suponen nada exponen a SWAPI o recortan los datos. La suposición vive solo en `swapi`.
- Si una dirección llega con otra forma, la API responde `502` en vez de omitir la relación. Un dato que falta sin aviso es más difícil de detectar que un error.
- Una API muestra solo lo que ofrece. Un identificador de planeta sin un pedido de planetas obligaría a quien consume a salir a SWAPI, que es lo que la frontera busca evitar.
- No devolver planetas ni especies es un límite declarado, no un olvido. Si más adelante la API expone alguna de esas entidades, sus identificadores se suman como campos nuevos, sin romper el contrato. Quitar un campo ya publicado sí lo rompería.
- Devolver direcciones de esta API queda como mejora posible. Se puede sumar como un campo nuevo, sin romper el contrato.
- Los valores numéricos siguen el mismo criterio que los identificadores: no prometer sobre datos ajenos más de lo que promete su dueño. Un valor mal convertido es peor que un texto fiel.
- La capa anticorrupción traduce el formato de SWAPI, pero no interpreta sus datos: convertirlos exigiría adivinar formatos y perder la diferencia entre "no se conoce" y "no aplica".
- La única limpieza es quitar los espacios del inicio y del final de los textos: `"36.8 "` pasa a ser `"36.8"`. No cambia ningún dato y evita que un descuido de SWAPI se vea en las respuestas. Cualquier corrección mayor ya sería interpretar los datos.
- Esa limpieza se hace en `swapi`, al traducir. No hace falta una capa aparte: es el mismo lugar por el que ya pasan todos los datos de SWAPI.
- Si más adelante hiciera falta ordenar o filtrar por un valor numérico, se puede sumar un campo convertido al lado del texto, sin romper el contrato.
- La capa anticorrupción protege al código de los cambios de SWAPI, pero no al contrato publicado. Por eso estas decisiones se toman acá y no quedan libradas a la implementación.
- Las reglas exactas (cuántos dígitos se aceptan y qué código responde cada caso) están en la [especificación técnica](../spec/SPEC.md#5-validaciones-errores-y-casos-límite).
