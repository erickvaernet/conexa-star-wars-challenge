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

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **15/11/2026.** Precisiones que aparecieron al escribir el cliente (ticket #8):
  - **Variables.** La dirección entra por `SWAPI_BASE_URL` y los tiempos máximos por `SWAPI_CONNECT_TIMEOUT` (3 segundos por defecto) y `SWAPI_READ_TIMEOUT` (10 segundos por defecto). Los tiempos se escriben como un número con su unidad: `500ms`, `3s`. La alternativa era un número con la unidad en el nombre, como `JWT_TTL_MINUTES`; se descartó porque los tests necesitan bajar el tiempo de lectura a un segundo o menos, y un único formato con unidad sirve para los dos usos. Un valor vacío, cero o negativo impide arrancar, y el mensaje dice qué variable corregir.
  - **Qué responde cada falla.** Si se vence el tiempo de conexión o el de lectura, `504`. Todo lo demás es `502`: una respuesta con error (incluido el `429` de SWAPI por exceso de pedidos), una respuesta que no se puede interpretar, una conexión rechazada o cortada. El cliente convierte cualquier falla en uno de esos dos errores, así el resto del código no necesita saber cómo falla un cliente HTTP.
  - **Sin dependencias nuevas.** `RestClient` se arma en `swapi` sobre el cliente HTTP que trae Java, con los dos tiempos máximos. La alternativa era sumar el módulo de Spring Boot que lo entrega configurado; se descartó porque acá hay un solo cliente y configurarlo a mano son pocas líneas a la vista.
  - **Hilos virtuales.** Se activan con `spring.threads.virtual.enabled=true`: cada pedido corre en un hilo virtual, y esperar a SWAPI no retiene un hilo del sistema.
  - **Barra final.** Si `SWAPI_BASE_URL` termina en `/`, esa barra se descarta, para no armar direcciones con dos barras seguidas.
- **15/11/2026.** Precisión que apareció al corregir lo que encontró la revisión final del MVP: **una respuesta que empieza a tiempo y se corta a mitad también es una demora (`504`).**
  - **Qué pasaba.** Si SWAPI enviaba el comienzo de la respuesta y después se detenía, la espera terminaba al cumplirse el tiempo máximo de lectura, pero el error era `502`, el de una respuesta que no se puede leer.
  - **Por qué se decide midiendo.** El corte a mitad del cuerpo no trae ninguna marca de tiempo vencido: el cliente HTTP lo informa igual que una respuesta que no es JSON. Lo que distingue los dos casos es cuánto llevaba esperando el pedido. Si falló la lectura de la respuesta y ya pasó el tiempo máximo de lectura, es una demora.
  - **Qué no cambia.** Una respuesta que no se puede leer y llega a tiempo sigue siendo `502`. Un código de error de SWAPI, como `500` o `429`, también: llega con el comienzo de la respuesta, así que nunca es un corte. Y las demoras que el cliente HTTP sí informa (no conectar o no empezar a responder a tiempo) siguen como estaban.
  - **Alternativa descartada.** Buscar en el error original un texto que diga que se venció el tiempo. Depende de cómo escribe sus mensajes la librería, que no es parte de lo que promete.
