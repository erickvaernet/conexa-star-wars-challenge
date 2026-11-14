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
