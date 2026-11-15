# ADR-0020: Tolerancia a fallas de SWAPI: un reintento acotado, sin corte automático

| Campo      | Valor                                                                       |
|------------|-----------------------------------------------------------------------------|
| Estado     | Aceptado                                                                    |
| Fecha      | 15/11/2026                                                                  |
| Requisitos | REQ-22, REQ-16                                                              |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), sección 4 (decisiones postergadas), y [ADR-0003](0003-swapi-client.md) |

## Contexto

El REQ-22 pide "una respuesta clara y a tiempo si SWAPI está lenta o caída, en vez de que la aplicación se cuelgue", y que el login siga funcionando aunque SWAPI no responda.

**Lo que pide esa historia ya lo cumple el MVP,** y está cubierto por tests de aceptación:

| Qué pide                         | Cómo lo cumple el MVP                                                                 | Criterio          |
|----------------------------------|---------------------------------------------------------------------------------------|-------------------|
| Que no se cuelgue                | Tiempos máximos de conexión (3 s) y de lectura (10 s) ([ADR-0003](0003-swapi-client.md)) | CA-16.3           |
| Una respuesta clara              | `502` si SWAPI falla y `504` si no responde a tiempo, sin detalles internos ([ADR-0007](0007-error-format.md)) | CA-16.2, CA-16.5 |
| Que el login no dependa de SWAPI | El login solo usa la base de datos                                                    | CA-16.4           |
| Que una falla no quede pegada    | Los errores no se guardan en la caché ([ADR-0011](0011-swapi-response-cache.md))      | CA-32.4           |

Lo que el RFC dejó para este ciclo fue decidir si se suman "reintentos y corte automático", con Resilience4j como candidato.

Qué condiciona la decisión:

- **SWAPI limita el uso:** 100 pedidos cada 15 minutos, y demora cada vez más a quien pide mucho. Un reintento es un pedido más. Reintentar a quien ya dijo "demasiados pedidos" (`429`) empeora el problema.
- **Los pedidos a SWAPI son todos lecturas:** repetir uno no cambia nada del otro lado.
- Hay una sola instancia, un solo servicio externo, y una caché que ya evita la mayor parte de los pedidos.
- Dos criterios de aceptación ya aprobados usan un error `500` de SWAPI y cuentan cuántos pedidos recibe: CA-16.2 y CA-32.4.

Qué hay disponible hoy, verificado el 15/11/2026:

- **Spring Framework 7 trae reintentos propios,** en su módulo base: un componente al que se le dice cuántas veces reintentar, cuánto esperar y ante qué errores. El proyecto ya lo tiene, porque usa Spring Framework 7.0.10; no hace falta ninguna dependencia. No trae corte automático.
- **Resilience4j** publicó en marzo de 2026 la versión 2.4.0, la primera con soporte para Spring Boot 4. Funciona, pero es reciente: su documentación oficial todavía no cubre Spring Boot 4, tiene varios problemas abiertos de esa integración, no hubo otra versión desde entonces y no se encontró confirmación de que se haya probado con Spring Boot 4.1.
- **Spring Retry,** el proyecto que se usaba antes para esto, está archivado desde julio de 2026: lo reemplaza lo que trae Spring Framework 7.
- El cliente HTTP de Java ya reintenta por su cuenta algunas fallas al abrir la conexión.

## Decisión

Se suma **un solo reintento** de los pedidos a SWAPI, con lo que trae Spring Framework 7, y **no se suma corte automático**.

| Tema                    | Decisión                                                                                                            |
|-------------------------|---------------------------------------------------------------------------------------------------------------------|
| Cuántas veces           | **Una** vez más, después de esperar 200 milisegundos. Las dos cosas son configurables; con cero reintentos, se apaga |
| Qué se reintenta        | Lo que suele ser pasajero y falla rápido: SWAPI responde `502`, `503` o `504`, o la conexión se corta o se rechaza   |
| Qué **no** se reintenta | Un `429` ni ningún otro `4xx`; un `500`; un tiempo máximo vencido, de conexión o de lectura; una respuesta que llegó y no se puede leer |
| Dónde                   | Dentro del cliente de SWAPI, en `swapi`. El resto de la aplicación no se entera                                       |
| Si el reintento falla   | La misma respuesta que hoy: `502` o `504`, según la última falla                                                      |

## Alternativas consideradas

Qué hacer:

