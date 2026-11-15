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

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **15/11/2026.** Precisiones que aparecieron al implementarlo (ticket #38):
  - **Lo que trae Spring Framework alcanza, y se probó antes de diseñar.** Con un programa suelto contra la versión que usa el proyecto, la 7.0.10: se arma una política (cuántos reintentos, la espera y una condición sobre la falla) y un `RetryTemplate`, y el pedido se envuelve con su método `invoke`. Si todos los intentos fallan, relanza la última falla tal cual, sin envolverla; si la falla no cumple la condición, la relanza sin esperar; con cero reintentos hace un solo intento, así que apagarlos es un valor más y no otro camino en el código. La espera es fija. Si el hilo se interrumpe durante la espera, no reintenta. Es menos código que un bucle propio y no hubo que escribir ni probar la espera.
  - **Cómo quedó en el código.** `swapi/SwapiCatalogSource` arma el `RetryTemplate` en su constructor. Los cinco pedidos pasan por dos métodos, el de las listas y las búsquedas y el de un elemento, y cada uno envuelve su intento entero. Nada fuera de esa clase cambió: ni la caché, que sigue por fuera y guarda lo que llega en el reintento como cualquier respuesta, ni el núcleo.
  - **Cada intento incluye la traducción de la falla,** y la condición del reintento mira el error ya traducido y su causa: reintenta un `502` propio cuya causa es un `502`, `503` o `504` de SWAPI, o una falla de conexión.
    - **Por qué.** El tiempo que distingue una respuesta cortada a mitad ([ADR-0003](0003-swapi-client.md)) queda medido por intento sin hacer nada. "Un tiempo vencido nunca se reintenta" queda garantizado por el tipo del error, también para el que se decide midiendo. Y el texto del error traducido ya nombra el pedido sin el texto de búsqueda del cliente, así que sirve para el registro.
    - **Alternativa descartada.** Reintentar sobre la falla sin traducir y traducir solo la última. Obliga a sacar del reintento, por un costado, cuándo empezó el último intento; la garantía sobre los tiempos vencidos pasa a depender de que la lista de fallas sin traducir esté completa; y esa falla no sirve para el registro. Son más líneas para el mismo comportamiento.
    - **Qué cuesta.** La condición depende de que el error traducido conserve su causa. Ya lo hacía, y un test lo comprueba.
  - **Qué informa el cliente HTTP ante cada falla,** medido contra la SWAPI simulada:

    | Falla de SWAPI                                   | Qué informa el cliente                                  | ¿Se reintenta? |
    |--------------------------------------------------|---------------------------------------------------------|----------------|
    | `502`, `503`, `504`                              | Una respuesta con ese código                            | Sí             |
    | `500`, `429` y cualquier otro código de error    | Una respuesta con ese código                            | No             |
    | `404` al pedir un elemento                       | No es una falla: el elemento no existe                  | No             |
    | Conexión rechazada o cortada                     | Un error de conexión                                    | Sí             |
    | No conecta, o no empieza a responder, a tiempo   | Un error de conexión causado por un tiempo vencido      | No             |
    | Empieza a responder y se corta a mitad           | Una respuesta que no se pudo leer, pasado el tiempo máximo | No          |
    | Responde bien algo que no es lo esperado, o nada | Una respuesta que no se pudo leer                       | No             |

  - **El cliente HTTP de Java repite por su cuenta un solo caso,** y eso se suma a este reintento. Contra la SWAPI simulada de los tests no repitió ninguna de las fallas de la tabla: en todas, SWAPI recibió un pedido por intento. Por eso "exactamente dos pedidos" (CA-22.2) se puede comprobar. Pero en la prueba con la aplicación levantada, contra un servidor que cierra la conexión sin enviar nada, ese cliente repitió el pedido una vez, en el momento y sin avisar. Es lo que el contexto de este ADR anota, y sirve para una conexión en espera que el servidor ya había cerrado. La consecuencia: si SWAPI corta así una sola vez, el pedido responde bien sin pasar por este reintento ni por su espera; y si corta siempre, SWAPI recibe dos pedidos por intento, cuatro en total con un reintento, y no dos. No se desactiva: el cliente no ofrece ese ajuste al armarlo, y el caso que cubre es útil. Ante un `502`, `503` o `504`, que son respuestas, ese cliente no repite nada.
  - **Si en vez de una respuesta HTTP llegan datos que no lo son, se reintenta.** El cliente HTTP lo informa igual que una conexión cortada: no llegó ninguna respuesta válida y la conexión se cerró. "Una respuesta que llegó y no se puede leer", que no se reintenta, es la que sí es HTTP y trae un cuerpo que no es el esperado. Distinguir los dos casos pediría mirar el texto del error, que es lo que el ADR-0003 descartó hacer. Tiene su test.
  - **`SWAPI_RETRY_DELAY` acepta `0s` y no tiene tope.** Con cero, el reintento sale en el momento. La alternativa era rechazar el cero, para validar todos los tiempos de SWAPI igual; se descartó porque en los tiempos máximos cero significa "esperar para siempre" y acá tiene un significado razonable. Tampoco tienen tope los tiempos máximos; la alternativa era fijar uno, y se descartó por lo mismo: quien configura un valor absurdo lo ve en el primer pedido que falla. Vacío o negativo impide arrancar.
  - **`SWAPI_MAX_RETRIES` va de 0 a 3,** como dice la especificación. Fuera de ese rango, vacío o con algo que no es un número, la aplicación no arranca.
  - **El registro.** Cada reintento deja una línea de advertencia con el número de reintento y el texto del error traducido, que nombra el pedido. No lleva el error del cliente HTTP ni su traza: si el último intento también falla, salen con ese error, como antes.
  - **Los tests.** Las fallas se arman en cada test sobre la SWAPI simulada, con una secuencia de respuestas: la primera falla y las siguientes son la grabación. Cuatro de los ocho criterios (CA-22.4, CA-22.5, CA-22.6 y CA-22.8) ya pasaban antes del código, porque describen lo que no se reintenta o el comportamiento del MVP: quedaron como red. Los de CA-22.1 a CA-22.7 corren sobre la misma aplicación que los de REQ-16, para no levantar otra, y por eso vacían la caché también al terminar. Ningún test anterior cambió de resultado; el único de aceptación que ahora pasa por un reintento es el de CA-16.4, que tarda 200 milisegundos más.
