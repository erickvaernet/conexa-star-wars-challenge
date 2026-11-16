# ADR-0022: Colección de pedidos lista para usar: un archivo de Postman, comparado con la documentación de la API

| Campo      | Valor                                                                       |
|------------|-----------------------------------------------------------------------------|
| Estado     | Aceptado                                                                    |
| Fecha      | 15/11/2026                                                                  |
| Requisitos | REQ-28, REQ-12                                                              |
| Origen     | [ADR-0008](0008-api-documentation.md): la colección complementa a la documentación interactiva |

## Contexto

El PRD pide que quien corrige pueda "importar un archivo y recorrer todo el flujo (iniciar sesión, listar y filtrar) sin armar cada pedido a mano" (REQ-28). Complementa a la documentación interactiva (REQ-12), no la reemplaza.

Qué hay hecho:

- **Swagger UI ya permite probar cada pedido** desde el navegador, con el login y el token ([ADR-0008](0008-api-documentation.md)). Lo que no da es un recorrido: el orden, los valores que conviene probar y el token cargado sin copiarlo.
- **El README tiene ejemplos con `curl`,** que hay que copiar de a uno.
- **La documentación de la API se genera desde el código** y declara diez pedidos: el login, los cuatro listados, los cuatro detalles y el alta de usuarios. Un test tiene la lista cerrada de esos pedidos y de sus nombres.

Qué condiciona la decisión:

- **Quien corrige usa la herramienta que ya tiene.** Las más comunes para probar una API, Postman, Insomnia, Bruno y Hoppscotch, importan todas una colección de Postman en su formato 2.1; no hay otro formato que lean las cuatro.
- **Un archivo escrito a mano queda viejo:** si se suma o se renombra un pedido y nadie toca la colección, sigue importándose bien y falla recién cuando alguien la usa.
- **Los tests nunca llaman a la SWAPI real** ([ADR-0009](0009-testing-strategy.md)), y SWAPI corta a los 100 pedidos cada 15 minutos.
- Las únicas credenciales que pueden estar en el repositorio son las de los usuarios de prueba, que el README ya publica.

## Decisión

El repositorio trae **una colección de Postman, en formato 2.1, en un solo archivo:** `docs/api/star-wars-api.postman_collection.json`. **Un test la compara con la documentación de la API,** en los dos sentidos.

| Tema                    | Decisión                                                                                                         |
|-------------------------|------------------------------------------------------------------------------------------------------------------|
| Formato                 | Colección de Postman 2.1, en JSON. Un solo archivo, sin archivo de entorno aparte                                 |
| Variables               | Dos, de la colección: `baseUrl`, con `http://localhost:8080` por defecto, y `token`, vacía                        |
| Autenticación           | Una sola vez, a nivel de la colección: Bearer con `{{token}}`. Los pedidos de login y el que muestra el `401` se declaran sin autenticación |
| El token                | Cada login tiene un guion de una línea que guarda `accessToken` en la variable `token`: los pedidos siguientes lo usan sin copiar nada |
| Contenido, en orden     | Login como `demo`; por cada una de las cuatro entidades, el listado con `page` y `size`, el filtro por ID, el filtro por nombre, el listado con `expanded=true` y el detalle; los errores que conviene mostrar (`401` sin token, `403` del alta con `demo`, `400` con `errors` y `404`); login como `admin`; y el alta de un usuario |
| Credenciales            | Solo las de prueba publicadas, `demo` y `admin`. Ningún otro secreto: la variable `token` va vacía                |
| Que no quede vieja      | Un test de aceptación lee el archivo y la documentación de la API de la aplicación levantada: cada pedido documentado tiene al menos un pedido en la colección, y cada pedido de la colección existe en la documentación, con parámetros que ese pedido declara |
| Contra la SWAPI real    | No se ejecuta en la verificación automática. Se ejecuta una vez a mano, completa y en orden, contra la aplicación levantada, al sumarla, y el resultado queda escrito en el pull request |
| README                  | Una sección corta, enlazada desde el inicio rápido: cómo importarla y qué cambiar para usarla contra otra dirección |