| Alternativa                                    | A favor                                                                                     | En contra                                                                                          |
|------------------------------------------------|---------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| No sumar nada                                  | La historia ya está cumplida; cero código y cero riesgo                                     | Una falla pasajera de SWAPI, de las que se arreglan solas en un instante, llega a quien consume como un `502` |
| **Un reintento, con lo que trae Spring Framework 7** | Absorbe la falla pasajera más común; sin dependencias nuevas; pocas líneas en un solo lugar | Un pedido más a SWAPI cuando algo falla; el peor caso tarda más                                 |
| Un reintento escrito a mano                    | Sin depender de una pieza nueva de Spring                                                   | Reescribe, y obliga a probar, algo que ya viene hecho                                              |
| Resilience4j: reintentos y corte automático    | Es la librería más conocida para esto; trae además el corte                                 | Dependencias nuevas y una integración con Spring Boot 4 todavía inmadura; el corte no se justifica acá (ver abajo) |
| Responder con el dato vencido de la caché cuando SWAPI falla | Es la tolerancia que más se notaría: la API seguiría respondiendo con SWAPI caída | Cambia qué promete la caché ([ADR-0011](0011-swapi-response-cache.md): un dato vence a la hora) y pide guardar lo vencido aparte; es otro diseño, más grande que este requisito |

Corte automático (*circuit breaker*): deja de llamar a un servicio que viene fallando, por un rato, y responde el error en el momento.

| Alternativa              | A favor                                                                      | En contra                                                                                                   |
|--------------------------|------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| **No sumarlo**           | Nada que configurar, probar ni explicar                                      | Con SWAPI caída, cada pedido que no está en la caché espera hasta su tiempo máximo antes de fallar           |
| Sumarlo                  | Con SWAPI caída, la API fallaría rápido y dejaría de gastarle pedidos         | Sirve para proteger a un sistema con mucho tráfico o muchas dependencias; acá hay una instancia, una dependencia, tiempos máximos que ya acotan la espera, hilos virtuales que hacen barata esa espera y una caché adelante. Suma un estado más ("cortado"), difícil de probar, en el que la API puede seguir rechazando pedidos cuando SWAPI ya volvió |

Qué se reintenta:

| Alternativa                                         | A favor                                                                 | En contra                                                                                          |
|-----------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **`502`, `503`, `504` y conexión cortada**          | Son las fallas que dicen "ahora no", no "este pedido está mal"; fallan rápido, así que reintentar casi no alarga la espera | Deja afuera fallas que a veces también son pasajeras       |
| Sumar los tiempos máximos vencidos                  | Cubre una demora puntual                                                | Duplica la peor espera (de 10 a más de 20 segundos), contra "una respuesta a tiempo"; SWAPI demora a propósito a quien pide mucho, y volver a pedir lo agrava |
| Sumar el `500`                                      | A veces también es pasajero                                             | Suele ser un error que se repite igual; y cambia dos criterios ya aprobados (CA-16.2 y CA-32.4), que usan un `500` y cuentan los pedidos |
| Sumar el `429`, esperando lo que SWAPI indique      | Es lo que el estándar prevé para ese código                             | La espera correcta puede ser de minutos, imposible dentro de un pedido; cada intento gasta más del cupo |

## Consecuencias

- Una falla pasajera de SWAPI deja de verse desde afuera: el segundo intento responde y quien consume recibe su `200`.
- Lo que promete la API ante una falla no cambia: sigue siendo `502` o `504`, con los mismos textos. Los criterios CA-16.x y CA-32.x siguen valiendo como están escritos, y sus tests no se tocan.
- **El peor caso tarda más.** Hoy un pedido espera a SWAPI 10 segundos como mucho. Con el reintento, si el primer intento falla recién al final y el segundo se demora, puede llegar a unos 20. Es poco frecuente, porque lo que se reintenta suele fallar enseguida, y queda escrito en la especificación.
- Con SWAPI caída del todo, cada pedido le cuesta a SWAPI dos intentos en vez de uno. Es un múltiplo fijo y chico, y la caché sigue adelante. Si SWAPI responde `429`, no se reintenta nunca.
- El reintento va en un solo lugar. El cliente HTTP de Java puede repetir por su cuenta el intento de abrir una conexión; no se le suma otra capa encima de la caché ni en el núcleo.
- No hay corte automático. Si la aplicación pasara a tener mucho tráfico, varias instancias o más servicios externos, habría que reconsiderarlo, con un ADR nuevo. Para ese momento Resilience4j probablemente tenga una integración madura con Spring Boot 4.
- Se usa una pieza de Spring Framework que existe desde su versión 7.0. Es chica y está en el módulo base; si cambiara, el uso está en una sola clase.
- Los reintentos se pueden apagar con una variable, sin tocar el código.
- **Es el último ticket del ciclo y el primero que se corta.** Su historia ya está cumplida por el MVP. Si no entra en el ciclo 2, el reintento pasa al ciclo 3 y el cambio se anota en el PRD; esta decisión no cambia.
- Responder con el dato vencido cuando SWAPI falla queda anotado como la mejora que más valor daría después de esta.
