# ADR-0002: Organización del código

| Campo      | Valor                                                              |
|------------|--------------------------------------------------------------------|
| Estado     | Aceptado                                                           |
| Fecha      | 14/11/2026                                                         |
| Requisitos | REQ-01 a REQ-08, REQ-14, REQ-17                                    |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), decisiones D-03 y D-04  |

## Contexto

La aplicación casi no tiene lógica de negocio: lee datos de SWAPI, los pagina, los filtra y los protege con login. Las cuatro entidades (People, Films, Starships y Vehicles) se listan y se filtran exactamente igual; solo cambian sus datos de detalle y el campo por el que se buscan.

SWAPI es un servicio externo con un formato irregular, que puede cambiar sin aviso.

## Decisión

El código se organiza **por funcionalidad**, con **una única frontera hacia SWAPI**.

```
io.github.erickvaernet.starwars
├── auth/          login, emisión del token y usuarios
├── catalog/       núcleo común: páginas, filtros y la interfaz hacia la fuente de datos
│   ├── people/    controlador y datos propios de People
│   ├── films/
│   ├── starships/
│   └── vehicles/
├── swapi/         cliente de SWAPI y traducción de sus respuestas
└── shared/        errores, configuración y documentación de la API
```

- **Núcleo común:** la paginación y los filtros se escriben una sola vez en `catalog`. Cada entidad agrega solo lo que la distingue: su ruta, su nombre en SWAPI, el campo por el que se busca y sus datos de detalle.
- **Frontera:** `catalog` define la interfaz de la fuente de datos y `swapi` la implementa.

Reglas de dependencia:

1. `catalog` no conoce a `swapi`.
2. Los datos con el formato de SWAPI no salen de `swapi`.
3. `auth` y `catalog` no dependen entre sí.
4. Nada depende de un controlador.

## Alternativas consideradas

| Alternativa                              | A favor                                                    | En contra                                                                  |
|------------------------------------------|------------------------------------------------------------|----------------------------------------------------------------------------|
| Capas técnicas (`controller`, `service`, `repository`) | La más conocida                              | Mezcla funcionalidades sin relación; un cambio toca todas las capas         |
| **Por funcionalidad, con una frontera**  | Cada carpeta se entiende sola; SWAPI queda encerrada       | Hay que cuidar que las funcionalidades no se mezclen                        |
| Hexagonal completa                       | Aísla el negocio de toda la infraestructura                | Mucha estructura y traducciones para una aplicación casi sin negocio        |
| DDD                                      | Ordena dominios complejos                                  | No hay dominio que modelar: los datos son de solo lectura y vienen de afuera |
| Cuatro implementaciones separadas        | Cada entidad se lee sola                                   | Cuatro copias de la misma lógica                                           |
| Un único controlador genérico            | Mínimo código                                              | La documentación de la API pierde los datos propios de cada entidad         |

## Consecuencias

- Un arreglo en la paginación o en los filtros se hace en un solo lugar y vale para las cuatro entidades.
- Si SWAPI cambia su formato, el cambio queda dentro de `swapi`.
- De la arquitectura hexagonal se toma solo la idea que aporta acá: depender de una interfaz propia y no del servicio externo. La base de datos y la seguridad usan Spring de forma directa.
- Las reglas de dependencia se comprueban con tests (ver [ADR-0009](0009-testing-strategy.md)) y están descriptas para el día a día en [`ARCHITECTURE.md`](../ARCHITECTURE.md).
- Si la aplicación sumara lógica de negocio propia, habría que reconsiderar una separación más estricta.

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **14/11/2026.** La frontera hacia SWAPI que define este ADR es el patrón conocido como **capa anticorrupción** (*anti-corruption layer*): un único punto que traduce el modelo de un sistema externo al propio, para que su formato y sus cambios no se propaguen al resto del código. El texto original no usaba ese nombre. Las decisiones sobre a qué se traducen los datos de SWAPI están en el [ADR-0012](0012-response-data-shape.md).
- **15/11/2026.** Precisiones que aparecieron al implementar el primer detalle (ticket #10). La decisión no cambia: el código sigue organizado por funcionalidad, con una única frontera hacia SWAPI.
  - **El detalle tiene dos mitades, y cada una va en un lado de la frontera.** Este ADR dice que los datos de detalle son lo propio de cada entidad, en su carpeta de `catalog`, y también que los datos con el formato de SWAPI no salen de `swapi`. Con el primer detalle hubo que decidir dónde va el código que une las dos cosas. Quedó así: los datos de detalle de la entidad, con los nombres de esta API, están en su carpeta de `catalog`; su traducción, que es la que conoce los nombres de los campos de SWAPI (`birth_year`, `hair_color`) y sus direcciones, está en `swapi`.
  - **Consecuencia: sumar una entidad incluye una pieza en `swapi`.** El texto original decía que cada entidad agrega solo su carpeta en `catalog`. Desde este ticket agrega además su traducción en `swapi` y una línea en la tabla que dice con qué traducción se lee cada entidad. La paginación, los filtros, la validación, la caché y los errores siguen sin tocarse. Los pasos están en [`ARCHITECTURE.md`](../ARCHITECTURE.md#cómo-se-agrega-una-entidad).
  - **Alternativas descartadas.**
    - *Que la carpeta de la entidad diga cómo se arma su detalle a partir de un registro genérico de la fuente* (pedirle "el texto del campo `birth_year`"). No suma nada en `swapi`, pero los nombres de SWAPI quedan escritos en `catalog`: si SWAPI cambia un nombre, el cambio ya no queda dentro de `swapi`.
    - *Anotar los datos de detalle con los nombres de SWAPI.* El mismo problema, y además mezcla en una sola clase el contrato propio con el ajeno.
    - *Traducir de forma automática,* pasando `birth_year` a `birthYear` y `films` a `filmIds` con una regla. No hay nada que escribir por entidad, pero la regla tiene excepciones (`MGLT`, `characters`, los campos que no se devuelven) y la documentación de la API dejaría de mostrar los campos de cada entidad.
  - **Qué conoce a qué.** `swapi` ya conocía el modelo de `catalog`, porque implementa su interfaz. Ahora conoce también los datos de detalle de cada entidad, que arma al traducir. La dependencia va en un solo sentido: ninguna carpeta de `catalog` conoce a `swapi`. Las cuatro reglas de este ADR no cambian y los tests de arquitectura siguen pasando sin modificarse.
  - **El núcleo no conoce los datos de detalle de ninguna entidad.** Le alcanza con saber cómo se ve cualquier elemento en un listado: su identificador y su nombre. Con eso el filtro por ID es un solo código para las cuatro entidades. En el detalle, el controlador de cada entidad dice qué tipo de datos espera.
  - **Cómo elige `swapi` la traducción.** Con una tabla fija, del nombre de la entidad en SWAPI a su formato, escrita en el cliente de SWAPI. La alternativa era que cada traducción fuera un componente de Spring y el cliente los recibiera en una lista: es más abierta, pero es más maquinaria que una tabla de cuatro líneas, y una entidad que se sume tiene que escribir su traducción de todos modos.
