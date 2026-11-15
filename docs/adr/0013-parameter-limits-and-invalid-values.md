# ADR-0013: Límites del tamaño de página y valores inválidos

| Campo      | Valor                                                        |
|------------|--------------------------------------------------------------|
| Estado     | Aceptado                                                     |
| Fecha      | 14/11/2026                                                   |
| Requisitos | REQ-05 a REQ-08                                              |
| Origen     | Revisión de la [especificación técnica](../spec/SPEC.md)     |

## Contexto

El [ADR-0004](0004-pagination-and-filters.md) fijó el tamaño de página: 10 por defecto y 100 como máximo. Al revisar la especificación técnica aparecieron dos huecos:

- **Los números no tenían motivo escrito.** Entraron como una línea de la decisión, sin alternativas.
- **No estaba decidido qué pasa cuando alguien pide más que el máximo:** si la API rechaza el pedido o lo recorta.

Qué condiciona la decisión:

- La consigna pide listados paginados y no habla de tamaños.
- SWAPI no impone un máximo: con `limit=200` devolvió los 82 personajes en una sola página (verificado el 14/11/2026).
- El tamaño de página por defecto de SWAPI es 10 (verificado el 14/11/2026: un listado sin parámetros devuelve 10 elementos).
- La lista más larga hoy es la de personajes, con 82 elementos.

## Decisión

| Tema                 | Decisión                                                                                                   |
|----------------------|------------------------------------------------------------------------------------------------------------|
| Tamaño de página     | **De 1 a 100, ambos incluidos.** Por defecto, 10. Se mantiene lo fijado en el ADR-0004                     |
| Valor fuera de rango | **Se rechaza con un error `400`** que nombra el parámetro y los valores aceptados. No se corrige en silencio |

La segunda regla vale para todos los parámetros: `page`, `size`, `id` y `name`.

## Alternativas consideradas

Tamaño máximo:

| Alternativa         | A favor                                                                                          | En contra                                                                    |
|---------------------|--------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| **Máximo de 100**   | Tope habitual en APIs públicas; cubre la lista más larga de hoy (82) en una sola página           | El número es una convención, no sale de una medición                         |
| Sin máximo          | Nada que validar; SWAPI tampoco lo tiene                                                         | Un tamaño enorme convierte el listado en "traer todo" y la paginación deja de existir |
| Otro tope (50, 200) | Igual de válido como convención                                                                  | Con 50, la lista de personajes ya no entra en una página; 200 no aporta nada con los datos de hoy |

Valor fuera de rango:

| Alternativa                 | A favor                                                                                     | En contra                                                                                   |
|-----------------------------|---------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **Rechazar con `400`**      | Una sola regla para todos los valores inválidos; quien consume se entera del límite en el momento | Más estricta que lo habitual en Spring                                                |
| Recortar al máximo          | Más tolerante; es lo que hace Spring Data por defecto                                       | Quien pide 500 y recibe 100 puede creer que son todos; obliga a tratar distinto a `size=0`, que no se puede corregir sin inventar un valor |

## Consecuencias

- El mínimo es 1 porque una página de cero elementos no tiene sentido y no permite calcular el total de páginas.
- El tamaño por defecto coincide con el de SWAPI, así que un listado sin parámetros se resuelve con el mismo pedido que SWAPI atiende por defecto.
- Con los datos de hoy, el máximo casi no protege de nada: la lista más larga tiene 82 elementos. Vale como parte del contrato y por si los datos crecen.
- Subir el máximo más adelante es un cambio compatible. Bajarlo no: rompería a quien ya pide páginas grandes.
- Rechazar en vez de recortar se aparta de lo habitual en Spring Data. Es deliberado: la API no devuelve algo distinto de lo que se le pidió sin avisar.
- La única tolerancia es con los espacios al inicio y al final de `name`, que se ignoran: no cambian lo que se busca.
- Los extremos del rango se verifican con un criterio propio (CA-05.5), además de los valores inválidos (CA-05.4). Un error de límites pasaría inadvertido si solo se probaran los valores que fallan.
- Este ADR completa al ADR-0004, no lo reemplaza: los valores son los mismos.

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **15/11/2026.** Precisiones que aparecieron al implementar la validación de `page` y `size` (ticket #8):
  - **Dónde se valida.** Los dos parámetros se reciben como texto y se convierten en `catalog`, en un solo lugar. La alternativa era recibirlos como enteros y validarlos con anotaciones; se descartó porque reparte la misma regla entre la conversión de tipos de Spring, la validación y el formato de los errores, y porque los textos por defecto de esa validación cambian con el idioma de la máquina.
  - **Casos que la especificación no nombra.** También responden `400`: un parámetro vacío (`page=`), uno repetido (`page=1&page=2`) y un número que no entra en un entero de 32 bits. Ninguno es "un entero" válido, y no se corrigen.
  - **`page` no tiene máximo.** Una página muy posterior a la última es un pedido válido y devuelve la lista vacía.