## Alternativas consideradas

Formato:

| Alternativa                                         | A favor                                                                 | En contra                                                                                          |
|-----------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **Una colección de Postman 2.1**                    | La importan las cuatro herramientas más usadas; un archivo; admite variables, autenticación común y un guion que guarda el token | Es un JSON largo, incómodo de leer y de revisar en un cambio; el guion del token usa la forma de Postman, y no todas las herramientas lo ejecutan al importar |
| Un archivo `.http`                                  | Texto plano, se lee y se revisa bien; no pide instalar nada a quien usa un editor que lo entienda | Lo abren solo algunos editores, y cada uno resuelve de otra forma cómo pasar el token de un pedido al siguiente |
| Una colección por herramienta                       | Cada una en su formato propio, con todo lo que esa herramienta ofrece    | Tres o cuatro archivos con lo mismo, que hay que mantener iguales                                   |
| Generar la colección desde la documentación en cada compilación | Nunca queda vieja                                           | Sale una lista de pedidos sin orden, sin el guion del token y sin los valores elegidos, que es justo lo que la documentación interactiva no da; suma una herramienta a la compilación |
| No traer archivo: importar `/v3/api-docs` desde la herramienta | Nada que mantener                                            | Lo mismo que la anterior, y además pide tener la aplicación levantada antes de importar              |

Variables:

| Alternativa                                         | A favor                                                                 | En contra                                                                                          |
|-----------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **Variables de la colección**                       | Un solo archivo para importar; funciona apenas se importa               | Para apuntar a otra dirección hay que editar la variable en la colección                            |
| Un archivo de entorno aparte                        | Es la forma habitual de cambiar entre local y público                   | Dos archivos, dos importaciones y un paso más (elegir el entorno) antes del primer pedido            |

Cómo se evita que quede vieja:

| Alternativa                                         | A favor                                                                 | En contra                                                                                          |
|-----------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **Un test que la compara con la documentación**     | Detecta un pedido nuevo que falta, uno que ya no existe y un parámetro renombrado; corre con los demás tests, sin SWAPI | No ejecuta los pedidos: no detecta un cuerpo con un valor equivocado ni una respuesta distinta de la esperada |
| Ejecutar la colección dentro de los tests, contra la SWAPI simulada | Comprobaría también las respuestas                       | Pide un intérprete de la colección en los tests y una grabación de SWAPI por cada pedido: es un proyecto aparte |
| Ejecutar la colección en la verificación automática, contra la SWAPI real | La prueba más completa                              | La verificación fallaría cuando SWAPI esté lenta o caída, y gastaría su cupo: es lo que el [ADR-0014](0014-continuous-verification.md) evita |
| Solo revisarla a mano                               | Nada que programar                                                      | Queda vieja con el primer pedido que se sume                                                        |

## Consecuencias

