# ADR-0003: Cliente de SWAPI

| Campo      | Valor                                                        |
|------------|--------------------------------------------------------------|
| Estado     | Aceptado                                                     |
| Fecha      | 14/11/2026                                                   |
| Requisitos | REQ-01 a REQ-04, REQ-16                                      |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), decisión D-05     |

## Contexto

Todos los datos de Star Wars salen de SWAPI por HTTP. SWAPI es un servicio externo y gratuito: puede estar lento o caído, y demora cada vez más a quien le hace muchos pedidos.

Los pedidos a SWAPI son bloqueantes: el código espera la respuesta. Java 21 trae hilos virtuales, que permiten atender muchos pedidos bloqueantes a la vez sin el costo de un hilo del sistema por cada uno.

## Decisión

Los pedidos a SWAPI se hacen con **`RestClient`**, el cliente HTTP síncrono de Spring, detrás de la interfaz que define `catalog` (ver [ADR-0002](0002-code-organization.md)).

- La dirección de SWAPI y los tiempos máximos de conexión y de lectura son configurables.
- Se activan los **hilos virtuales** de Java 21.
- Si SWAPI falla o tarda de más, la aplicación responde con un error claro y no se queda esperando.

## Alternativas consideradas

| Alternativa      | A favor                                                    | En contra                                                    |
|------------------|------------------------------------------------------------|--------------------------------------------------------------|
| **`RestClient`** | Viene con Spring; síncrono, simple de leer y de probar     | —                                                            |
| `WebClient`      | Pedidos no bloqueantes                                     | Obliga a sumar el modelo reactivo solo para un cliente        |
| OpenFeign        | Cliente declarativo, con menos código                      | Una dependencia más para algo que Spring ya resuelve          |

## Consecuencias

- Como la dirección de SWAPI es configurable, los tests la apuntan a un servidor simulado sin cambiar código (ver [ADR-0009](0009-testing-strategy.md)).
- Los tiempos máximos de espera tienen que contemplar la demora progresiva de SWAPI; la caché reduce cuántos pedidos le llegan (ver [ADR-0011](0011-swapi-response-cache.md)).
- Los reintentos y el corte automático ante fallas repetidas quedan para el ciclo 2 (REQ-22), con su propio ADR.
