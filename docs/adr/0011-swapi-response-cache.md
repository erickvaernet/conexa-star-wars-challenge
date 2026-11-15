# ADR-0011: Caché de las respuestas de SWAPI

| Campo      | Valor                                                        |
|------------|--------------------------------------------------------------|
| Estado     | Aceptado                                                     |
| Fecha      | 14/11/2026                                                   |
| Requisitos | REQ-32, REQ-16                                               |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), decisión D-15     |

## Contexto

En el PRD original, "Respuestas rápidas aunque SWAPI sea lenta" (REQ-32) era un extra del ciclo 3, marcado como "Podría".

Al preparar el RFC se releyó la documentación de SWAPI y se midieron sus tiempos el 14/11/2026:

- **Demora progresiva.** Dentro de una ventana de 15 minutos, cada pedido tarda 0,1 s más que el anterior a partir del quinto.

  | Pedido  | Tiempo de respuesta |
  |---------|---------------------|
  | 1 a 3   | 0,3 s               |
  | 4       | 0,9 s               |
  | 10      | 1,5 s               |

  A ese ritmo, el pedido número 50 tarda unos 5 segundos.
- **Tope.** 100 pedidos por ventana de 15 minutos, según las cabeceras de la respuesta, y 10.000 por día según la documentación.

