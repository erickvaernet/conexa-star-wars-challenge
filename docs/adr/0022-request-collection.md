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