- Quien corrige importa un archivo y recorre la API en orden, con el token ya cargado después del login. No necesita leer el contrato para armar el primer pedido.
- **Sumar, quitar o renombrar un pedido de la API obliga a tocar la colección:** el test falla hasta que coincidan. Es el mismo trato que ya tiene la lista cerrada de pedidos de la documentación ([ADR-0008](0008-api-documentation.md)).
- **El test no ejecuta la colección.** Que cada pedido responda lo esperado contra la SWAPI real se comprobó una vez, a mano, al sumarla. Un cambio posterior en un cuerpo o en un valor de ejemplo no lo detecta ningún test.
- **El orden importa, porque hay una sola variable `token`.** El pedido que muestra el `403` va antes del login de `admin`: usa el token de `demo`. Quien ejecute un pedido suelto después de iniciar sesión como `admin` lo hace como administrador.
- **El alta crea siempre el mismo usuario.** La primera vez responde `201`, y las siguientes, `409`, que es la respuesta documentada para un nombre que ya existe. No se usa un nombre al azar: cada herramienta lo genera de una forma distinta.
- **El guion que guarda el token está escrito para Postman.** En una herramienta que no lo ejecute al importar, el token se copia a mano en la variable `token`, una vez. El README lo dice. Qué herramientas se probaron de verdad queda anotado al sumar la colección.
- Los pedidos de la colección llegan a la SWAPI real a través de la aplicación: recorrerla entera son unos veinte pedidos a SWAPI la primera vez, y ninguno mientras dure la caché ([ADR-0011](0011-swapi-response-cache.md)).
- La colección no tiene pedidos de login con credenciales equivocadas: recorrerla no acerca a nadie al límite de intentos de login ([ADR-0023](0023-login-attempt-limit.md)).
- Para usarla contra el despliegue público alcanza con cambiar `baseUrl`. Las credenciales de prueba son las mismas ([ADR-0015](0015-public-deployment.md)).
- `docs/` suma una carpeta, `api/`, con un archivo que no es un documento. Queda ahí porque es material para quien lee la documentación, no código de la aplicación.
- Si la API creciera mucho, mantener la colección a mano dejaría de valer la pena y convendría generarla y completarla: se registraría en un ADR nuevo.

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **15/11/2026.** Al sumar la colección ([#49](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/49)):
  - **Con qué herramientas se probó.**
    - **Newman 6.2.3,** el ejecutor de colecciones de Postman para la terminal: ejecutó la colección completa, en orden, contra la aplicación levantada con `docker compose`. **Ejecuta el guion:** los pedidos que siguen a cada login salieron con el token, y el alta, con el de `admin`.
    - **Bruno 4.3.0:** se pasó el archivo por el conversor con el que la aplicación importa una colección de Postman, sin abrir su interfaz y sin enviar ningún pedido. Conserva las siete carpetas y los 27 pedidos en orden, las dos variables, la autenticación común con `{{token}}` y los tres pedidos sin autenticación, y no informa ningún problema. **Traduce el guion** a su propia forma, como guion posterior a la respuesta: `bru.setCollectionVar("token", res.getBody().accessToken);`. Que ese guion traducido carga el token al ejecutarlo en Bruno no se probó.
    - **La aplicación de Postman, Insomnia y Hoppscotch no se probaron:** ni la importación ni el guion.
  - **Lo que respondió.** Los 27 pedidos respondieron lo esperado: `200` los dos logins y los veinte pedidos de las entidades; `401`, `403`, `400` con `errors` y `404` los cuatro errores; y `201` el alta. Un segundo pedido de alta, hecho aparte, respondió `409`.
  - **SWAPI recibió 17 pedidos, no "unos veinte".** Cuatro por entidad: el listado, el elemento del filtro por ID, la búsqueda por nombre y el listado con `expanded=true`. El detalle pide el mismo elemento que el filtro por ID y sale de la caché. El pedido 17 es el del `404`. El `401`, el `403` y el `400` no llegan a SWAPI. Se contaron con el registro de pedidos del cliente HTTP de Java, encendido solo para esa ejecución con una variable de entorno que no está en el repositorio.
  - **Recorrerla de nuevo sí le hace un pedido a SWAPI,** y no "ninguno": el del `404`, porque un "no existe" no se guarda ([ADR-0011](0011-swapi-response-cache.md)). Sale de esa regla; la segunda vuelta no se ejecutó.
  - **El archivo se escribió a mano,** no se exportó de una herramienta: no tiene identificador de colección, de cuenta ni de espacio de trabajo. El formato solo exige el nombre y la declaración del formato.
  - **Los pedidos van en siete carpetas numeradas,** una por paso del recorrido. Una herramienta ejecuta lo que hay dentro de una carpeta antes de seguir con la siguiente, y así las lee el test. Ninguna carpeta declara autenticación: la alternativa, declarar "sin autenticación" en la carpeta de los logins, deja de valer si se le suma otro pedido.
  - **La dirección de cada pedido está escrita dos veces,** entera y en partes (servidor, tramos de la ruta y parámetros), que es como la exporta Postman. El formato acepta también la dirección como un solo texto, que es más corto, pero las herramientas que importan se prueban contra archivos exportados. Como a mano las dos formas pueden quedar distintas, un test comprueba que digan lo mismo.
  - **Los nombres y las descripciones están en inglés,** como la documentación de la API que muestra Swagger UI: es lo que se ve al lado al probar.
  - **Qué valores usa.** Los mismos de los ejemplos del README: el personaje 1, la película 1, la nave 9 y el vehículo 4; las búsquedas `sky`, `hope`, `wing` y `speeder`. El `400` es `size=0` en el listado de personajes, y el `404`, el detalle del personaje `9999`. El `403` y el alta envían el mismo cuerpo: lo único que cambia es quién pide.
  - **Tests que no salen de un criterio,** en la misma clase: todo `POST` lleva cuerpo, las dos formas de cada dirección coinciden, y cada pedido y cada carpeta tiene un nombre propio.
  - **El test de CA-28.5 busca el guion por su texto:** `pm.collectionVariables.set("token", pm.response.json().accessToken)`. Otra forma de guardar el token, aunque funcione, lo hace fallar: es una sola línea y se prefirió que el test diga cuál.
- **16/11/2026.** Lo que encontró la revisión final del ciclo 3, y qué cambió en el test ([pull request #56](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/56)). El archivo de la colección no cambió: la revisión lo ejecutó con Newman contra la aplicación levantada y los 27 pedidos respondieron lo que anuncian.
  - **El test dejaba pasar diez roturas hechas a propósito,** de veintidós. Tres tocaban lo que el test decía comprobar: la línea que guarda el token comentada, una segunda línea que lo vaciaba después de guardarlo, y un parámetro apagado (`"disabled": true`), que la herramienta no envía aunque la dirección entera lo muestre.
  - **Corrige la nota anterior sobre CA-28.5,** que decía que el test "busca el guion por su texto". Buscarlo adentro del texto era lo que aceptaba las dos primeras roturas. Ahora compara las líneas del guion: tiene que ser esa línea y ninguna otra. Y un test nuevo comprueba que los únicos guiones de la colección son los de los dos logins: ni otro pedido, ni una carpeta, ni la colección entera tienen uno.
  - **Cada dirección tiene solo las partes que el test compara:** la dirección entera, el servidor, los tramos y los parámetros, y cada parámetro, su nombre y su valor. Un protocolo, un puerto o un parámetro apagado cambian lo que se envía sin cambiar la dirección entera, y ahora hacen fallar el test.
  - **Los 27 pedidos están fijados.** Borrar los pedidos del `400` y del `404` pasaba en verde; el número del README no lo vigilaba nada.
  - **Con esos cambios, de las veintidós roturas el test detecta diecisiete.** Se volvieron a correr las diez que pasaban: cinco ya no pasan.
  - **Lo que el test sigue sin ver,** con las otras cinco, y es el límite que este ADR ya declaraba: no ejecuta los pedidos. No detecta un cuerpo que la API rechazaría, un valor que cambia el código de la respuesta, ni un nombre que anuncia otro código. Tampoco que el pedido sin autenticación sea el que muestra el `401`.
  - **La colección no tiene comprobaciones,** y `npx newman run` no falla si la API responde otra cosa: muestra el código de cada respuesta, que se compara a ojo con el del nombre del pedido. El README ahora lo dice. Sumar una comprobación por pedido es lo que propuso la revisión, y no se hizo en este ciclo: cambia el archivo publicado y lo que ejecuta quien lo importa.
  - **Contra el despliegue público el alta responde `201` una sola vez,** la primera que alguien la recorra, porque esa base no se borra. El README lo dice.
- **16/11/2026.** En su revisión antes de entregar ([registro de uso de herramientas de IA](../process/AI-USAGE.md#revisión-del-autor-antes-de-entregar)), el autor importó la colección en la aplicación de Postman y la ejecutó contra el servicio público, sin el alta de usuario, para no gastar el único `201` de esa base. Hasta ese momento se había ejecutado solo con Newman. Evaluó además sumar dos entornos de Postman, uno por dirección, y mantuvo la decisión: un solo archivo. El README dice ahora qué valor poner en `baseUrl` para una máquina propia y para el servicio público.