Sin guardar las respuestas, quien pruebe la aplicación un rato desde Swagger la vería cada vez más lenta y podría llegar al corte. Eso afecta justo a quien corrige. Por eso **REQ-32 pasó de "Podría" (ciclo 3) a "Debe" (ciclo 1)**; el cambio está registrado en el historial del [PRD](../requirements/PRD.md#historial-de-cambios).

## Decisión

Las respuestas de SWAPI se guardan en una **caché en memoria con vencimiento**, desde el MVP.

- El vencimiento y el tamaño máximo son configurables. El vencimiento por defecto es de una hora.
- Se aplica en el único punto de entrada a SWAPI, así que cubre los listados, las búsquedas y el detalle.

## Alternativas consideradas

| Alternativa                          | A favor                                                         | En contra                                                             |
|--------------------------------------|-----------------------------------------------------------------|-----------------------------------------------------------------------|
| Sin caché                            | Nada que mantener                                               | Respuestas cada vez más lentas y riesgo de llegar al corte de SWAPI    |
| **Caché en memoria con vencimiento** | Los datos de Star Wars casi no cambian; Spring lo resuelve con poco código | Cada instancia tiene su propia copia                        |
| Caché compartida (Redis)             | Sirve con varias instancias                                     | Un servicio más para levantar; no hace falta con una sola instancia    |

## Consecuencias

- Un pedido repetido no llega a SWAPI: responde rápido y no consume el cupo.
- La paginación en memoria se apoya en la caché: recorrer las páginas de Films o de una búsqueda genera un solo pedido a SWAPI (ver [ADR-0004](0004-pagination-and-filters.md)).
- Un dato que cambie en SWAPI puede tardar hasta una hora en verse. Es aceptable porque los datos de Star Wars casi no cambian.
- La caché vive en la memoria de la aplicación: se vacía al reiniciar y cada instancia tiene la suya. Con más de una instancia haría falta una caché compartida, como Redis.
- El primer pedido de cada dato sigue dependiendo de la velocidad de SWAPI.

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **14/11/2026.** Al revisar la especificación técnica se precisó qué se guarda: **solo las respuestas correctas de SWAPI**. Un "no existe" (su 404) o un error no se guardan.
  - Se evaluó guardar también el "no existe", para no gastar pedidos a SWAPI en identificadores inexistentes. Se descartó: casi nadie vuelve a pedir el mismo identificador inexistente, así que el ahorro es mínimo, y a cambio la caché se llenaría de entradas que nadie vuelve a leer y que desplazan datos útiles.
  - Un error nunca se guarda: si se guardara, una caída breve de SWAPI se seguiría viendo hasta que venza la entrada.
  - Consecuencia: repetir el pedido de un identificador inexistente vuelve a llamar a SWAPI. La protección contra el abuso es el límite de pedidos por usuario (REQ-31), previsto para el ciclo 3.
  - Una búsqueda sin resultados sí se guarda, porque SWAPI la responde como correcta, con una lista vacía.
- **14/11/2026.** Se revisó si convenía guardar solo los listados y dejar afuera los recursos específicos (`/people/1`), para que la caché no se llene. Se mantuvo la decisión de guardar todo.
  - Los recursos específicos son la parte más acotada: existen 163 (82 personajes, 6 películas, 36 naves y 39 vehículos), cada respuesta real pesa entre 0,7 y 2,6 KB, y un identificador inexistente no se guarda.
  - Son además los pedidos que más se repiten, y los filtros por ID se resuelven con ese mismo pedido a SWAPI.
  - Lo que no tiene límite son las búsquedas por nombre y los números de página: cada texto y cada número es una entrada distinta, y una búsqueda sin resultados se guarda.
  - El tamaño máximo impide que la caché crezca sin límite, pero una avalancha de búsquedas inútiles podría desplazar datos útiles. Con un uso normal no se llega a ese punto, así que la mejora queda como requisito "Podría" (REQ-35): guardar por separado los recursos específicos y los listados, cada grupo con su tope.
- **15/11/2026.** Precisiones que aparecieron al implementar la caché (ticket #9). La decisión no cambia: sigue siendo una caché en memoria con vencimiento.
  - **Librería: Caffeine.** Este ADR decía que "Spring lo resuelve con poco código", sin nombrar con qué. La caché que Spring trae por defecto es un mapa que no vence ni tiene tope, así que hacía falta una librería. Se compararon estas alternativas y se eligió la más simple que cumple la especificación:
    - *El mapa por defecto de Spring.* No suma dependencias, pero guarda todo para siempre: no cumple ni el vencimiento ni el tamaño máximo.
    - *Caffeine* (elegida). Trae hechas las tres cosas que hacen falta: el vencimiento, el tamaño máximo y un reloj que los tests pueden reemplazar. Es una sola dependencia, y Spring Boot gestiona su versión.
    - *Escribirla a mano.* Sin dependencias, pero es código propio para varios hilos a la vez (un mapa ordenado por uso, la hora de guardado de cada entrada y un candado), que hay que probar y mantener para llegar a lo mismo.
    - *Guava.* Ofrece algo equivalente, pero su propia documentación recomienda Caffeine, y es una librería grande para usar una sola pieza.
    - *Ehcache.* Está pensada para guardar en varios niveles, como memoria y disco, y pide más configuración de la que acá hace falta.
  - **Se usa de forma directa, sin las anotaciones de caché de Spring.** La alternativa era marcar el método que le pide a SWAPI con `@Cacheable` y dejar Caffeine detrás: para un listado es una anotación y nada más. Se descartó por tres motivos. El principal: recorrer las páginas de Films o de una búsqueda tiene que generar un solo pedido a SWAPI, y eso exige buscar primero si ya está guardada la lista completa (ver el último punto de esta nota); con la anotación no se puede expresar, y habría que rehacer la caché en ese ticket. Además, con el uso directo la clave y la regla "solo se guarda lo correcto" quedan escritas en el código, a la vista, y hace falta una dependencia en vez de dos. Esto no cambia la decisión: lo que cambia respecto del texto original es que no la resuelve Spring, sino una librería usada directamente.
  - **Dónde está.** Una clase de `swapi` envuelve al cliente de SWAPI y tiene su misma interfaz. Busca la respuesta guardada; si no está, le pide al cliente y guarda lo que respondió. `catalog` recibe esa clase y no sabe que hay una caché; el cliente tampoco cambió.
  - **Qué es "el mismo pedido".** En un listado, la misma entidad, la misma página y el mismo tamaño. Si cambia cualquiera de los tres, es otro pedido y otra entrada. Cada tipo de pedido que se sume (un elemento por su identificador, una búsqueda por nombre) tendrá su propia clave, de modo que sus respuestas no puedan confundirse con las de un listado.
  - **Solo se guarda lo correcto.** La respuesta se guarda en la línea siguiente a la que la recibe. Si SWAPI falla o no responde a tiempo, el error sale antes de llegar a esa línea y no queda nada guardado: el pedido siguiente vuelve a SWAPI.
  - **Cómo vence una entrada.** Vence una hora después de **guardarse**, se lea las veces que se lea. La alternativa era contar desde la última lectura; se descartó porque un dato muy pedido no vencería nunca y no se volvería a buscar en SWAPI. No hay una tarea que recorra la caché borrando lo vencido: Caffeine lo quita cuando la caché se usa, y una lectura nunca devuelve una entrada vencida.
  - **Qué se descarta al llegar al tope.** No es simplemente la entrada más vieja. Caffeine descarta la que estima menos probable que se vuelva a pedir, mirando cuán seguido y cuán recientemente se usó cada una. Eso protege un poco a los datos más pedidos frente a una tanda de pedidos que nadie repite, aunque no reemplaza al REQ-35. El descarte ocurre enseguida después de guardar, no en el mismo paso: por un instante puede haber alguna entrada de más.
  - **Por qué 500.** Es una convención con margen, no una medición. La cuenta que la respalda: existen 163 recursos (82 personajes, 6 películas, 36 naves y 39 vehículos) y, con el tamaño de página por defecto, 18 listados (9 páginas de personajes, 4 de naves, 4 de vehículos y la lista de películas). Tener todo eso guardado son unas 180 entradas. 500 es casi tres veces esa cantidad: deja lugar para otros tamaños de página y para las búsquedas, que suman una entrada por cada combinación distinta de página, tamaño y texto. En memoria es poco: una respuesta real de SWAPI pesa entre 0,7 y 2,6 KB si es un recurso y 6,6 KB si es la página más grande posible, de 100 elementos, y lo que se guarda es la respuesta ya traducida, que ocupa menos. Aun llena de las respuestas más grandes, la caché ocupa pocos megabytes. El tope no está para cuidar la memoria en el uso normal, sino para que la caché no pueda crecer sin límite, porque esas combinaciones no se terminan. Si el uso real mostrara que conviene otro número, se cambia con una variable, sin tocar el código.
  - **Variables.** El vencimiento entra por `SWAPI_CACHE_TTL` (`1h` por defecto), con el mismo formato que los tiempos de espera de SWAPI: un número con su unidad, como `30m`. El tamaño máximo entra por `SWAPI_CACHE_MAX_SIZE` (`500` por defecto). Un vencimiento vacío, cero o negativo, o un tamaño menor que 1, impiden arrancar, y el mensaje dice qué variable corregir. Caffeine acepta un tamaño de 0, que equivale a no guardar nada; acá se rechaza, porque apagar la caché no es una opción de esta aplicación.
  - **Dos pedidos iguales que llegan a la vez con la caché vacía van los dos a SWAPI.** Caffeine puede evitarlo con una operación que busca y carga en un solo paso, pero la carga se hace con un candado de la caché tomado. Acá la carga es el pedido a SWAPI, que puede tardar hasta 10 segundos, y cada pedido corre en un hilo virtual ([ADR-0003](0003-swapi-client.md)): en Java 21, un hilo virtual que espera dentro de un candado de ese tipo deja ocupado su hilo del sistema durante toda la espera, que es justo lo que los hilos virtuales evitan. Por eso buscar y guardar son dos pasos separados, y el pedido a SWAPI se hace sin ningún candado tomado. El costo es un pedido de más a SWAPI en un caso poco frecuente.
  - **El reloj en los tests.** La caché mide el tiempo con el reloj del sistema. Los tests del vencimiento le dan otro, que solo avanza cuando el test se lo pide: prueban que pasó una hora sin esperarla.
  - **Queda por resolver cuando exista la lista completa.** Siempre se le envían a SWAPI la página y el tamaño ([ADR-0004](0004-pagination-and-filters.md)), así que las páginas 1, 2 y 3 de Films son tres pedidos distintos, aunque SWAPI responda a los tres con la misma lista completa. Con una clave que incluye la página serían tres entradas y tres pedidos. Para que sea uno solo, como dice la consecuencia de este ADR, la caché tiene que guardar una lista completa con una clave sin página ni tamaño, y fijarse si ya la tiene antes de pedir. Hoy SWAPI se consulta solo para el listado de People, que llega paginado, así que todavía no hay ninguna lista completa que guardar: se resuelve con la búsqueda por nombre (ticket #11) y con Films (ticket #12).
- **15/11/2026.** Precisiones que aparecieron al guardar los elementos pedidos por su identificador (ticket #10):
  - **Clave.** La entidad y el identificador, en un registro propio, distinto del de los listados: un elemento y una página no pueden confundirse aunque estén en la misma caché.
  - **Qué se guarda.** El elemento ya traducido, con todos sus datos. No la respuesta de SWAPI.
  - **El filtro por ID y el detalle comparten la entrada.** Los dos le hacen a la fuente el mismo pedido, con la misma clave. Pedir `/people/1` y después `/people?id=1` es un solo pedido a SWAPI, como anticipaba la nota del 14/11/2026.
  - **Un "no existe" no se guarda.** En el código es una sola línea: se guarda solo si la fuente devolvió un elemento. Repetir el pedido de un identificador inexistente vuelve a SWAPI cada vez (CA-32.5).
  - **Qué es "no existe".** Un `404` de SWAPI al pedido de un elemento. Cualquier otro código de error sigue siendo una falla, que tampoco se guarda.
- **15/11/2026.** Precisiones que aparecieron al guardar las búsquedas por nombre (ticket #11). Resuelven lo que la nota del ticket #9 dejó pendiente para "cuando exista la lista completa":
  - **Dos claves, según cómo respondió SWAPI.** Una respuesta que trae todos los elementos sirve para cualquier página, así que se guarda con una clave sin página ni tamaño: la entidad y, si es una búsqueda, el texto. Una respuesta que trae una sola página se guarda como hasta ahora, con la entidad, el texto si lo hay, la página y el tamaño. Un listado sin filtro y una búsqueda de la misma entidad nunca comparten entrada: en el listado el texto está ausente.
  - **Cómo se usa.** Igual para un listado y para una búsqueda: primero se busca la respuesta completa; si no está, la de esa página; si tampoco, se le pide a SWAPI y se guarda con la clave que corresponde a la forma en que respondió. No se pregunta qué entidad es ni si es una búsqueda. La alternativa era fijar por regla que "las búsquedas llegan completas"; se descartó por lo mismo que en el [ADR-0004](0004-pagination-and-filters.md): si SWAPI empezara a paginarlas, se rompería.
  - **Recorrer las páginas de una búsqueda es un solo pedido a SWAPI.** También pedirla con otro tamaño de página. Los tests de aceptación lo comprueban contando los pedidos que recibe SWAPI.
  - **Lo mismo va a valer para Films** (CA-32.2). Su listado llega completo, así que se va a guardar una vez con la clave sin página, sin tocar la caché. Quedó cubierto con un test unitario, con una fuente que responde la lista completa.
  - **Cómo se normaliza el texto.** No hay un paso propio de la caché: el texto ya llega sin los espacios de los extremos y en minúsculas, porque así lo deja su validación ([ADR-0013](0013-parameter-limits-and-invalid-values.md)). `name=sky`, `name=SKY` y `name=%20sky%20` comparten la entrada, y además son el mismo pedido a SWAPI. Era el pendiente anotado para este ticket.
  - **Una búsqueda sin coincidencias se guarda,** como ya decía la nota del 14/11/2026. La respuesta real lo confirma: es un `200` con la lista vacía, distinto del `404` de un identificador que no existe. En el código no tiene ninguna línea propia: es una lista completa de cero elementos.
  - **ID y nombre juntos no suman ninguna entrada.** Se resuelven con el pedido del elemento, que ya tiene la suya.
  - **Menos entradas de las que estimaba la nota del ticket #9.** Decía que las búsquedas suman una entrada por cada combinación de página, tamaño y texto. Como SWAPI las responde completas, hoy suman una por texto. El riesgo de una avalancha de textos distintos sigue siendo el del REQ-35.
  - **El costo.** Un listado que SWAPI pagina hace ahora dos consultas a la caché en vez de una antes de ir a SWAPI. Las dos son en memoria.
