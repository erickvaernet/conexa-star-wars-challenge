# Conexa · Back-end Java Challenge — Star Wars API

[![CI](https://github.com/erickvaernet/conexa-star-wars-challenge/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/erickvaernet/conexa-star-wars-challenge/actions/workflows/ci.yml?query=branch%3Amain)
[![Cobertura de líneas](https://img.shields.io/badge/cobertura-99%25-brightgreen)](https://github.com/erickvaernet/conexa-star-wars-challenge/actions/workflows/ci.yml?query=branch%3Amain)

API REST en **Java 21** que se integra con [SWAPI](https://www.swapi.tech/documentation) para listar **People, Films, Starships y Vehicles** de forma paginada, con filtrado por ID y/o nombre y autenticación segura.

> **Estado:** los tres ciclos están cerrados: el 1 (MVP), el 2 (mejoras) y el 3 (extras), con la versión `v0.3.0` y 1321 tests. La aplicación está desplegada y se puede probar sin instalar nada: **<https://star-wars-api-7x3l.onrender.com/swagger-ui.html>**. La primera visita puede tardar un minuto y medio, porque el servicio gratuito la duerme cuando nadie la usa ([Despliegue público](#despliegue-público)).

## Alcance

- [x] Integración con la API de Star Wars (People, Films, Starships, Vehicles)
- [x] Listados paginados
- [x] Filtrado por ID y/o nombre
- [x] Login seguro con autenticación y autorización para acceder a los listados (usuarios en base de datos + seed inicial)
- [x] Documentación de la API con Swagger / OpenAPI
- [x] Pruebas unitarias y de integración
- [x] Entorno local dockerizado
- [x] Despliegue en un servidor gratuito

## Inicio rápido

La aplicación se puede probar de dos formas, con los mismos usuarios de prueba: en el servicio público, sin instalar nada, o levantándola en una máquina propia.

| Qué | En el servicio público | En una máquina propia |
|---|---|---|
| Documentación interactiva (Swagger UI) | <https://star-wars-api-7x3l.onrender.com/swagger-ui.html> | <http://localhost:8080/swagger-ui.html> |
| La misma documentación, en formato OpenAPI | <https://star-wars-api-7x3l.onrender.com/v3/api-docs> | <http://localhost:8080/v3/api-docs> |
| Control de estado | <https://star-wars-api-7x3l.onrender.com/actuator/health> | <http://localhost:8080/actuator/health> |
| Variable `baseUrl` de la [colección de pedidos](#colección-de-pedidos) | `https://star-wars-api-7x3l.onrender.com` | `http://localhost:8080`, que es como viene |

**Dos esperas que no son fallas:**

- **En el servicio público, la primera visita puede tardar un minuto y medio.** El plan gratuito de Render apaga la aplicación después de 15 minutos sin pedidos, y despertarla es arrancarla de nuevo con muy poco procesador: unos 70 segundos. La base de datos, en Neon, también se apaga, a los 5 minutos sin consultas, pero según sus páginas despierta en menos de un segundo: la espera es de la aplicación, no de la base. Mientras despierta, el navegador queda cargando; si corta, alcanza con volver a cargar. Después responde con normalidad, hasta que pasen otros 15 minutos sin uso ([Despliegue público](#despliegue-público)).
- **Cinco logins fallidos en quince minutos bloquean el login,** en los dos lados y también con la contraseña correcta, hasta que pasan quince minutos desde el primer fallo. Los fallos se cuentan por dirección de origen, no por usuario, y la respuesta, un `429`, dice cuántos segundos faltan ([Iniciar sesión](#iniciar-sesión)). Si pasa al probar, alcanza con esperar. En una máquina propia también alcanza con reiniciar la aplicación, porque la cuenta se guarda en memoria.

Lo que sigue es para levantarla en una máquina propia. Hace falta tener Docker andando. No hay que instalar Java ni configurar nada.

**1. Levantar la aplicación y su base de datos**

```bash
docker compose up --build
```

Cuando termina de arrancar, la aplicación responde en las direcciones de la columna "En una máquina propia" de la tabla de arriba.

**2. Iniciar sesión con un usuario de prueba**

`docker compose up` carga dos, uno de cada rol:

| Usuario | Contraseña | Rol | Qué puede hacer |
|---|---|---|---|
| `demo` | `demo1234` | `USER` | Usar los listados y los detalles |
| `admin` | `admin1234` | `ADMIN` | Lo mismo, y además [crear usuarios](#crear-usuarios) |

Son credenciales de prueba, pensadas para quien corrige: en la base de datos solo está su *hash* BCrypt. Con cinco logins fallidos seguidos el login se bloquea por hasta quince minutos, como dice más arriba.

**3. Probar**

Desde el navegador, con [Swagger UI](#probar-desde-swagger-ui); desde la terminal, con [`curl`](#probar-con-curl); o importando la [colección de pedidos](#colección-de-pedidos) en Postman o en una herramienta parecida, que trae el recorrido completo, en orden y con el token ya cargado. Están el login, la protección del acceso y las cuatro entidades, People, Films, Starships y Vehicles, cada una con su listado paginado, sus filtros por ID y por nombre y su detalle, con la caché de las respuestas de SWAPI. Y el alta de usuarios, que solo puede hacer un administrador. Todo se puede probar desde la documentación interactiva.

**4. Correr los tests**

Hace falta además un JDK 21 o posterior. Maven no se instala: se usa el *wrapper* (`./mvnw`).

| Para | Comando | Necesita |
|---|---|---|
| Tests unitarios y de arquitectura | `./mvnw test` | Ni red ni Docker |
| Todos los tests, incluidos los de aceptación | `./mvnw verify` | Docker |
| Comprobar la cobertura y su distintivo | `bash scripts/check-coverage.sh` | Haber corrido `./mvnw clean verify` |

Más detalle, en [Tests](#tests).

**Para apagar** y borrar los datos locales:

```bash
docker compose down -v
```

## Probar desde Swagger UI

No hace falta ninguna otra herramienta: alcanza con el navegador.

1. Abrir Swagger UI: en el servicio público, <https://star-wars-api-7x3l.onrender.com/swagger-ui.html>; en una máquina propia, <http://localhost:8080/swagger-ui.html>. El primer grupo es **Authentication**, con el login.
2. Desplegar `POST /api/v1/auth/login`, pulsar **Try it out** y después **Execute**. El cuerpo ya trae al usuario de prueba, así que no hay que escribir nada.
3. Copiar el valor de `accessToken` de la respuesta, sin las comillas.
4. Pulsar **Authorize**, arriba de la lista de pedidos, pegar el token en `Value`, confirmar con **Authorize** y cerrar el diálogo con **Close**. Va solo el token: la palabra `Bearer` la agrega la página.
5. Probar cualquier pedido. Por ejemplo, `GET /api/v1/people` con `name` igual a `sky`, o `GET /api/v1/films/{id}` con `id` igual a `1`.

- Los pedidos con candado son los que exigen el token: los cuatro listados, los cuatro detalles y el alta de usuarios. El login no lo lleva.
- El alta de usuarios, `POST /api/v1/users`, en el grupo **Users**, exige además el rol de administrador. Para probarla hay que iniciar sesión como `admin`: en el paso 2, cambiar el cuerpo por `{"username": "admin", "password": "admin1234"}`. Con el token de `demo` responde `403`.
- El token vence a los 60 minutos. Desde ese momento los pedidos responden `401`: se repite el login y se carga el token nuevo. El login no envía el token cargado, así que se puede repetir aunque ese token ya no sirva.
- **Logout**, en el mismo diálogo de **Authorize**, quita el token de la página.
- Cada pedido muestra sus parámetros, su respuesta y sus errores. Los ejemplos son respuestas reales de la API: cada listado trae dos, la página de resúmenes y la misma página con los datos completos, y cada error, el suyo.

Las secciones que siguen muestran los mismos pedidos con `curl`.

## Probar con `curl`

### Iniciar sesión

Con el usuario de prueba del [inicio rápido](#inicio-rápido):

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "demo", "password": "demo1234"}'
```

La respuesta trae el token, que vence a los 60 minutos:

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Un usuario o una contraseña incorrectos responden `401`, y un pedido al que le falta alguno de los dos, `400`. También responde `400` un usuario de más de 50 caracteres o con caracteres de control, y una contraseña de más de 72. Los errores usan el formato Problem Details.

Un `400` por un campo inválido dice cuál y qué regla tiene que cumplir, en la lista `errors`:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "", "password": "demo1234"}'
```

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/v1/auth/login",
  "errors": [
    {
      "field": "username",
      "message": "must not be blank, and must have at most 50 characters and no control characters"
    }
  ]
}
```

- Si los dos campos son inválidos, `errors` trae los dos, ordenados por nombre: primero `password` y después `username`.
- Cada campo aparece una sola vez y su texto es fijo: dice la regla completa, falle la parte que falle, y nunca repite lo que se envió.
- Un cuerpo que no es JSON también responde `400`, sin `errors`: `Failed to read request`. No hay un campo al que culpar.
- Un cuerpo que no se envía como JSON (otro `Content-Type`) responde `415`: `The content type of the request is not supported.`

El porqué de esta forma está en el [ADR-0016](docs/adr/0016-validation-error-detail.md).

**Límite de intentos.** Los logins fallidos se cuentan por dirección de origen. Al quinto dentro de quince minutos, todo login desde esa dirección responde `429`, también con las credenciales correctas, hasta que pasan quince minutos desde el primer fallo:

```
HTTP/1.1 429 Too Many Requests
Content-Type: application/problem+json
Retry-After: 847
```

```json
{
  "type": "about:blank",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Too many failed login attempts. Try again later.",
  "instance": "/api/v1/auth/login"
}
```

- `Retry-After` dice cuántos segundos faltan para que el login vuelva a comprobar las credenciales. Seguir probando no alarga la espera.
- Cuenta solo un login que responde `401`. Un `400` no cuenta, y un login correcto no suma ni borra los fallos anteriores.
- **Con cinco logins en curso a la vez desde una dirección, el siguiente también responde `429`,** aunque ninguno haya fallado. Cada login ocupa su lugar antes de comprobar la contraseña, para que de muchos pedidos simultáneos solo cinco lleguen a compararla. No es un bloqueo: en ese caso `Retry-After` vale `1`, y al reintentar responde `200`.
- Los cinco primeros fallos responden el `401` de siempre, que no dice cuántos intentos quedan.
- El límite es solo del login: un token que ya se obtuvo sigue sirviendo.
- La cuenta vive en la memoria de la aplicación y se pierde al reiniciarla.
- La cantidad de fallos y la ventana se cambian, y el límite se apaga, con dos variables de la [configuración](#configuración).

Qué frena y qué no, y por qué se cuenta por dirección y no por usuario, está en el [ADR-0023](docs/adr/0023-login-attempt-limit.md).

### Usar el token

Todos los pedidos necesitan el token, salvo el login, la documentación y el control de estado. Se envía en la cabecera `Authorization`:

```bash
curl http://localhost:8080/api/v1/people \
  -H "Authorization: Bearer eyJ..."
```

Sin token, o con uno mal formado, con la firma alterada o vencido, la respuesta es `401`, siempre la misma.

Con un token válido cuyo rol no alcanza para el pedido, la respuesta es `403`. Hoy pasa en un solo caso: el [alta de usuarios](#crear-usuarios) con el token de un usuario común.

## Colección de pedidos

El recorrido completo de la API en un archivo, para no armar cada pedido a mano: [`docs/api/star-wars-api.postman_collection.json`](docs/api/star-wars-api.postman_collection.json). Es una colección de Postman, en su formato 2.1, con 27 pedidos en este orden:

1. El login de `demo`.
2. Por cada entidad: el listado con `page` y `size`, el filtro por ID, el filtro por nombre, el listado con `expanded=true` y el detalle.
3. Cuatro errores: `401` sin token, `403` del alta con el token de `demo`, `400` con la lista `errors` y `404`.
4. El login de `admin` y el alta de un usuario.

Cómo se usa:

- **Importarla.** En Postman, con **Import**, eligiendo el archivo. Es uno solo: no hay entornos que importar aparte. Insomnia, Bruno y Hoppscotch también importan colecciones de Postman, según su documentación. Se ejecutó con Newman, el ejecutor de Postman para la terminal, y el autor la importó y la probó en la aplicación de Postman. La importación en las otras tres no se probó.
- **Recorrerla.** Con la aplicación levantada, se ejecutan los pedidos de arriba hacia abajo, de a uno o todos juntos. El nombre de cada pedido dice qué muestra y qué código responde.
- **El token se carga solo.** Cada login tiene un guion de una línea que guarda el `accessToken` de la respuesta en la variable `token` de la colección, y los demás pedidos la envían. No hay que copiar nada.
- **Si la herramienta no ejecuta ese guion,** los pedidos que siguen al login responden `401`. En ese caso se copia a mano el `accessToken` de la respuesta del login en la variable `token` de la colección, y se repite después del login de `admin`.
- **A dónde van los pedidos lo dice la variable `baseUrl` de la colección.** Es lo único que hay que completar, y las credenciales de prueba son las mismas en los dos lados.

  | Para probar | Valor de `baseUrl` |
  |---|---|
  | Una máquina propia | `http://localhost:8080`, que es como viene: no hay que tocar nada |
  | El [servicio público](#despliegue-público) | `https://star-wars-api-7x3l.onrender.com` |

  En Postman se cambia así: clic en la colección **Star Wars API**, pestaña **Variables**, se reemplaza el valor de `baseUrl` y se guarda. Sirve también para otro puerto.
- **Al cambiar de dirección hay que volver a ejecutar el login.** Un token del servicio público no sirve en una máquina propia, ni al revés: cada instalación firma los suyos con su propia clave.
- **El orden importa,** porque hay una sola variable `token`. El pedido que muestra el `403` usa el token de `demo`: va antes del login de `admin`.
- **El alta crea siempre el mismo usuario, `leia`.** La primera vez responde `201`, y desde la segunda, `409`, que es la respuesta para un nombre que ya existe. Vuelve a responder `201` después de `docker compose down -v`. Contra el despliegue público la base no se borra: el alta responde `201` solo la primera vez que alguien la recorre, y `409` a todos los demás.
- **Sin interfaz gráfica,** con Node instalado: `npx newman run docs/api/star-wars-api.postman_collection.json`. Para el servicio público se agrega `--env-var baseUrl=https://star-wars-api-7x3l.onrender.com`. La colección no tiene comprobaciones: Newman muestra el código de cada respuesta, pero no falla si la API responde otra cosa. El código esperado está en el nombre de cada pedido.

Recorrerla entera son 17 pedidos a SWAPI la primera vez. Después las respuestas salen de la caché, salvo la del `404`: un "no existe" no se guarda.

Un test compara la colección con la documentación de la API en los dos sentidos: cada pedido documentado tiene que estar en la colección, y cada pedido de la colección tiene que existir en la documentación, con parámetros que ese pedido declara. Sumar o renombrar un pedido obliga a actualizarla. El test no ejecuta los pedidos. El porqué está en el [ADR-0022](docs/adr/0022-request-collection.md).

## La API

### Pedidos

| Pedido | Qué devuelve | Exige token |
|---|---|---|
| `POST /api/v1/auth/login` | El token de la sesión | No |
| `GET /api/v1/people` | El listado de personajes | Sí |
| `GET /api/v1/people/{id}` | Todos los datos de un personaje | Sí |
| `GET /api/v1/films` | El listado de películas | Sí |
| `GET /api/v1/films/{id}` | Todos los datos de una película | Sí |
| `GET /api/v1/starships` | El listado de naves | Sí |
| `GET /api/v1/starships/{id}` | Todos los datos de una nave | Sí |
| `GET /api/v1/vehicles` | El listado de vehículos | Sí |
| `GET /api/v1/vehicles/{id}` | Todos los datos de un vehículo | Sí |
| `POST /api/v1/users` | El usuario creado | Sí, de un administrador |

El último es el único que escribe algo y el único reservado a un rol: está en [Crear usuarios](#crear-usuarios).

Las cuatro entidades se piden igual: los mismos parámetros, la misma forma de respuesta y los mismos errores. Los ejemplos que siguen usan People. Las reglas que valen para las cuatro están [más abajo](#reglas-que-valen-para-las-cuatro-entidades), y lo que cambia en cada una, en [Lo particular de cada entidad](#lo-particular-de-cada-entidad).

### Listar

El pedido de "Usar el token" devuelve la primera página de personajes, de a 10. Con `page` y `size` se elige otra página y otro tamaño:

```bash
curl "http://localhost:8080/api/v1/people?page=2&size=3" \
  -H "Authorization: Bearer eyJ..."
```

```json
{
  "content": [
    { "id": "4", "name": "Darth Vader" },
    { "id": "5", "name": "Leia Organa" },
    { "id": "6", "name": "Owen Lars" }
  ],
  "page": 2,
  "size": 3,
  "totalElements": 82,
  "totalPages": 28
}
```

Los cuatro listados aceptan los mismos parámetros:

| Parámetro | Por defecto | Valores aceptados |
|---|---|---|
| `page` | `1` | Un entero de 1 o más. La primera página es la 1 |
| `size` | `10` | Un entero de 1 a 100 |
| `id` | — | Solo dígitos: de 1 a 10, sin ceros a la izquierda |
| `name` | — | Un texto de 1 a 100 caracteres, sin contar los espacios del inicio y del final |
| `expanded` | `false` | `true` o `false`, en minúsculas. Con `true`, cada elemento trae [todos sus datos](#pedir-los-datos-completos-en-el-listado) |

### Filtrar por ID y ver el detalle

Con `id`, el listado devuelve solo ese personaje, con la misma forma que cualquier otra página:

```bash
curl "http://localhost:8080/api/v1/people?id=1" \
  -H "Authorization: Bearer eyJ..."
```

```json
{
  "content": [
    { "id": "1", "name": "Luke Skywalker" }
  ],
  "page": 1,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

El detalle devuelve todos los datos del personaje:

```bash
curl http://localhost:8080/api/v1/people/1 \
  -H "Authorization: Bearer eyJ..."
```

```json
{
  "id": "1",
  "name": "Luke Skywalker",
  "birthYear": "19BBY",
  "gender": "male",
  "height": "172",
  "mass": "77",
  "hairColor": "blond",
  "skinColor": "fair",
  "eyeColor": "blue",
  "filmIds": ["1", "2", "3", "6"],
  "starshipIds": ["12", "22"],
  "vehicleIds": ["14", "30"]
}
```

### Filtrar por nombre

Con `name`, el listado devuelve los personajes cuyo nombre contiene ese texto, en cualquier parte y sin distinguir mayúsculas de minúsculas. Se pagina igual que el listado completo:

```bash
curl "http://localhost:8080/api/v1/people?name=sky&page=1&size=2" \
  -H "Authorization: Bearer eyJ..."
```

```json
{
  "content": [
    { "id": "1", "name": "Luke Skywalker" },
    { "id": "11", "name": "Anakin Skywalker" }
  ],
  "page": 1,
  "size": 2,
  "totalElements": 3,
  "totalPages": 2
}
```

Con `id` y `name` juntos, devuelve ese personaje solo si además su nombre contiene el texto:

```bash
curl "http://localhost:8080/api/v1/people?id=1&name=luke" \
  -H "Authorization: Bearer eyJ..."
```

### Pedir los datos completos en el listado

Por defecto cada elemento de un listado es un resumen: `id` y `name`. Con `expanded=true`, cada elemento es el mismo objeto que devuelve su detalle, y no hace falta un pedido por cada uno:

```bash
curl "http://localhost:8080/api/v1/people?expanded=true&size=2" \
  -H "Authorization: Bearer eyJ..."
```

```json
{
  "content": [
    {
      "id": "1",
      "name": "Luke Skywalker",
      "birthYear": "19BBY",
      "gender": "male",
      "height": "172",
      "mass": "77",
      "hairColor": "blond",
      "skinColor": "fair",
      "eyeColor": "blue",
      "filmIds": ["1", "2", "3", "6"],
      "starshipIds": ["12", "22"],
      "vehicleIds": ["14", "30"]
    },
    {
      "id": "2",
      "name": "C-3PO",
      "birthYear": "112BBY",
      "gender": "n/a",
      "height": "167",
      "mass": "75",
      "hairColor": "n/a",
      "skinColor": "gold",
      "eyeColor": "yellow",
      "filmIds": ["1", "2", "3", "4", "5", "6"],
      "starshipIds": [],
      "vehicleIds": []
    }
  ],
  "page": 1,
  "size": 2,
  "totalElements": 82,
  "totalPages": 41
}
```

- La página es la misma: `expanded` no cambia qué elementos llegan, ni su orden, ni los totales. Solo cambia cuántos datos trae cada uno.
- Se combina con los filtros: `people?name=sky&expanded=true` devuelve las coincidencias con todos sus datos, y `people?id=1&expanded=true`, una página con el detalle de ese personaje.
- En Films, un elemento completo tiene `title` y no `name`, igual que su detalle.
- Con `expanded=false`, o sin el parámetro, el listado es el de siempre.

El porqué de esta forma, y las alternativas, están en el [ADR-0019](docs/adr/0019-expanded-listings.md).

### Reglas que valen para las cuatro entidades

**Páginas y parámetros**

- Una página posterior a la última devuelve `content` vacío, con los totales correctos.
- Un valor inválido responde `400` y el `detail` dice qué parámetro corregir: `Parameter 'size' must be an integer between 1 and 100.`
- Ese `400` trae además la lista `errors`, igual que el del login, con un elemento: `{ "field": "size", "message": "must be an integer between 1 and 100" }`. Si hay varios parámetros inválidos, nombra el primero, en el orden `page`, `size`, `id`, `name`, `expanded`. Vale también para el `id` de la ruta del detalle.
- Un ID con otro formato (`0`, `01`, `abc`) responde `400`, en el filtro y en el detalle, sin consultar a SWAPI: `Parameter 'id' must be a number of 1 to 10 digits, without leading zeros.`
- Un `name` vacío, solo de espacios o de más de 100 caracteres responde `400`, sin consultar a SWAPI: `Parameter 'name' must have 1 to 100 characters, not counting leading and trailing spaces.`
- Un `expanded` que no es exactamente `true` o `false` (`1`, `TRUE`, vacío) responde `400`, sin consultar a SWAPI: `Parameter 'expanded' must be true or false.`
- Un parámetro mal codificado, como `name=100%` (un `%` suelto se escribe `%25`), responde `400`: `The request has a parameter that is not correctly encoded.`
- Una dirección que el servidor no acepta, como una con `;` o con `//`, responde `400`, con token o sin él: `The request was rejected.`

**Filtros**

- Un ID que no existe responde distinto según cómo se pida: el filtro devuelve `200` con `content` vacío, como cualquier búsqueda sin resultados, y el detalle responde `404`.
- `name=SKY`, `name=sky` y `name=%20sky%20` son la misma búsqueda: no se distinguen mayúsculas y los espacios del inicio y del final se ignoran.
- Una búsqueda sin coincidencias responde `200` con `content` vacío y los totales en 0. Lo mismo si el elemento del `id` existe pero su nombre no contiene el texto.
- Los acentos cuentan: `name=padm` y `name=padmé` encuentran a Padmé Amidala, y `name=padme` no.
- El texto se busca tal cual: un punto es un punto y un paréntesis es un paréntesis. SWAPI los interpreta como parte de un patrón, así que la aplicación los escapa antes de consultarla. El porqué está en el [ADR-0004](docs/adr/0004-pagination-and-filters.md).

**Datos del detalle**

- Los valores son texto, como los entrega SWAPI, también las cantidades, porque muchos no se pueden leer como número sin interpretarlos: una altura puede ser `"unknown"`, y una nave trae `"342,953"`, `"4.0"`, `"3 years"` o `"n/a"`. No se convierten. La única excepción es el episodio de una película. El porqué está en el [ADR-0012](docs/adr/0012-response-data-shape.md).
- A los textos solo se les quitan los espacios del inicio y del final.
- Si SWAPI no envía un valor, llega como `null`.
- Las relaciones son listas de identificadores, que se pueden pedir en esta misma API. Las relaciones con planetas y especies no se devuelven, porque la API no expone esas entidades: el planeta de origen de un personaje, y los planetas y las especies de una película.

**SWAPI y la caché**

- Los datos salen de [SWAPI](https://www.swapi.tech/documentation). Si SWAPI responde con un error, el pedido responde `502`; si no responde a tiempo, `504`. El login no depende de SWAPI.
- Una falla pasajera de SWAPI no llega a quien consume: si SWAPI responde `502`, `503` o `504`, o rechaza o corta la conexión, la aplicación repite el pedido una vez, 200 milisegundos después, y recién si vuelve a fallar responde el error. No repite lo que no se arregla repitiendo o lo empeora: un `429` de SWAPI por exceso de pedidos, otro `4xx`, un `500`, un tiempo máximo vencido ni una respuesta que no se puede leer. Las dos cosas se [configuran](#configuración), y con cero reintentos se apaga. No hay corte automático. El porqué está en el [ADR-0020](docs/adr/0020-swapi-failure-tolerance.md).
- SWAPI puede recibir más pedidos de los que cuenta ese reintento: el cliente HTTP de Java repite por su cuenta, una vez, un pedido cuya conexión se cerró antes de recibir respuesta. Si SWAPI cierra así todas las conexiones, son cuatro pedidos y no dos, y dos con los reintentos apagados. Está medido en el mismo ADR.
- Las respuestas de SWAPI se guardan en memoria durante una hora: un pedido repetido responde sin volver a SWAPI, que demora cada vez más a quien le hace muchos pedidos. Un error no se guarda. El porqué y los detalles están en el [ADR-0011](docs/adr/0011-swapi-response-cache.md).
- El filtro por ID y el detalle del mismo elemento comparten la respuesta guardada: son un solo pedido a SWAPI. Un "no existe" no se guarda.
- SWAPI responde una búsqueda con todas las coincidencias juntas. La aplicación recorta la página pedida y guarda la respuesta completa: recorrer las páginas de una búsqueda es un solo pedido a SWAPI.
- Un listado con `expanded=true` es un solo pedido a SWAPI, que tiene ese mismo parámetro: no se pide cada elemento por separado. Su respuesta se guarda aparte de la del listado común, así que pedir el mismo listado de las dos formas son dos pedidos a SWAPI.
- Cada elemento que llega en un listado con `expanded=true` queda guardado también por su identificador: pedir después su detalle, o filtrar por su ID, no vuelve a SWAPI.
- Con `expanded=true`, si SWAPI entrega un solo elemento de la página con una relación que no se puede leer, toda la página responde `502`. El listado común no lee las relaciones y no corre ese riesgo.
- El orden de un listado es el de SWAPI, que no siempre es el de los identificadores, y los identificadores tienen huecos. Hay un ejemplo en cada entidad.

### Lo particular de cada entidad

| Entidad | Elementos | SWAPI pagina su listado | El filtro `name` busca en | Un ejemplo de búsqueda |
|---|---|---|---|---|
| People | 82 | Sí | El nombre | `people?name=sky`: tres personajes |
| Films | 6 | No: devuelve las seis juntas | El título | `films?name=hope`: "A New Hope" |
| Starships | 36 | Sí | El nombre | `starships?name=wing`: cinco naves, Y-wing, X-wing, A-wing, B-wing y V-wing |
| Vehicles | 39 | Sí | El nombre | `vehicles?name=speeder`: ocho vehículos, entre ellos X-34 landspeeder, Snowspeeder y Sith speeder |

Cuando SWAPI pagina un listado, como en People, Starships y Vehicles, cada página es un pedido a SWAPI y se guarda por separado.

#### Films

```bash
curl "http://localhost:8080/api/v1/films?page=2&size=2" \
  -H "Authorization: Bearer eyJ..."
```

```json
{
  "content": [
    { "id": "3", "name": "Return of the Jedi" },
    { "id": "4", "name": "The Phantom Menace" }
  ],
  "page": 2,
  "size": 2,
  "totalElements": 6,
  "totalPages": 3
}
```

En un listado, el `name` de una película es su título, y el filtro `name` busca en el título. Este pedido devuelve "A New Hope":

```bash
curl "http://localhost:8080/api/v1/films?name=hope" \
  -H "Authorization: Bearer eyJ..."
```

El detalle devuelve todos los datos de la película. En este ejemplo el texto de apertura está recortado:

```bash
curl http://localhost:8080/api/v1/films/1 \
  -H "Authorization: Bearer eyJ..."
```

```json
{
  "id": "1",
  "title": "A New Hope",
  "episodeId": 4,
  "openingCrawl": "It is a period of civil war.\r\nRebel spaceships, striking\r\nfrom a hidden base, have won...",
  "director": "George Lucas",
  "producer": "Gary Kurtz, Rick McCallum",
  "releaseDate": "1977-05-25",
  "characterIds": ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "12", "13", "14", "15", "16", "18", "19", "81"],
  "starshipIds": ["2", "3", "5", "9", "10", "11", "12", "13"],
  "vehicleIds": ["4", "6", "7", "8"]
}
```

- SWAPI no pagina las películas: devuelve las seis juntas, se le pida la página que se le pida. La aplicación recorta la página y guarda la lista completa, así que recorrer las páginas es un solo pedido a SWAPI.
- El orden es el de SWAPI, que es el de estreno y no el de los episodios: la película `4` es el episodio 1.
- `episodeId` es un número entero, el único valor de una película que no es texto. Si SWAPI no lo envía, llega como `null`; si envía algo que no es un número entero, la respuesta es `502`. El porqué está en el [ADR-0012](docs/adr/0012-response-data-shape.md).
- `openingCrawl` conserva sus saltos de línea (`\r\n`): a los textos solo se les quitan los espacios del inicio y del final.
- Los planetas y las especies de una película no se devuelven, porque la API no expone esas entidades.

#### Starships

El filtro `name` busca en el nombre de la nave. Este pedido devuelve las cinco naves cuyo nombre contiene "wing":

```bash
curl "http://localhost:8080/api/v1/starships?name=wing" \
  -H "Authorization: Bearer eyJ..."
```

El detalle devuelve todos los datos de la nave:

```bash
curl http://localhost:8080/api/v1/starships/9 \
  -H "Authorization: Bearer eyJ..."
```

```json
{
  "id": "9",
  "name": "Death Star",
  "model": "DS-1 Orbital Battle Station",
  "starshipClass": "Deep Space Mobile Battlestation",
  "manufacturer": "Imperial Department of Military Research, Sienar Fleet Systems",
  "costInCredits": "1000000000000",
  "length": "120000",
  "crew": "342,953",
  "passengers": "843,342",
  "maxAtmospheringSpeed": "n/a",
  "hyperdriveRating": "4.0",
  "mglt": "10",
  "cargoCapacity": "1000000000000",
  "consumables": "3 years",
  "filmIds": ["1"],
  "pilotIds": []
}
```

- El orden del listado es el de SWAPI, que no es el de los identificadores: después de la nave `11` viene la `10`. Los identificadores además tienen huecos: no hay nave `1`.
- Todos los valores son texto, también las cantidades: `"342,953"`, `"4.0"`, `"3 years"` o `"n/a"`. Se devuelven como los entrega SWAPI.
- `filmIds` y `pilotIds` son identificadores de películas y de personajes, que se pueden pedir en esta misma API. Una nave sin pilotos conocidos, como esta, trae `pilotIds` vacío; la nave `10`, el Millennium Falcon, trae `["13", "14", "25", "31"]`.

#### Vehicles

Sin parámetros, el listado devuelve la primera página, de 10 elementos:

```bash
curl http://localhost:8080/api/v1/vehicles \
  -H "Authorization: Bearer eyJ..."
```

```json
{
  "content": [
    { "id": "4", "name": "Sand Crawler" },
    { "id": "7", "name": "X-34 landspeeder" },
    { "id": "6", "name": "T-16 skyhopper" },
    { "id": "8", "name": "TIE/LN starfighter" },
    { "id": "14", "name": "Snowspeeder" },
    { "id": "18", "name": "AT-AT" },
    { "id": "16", "name": "TIE bomber" },
    { "id": "19", "name": "AT-ST" },
    { "id": "20", "name": "Storm IV Twin-Pod cloud car" },
    { "id": "24", "name": "Sail barge" }
  ],
  "page": 1,
  "size": 10,
  "totalElements": 39,
  "totalPages": 4
}
```

El filtro `name` busca en el nombre del vehículo. Este pedido devuelve los ocho vehículos cuyo nombre contiene "speeder":

```bash
curl "http://localhost:8080/api/v1/vehicles?name=speeder" \
  -H "Authorization: Bearer eyJ..."
```

El detalle devuelve todos los datos del vehículo:

```bash
curl http://localhost:8080/api/v1/vehicles/4 \
  -H "Authorization: Bearer eyJ..."
```

```json
{
  "id": "4",
  "name": "Sand Crawler",
  "model": "Digger Crawler",
  "vehicleClass": "wheeled",
  "manufacturer": "Corellia Mining Corporation",
  "costInCredits": "150000",
  "length": "36.8",
  "crew": "46",
  "passengers": "30",
  "maxAtmospheringSpeed": "30",
  "cargoCapacity": "50000",
  "consumables": "2 months",
  "filmIds": ["1", "5"],
  "pilotIds": []
}
```

- El orden del listado es el de SWAPI, que no es el de los identificadores: después del vehículo `7` viene el `6`. Los identificadores además tienen huecos: el primer vehículo es el `4`.
- Todos los valores son texto, también las cantidades: `"2 months"` o `"unknown"`. Se devuelven como los entrega SWAPI.
- A los textos solo se les quitan los espacios del inicio y del final: SWAPI entrega el largo de este vehículo como `"36.8 "`, con un espacio al final, y la API lo devuelve como `"36.8"`.
- `filmIds` y `pilotIds` son identificadores de películas y de personajes, que se pueden pedir en esta misma API. Un vehículo sin pilotos conocidos, como este, trae `pilotIds` vacío; el vehículo `14`, el Snowspeeder, trae `["1", "18"]`.
- Un vehículo tiene los mismos datos que una nave, salvo `hyperdriveRating` y `mglt`, y su clase se llama `vehicleClass`.

### Crear usuarios

Solo un administrador puede crear usuarios. Primero se inicia sesión como `admin`, el administrador de prueba:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin1234"}'
```

Y con ese token:

```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer eyJ..." \
  -H "Content-Type: application/json" \
  -d '{"username": "leia", "password": "alderaan-1977"}'
```

Responde `201` con lo que se creó. El usuario nuevo puede iniciar sesión en el momento:

```json
{
  "username": "leia",
  "role": "USER",
  "createdAt": "2026-11-16T14:03:27.512Z"
}
```

| Campo | Obligatorio | Regla |
|---|---|---|
| `username` | Sí | De 3 a 50 caracteres: solo minúsculas de la `a` a la `z`, dígitos, `.`, `_` y `-` |
| `password` | Sí | De 8 a 72 caracteres, no solo espacios, y no más de 72 bytes en UTF-8. Los espacios cuentan y no se recortan |
| `role` | No | `USER` o `ADMIN`, en mayúsculas. Si no se envía, `USER` |

- La contraseña se guarda cifrada con BCrypt, tal como llegó. La respuesta nunca la trae, ni tampoco su *hash*.
- Con `"role": "ADMIN"` se crea otro administrador, que también puede crear usuarios.
- Nada se corrige en silencio: `Leia` o `"role": "admin"` responden `400`, no se pasan a minúsculas ni a mayúsculas.
- No hay pedidos para listar, modificar ni borrar usuarios.

Los errores, en el orden en que se comprueban:

| Código | Cuándo | `detail` |
|---|---|---|
| `401` | Falta el token, o fue rechazado | `Authentication is required to access this resource.` |
| `403` | El token es de un usuario común, como `demo` | `You do not have permission to perform this request.` |
| `400` | Un campo no cumple su regla | `Invalid request content.` |
| `400` | El cuerpo no se puede leer: no es JSON, falta, o un campo trae un tipo que no se puede convertir | `Failed to read request` |
| `409` | Ya existe un usuario con ese `username` | `A user with that username already exists.` |

- El rol se comprueba antes que el contenido: un usuario común recibe `403` también si envía un cuerpo inválido.
- El `403` no lleva la cabecera `WWW-Authenticate`: quien pide ya inició sesión, y volver a hacerlo no cambia nada.
- El `400` de un cuerpo que no se puede leer no trae `errors`. Pasa también con un campo cuyo tipo no se puede convertir a texto, como `"role": ["ADMIN"]`: el cuerpo no llega a leerse, así que no hay un campo que nombrar.
- El `400` por campos inválidos trae todos juntos en `errors`, ordenados por nombre, cada uno con su regla:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/v1/users",
  "errors": [
    {
      "field": "password",
      "message": "must have 8 to 72 characters, not only spaces, and at most 72 bytes in UTF-8"
    },
    {
      "field": "role",
      "message": "must be USER or ADMIN"
    },
    {
      "field": "username",
      "message": "must have 3 to 50 characters: lowercase letters, digits, '.', '_' or '-'"
    }
  ]
}
```

El porqué de los roles está en el [ADR-0017](docs/adr/0017-role-based-access.md), y el del alta y sus reglas, en el [ADR-0018](docs/adr/0018-user-creation.md).

## Configuración

No hace falta configurar nada: `docker compose` trae valores de desarrollo. Para cambiarlos, copiar `.env.example` a `.env`.

| Variable | Para qué | En `docker compose` | Sin `docker compose` |
|---|---|---|---|
| `DB_URL` | Dirección de la base de datos PostgreSQL | Fija: la base que levanta `docker compose` | Obligatoria |
| `DB_USER` | Usuario de la base de datos | `starwars` | Obligatoria |
| `DB_PASSWORD` | Contraseña de la base de datos | `starwars` | Obligatoria |
| `JWT_SECRET` | Clave que firma los tokens, de al menos 32 caracteres | Una clave de desarrollo, pública | Obligatoria: sin ella la aplicación no arranca |
| `JWT_TTL_MINUTES` | Minutos hasta que vence un token | `60` | `60` |
| `SEED_ENABLED` | Cargar los usuarios de prueba | `true` | `false` |
| `LOGIN_MAX_FAILED_ATTEMPTS` | Cuántos logins fallidos desde una dirección de origen bloquean su login, de `0` a `100`. Con `0` el límite se apaga | `5` | `5` |
| `LOGIN_FAILED_ATTEMPTS_WINDOW` | Cuánto dura la ventana en la que se cuentan esos fallos, y con ella el bloqueo. De `1s` a `24h` | `15m` | `15m` |
| `SWAPI_BASE_URL` | Dirección de SWAPI | `https://www.swapi.tech/api` | `https://www.swapi.tech/api` |
| `SWAPI_CONNECT_TIMEOUT` | Tiempo máximo para conectarse con SWAPI | `3s` | `3s` |
| `SWAPI_READ_TIMEOUT` | Tiempo máximo de espera de una respuesta de SWAPI | `10s` | `10s` |
| `SWAPI_MAX_RETRIES` | Cuántas veces se reintenta un pedido a SWAPI que falló, de `0` a `3`. Con `0` no se reintenta | `1` | `1` |
| `SWAPI_RETRY_DELAY` | Espera antes de cada reintento. Con `0s` se reintenta en el momento | `200ms` | `200ms` |
| `SWAPI_CACHE_TTL` | Tiempo que se guarda una respuesta de SWAPI | `1h` | `1h` |
| `SWAPI_CACHE_MAX_SIZE` | Cantidad máxima de respuestas de SWAPI guardadas | `500` | `500` |
| `APP_PORT` | Puerto de esta máquina donde se publica la aplicación. La usa `docker compose`, no la aplicación | `8080` | No se usa |

Los tiempos se escriben como un número con su unidad: `500ms`, `3s`, `1h`. Un número sin unidad se lee en milisegundos; en la ventana del límite de intentos de login, que tiene un mínimo de un segundo, `15` impide arrancar en vez de dejar el límite sin efecto. Un valor inválido en una variable de SWAPI o del límite de intentos de login impide arrancar, y el mensaje dice cuál corregir.

## Despliegue público

**Dirección:** <https://star-wars-api-7x3l.onrender.com>. La documentación interactiva está en [`/swagger-ui.html`](https://star-wars-api-7x3l.onrender.com/swagger-ui.html) y el control de estado, en [`/actuator/health`](https://star-wars-api-7x3l.onrender.com/actuator/health). Los usuarios de prueba son los mismos que en local: `demo` / `demo1234` y `admin` / `admin1234`. Para probarla con `curl` o con la [colección de pedidos](#colección-de-pedidos) alcanza con cambiar `http://localhost:8080` por esa dirección.

- **Dónde:** la aplicación va en [Render](https://render.com), y la base de datos, en [Neon](https://neon.com), los dos en su plan gratuito.
- **Por qué esos servicios.** La consigna nombra Heroku y Vercel como ejemplos, pero Heroku ya no tiene plan gratuito y Vercel no ejecuta Java. Render no pide tarjeta, construye la imagen desde el `Dockerfile` del repositorio, da los 512 MB de memoria que la aplicación necesita y se describe entero en un archivo. Su base de datos gratuita vence a los 30 días; la de Neon no vence.
- **Cómo está descripto:** en [`render.yaml`](render.yaml), con el motivo de cada variable. No tiene ningún secreto: los tres datos de la base se cargan a mano al crear el servicio, y la clave que firma los tokens la genera Render.
- **Qué se despliega:** la misma imagen que levanta `docker compose`, sin cambios en el código. El archivo pide que cada cambio de `main` se despliegue solo, después de pasar la [verificación automática](#tests).
- **Las credenciales de `admin` son públicas, y con ellas cualquiera puede crear usuarios en la instalación pública,** también otros administradores. Esos usuarios quedan hasta que se los borre desde la base de datos: no hay un pedido para listarlos ni para borrarlos. Es un límite aceptado, porque es lo que quien corrige tiene que poder probar, y no hay datos que proteger ([ADR-0015](docs/adr/0015-public-deployment.md) y [ADR-0017](docs/adr/0017-role-based-access.md)).
- **El límite de intentos de login frena el caso simple, y no es una defensa completa.** Frena a quien prueba contraseñas una tras otra desde una dirección: cinco intentos cada quince minutos, aunque intercale logins correctos o mande muchos pedidos a la vez. No frena un ataque repartido entre muchas direcciones, y quienes comparten una dirección comparten la cuenta. La cuenta se pierde cada vez que la instancia se duerme. Las contraseñas de `demo` y de `admin` son públicas de todos modos: lo que el límite protege son los usuarios que se crean con el alta.
- **En el despliegue, el límite de intentos de login se puede esquivar.** Medido el 15/11/2026 contra el servicio: el intermediario de Render conserva lo que el cliente escribe en la cabecera `X-Forwarded-For` (y en `Forwarded`), y la aplicación, que acá lee las cabeceras del intermediario, cuenta por su primer valor. El límite funciona (el sexto intento responde `429`), pero quien prueba contraseñas puede cambiar esa cabecera en cada pedido, y quien conoce la dirección de otro puede bloquearle el login hasta quince minutos. En local no pasa: sin intermediario la cabecera se ignora. No se corrigió en este ciclo; la prueba, el motivo y la corrección pendiente están en el [ADR-0023](docs/adr/0023-login-attempt-limit.md#notas-posteriores).
- **La primera visita puede tardar un minuto y medio.** El plan gratuito duerme la aplicación tras 15 minutos sin uso, y despertarla es arrancarla de nuevo con muy poco procesador. La base de datos de Neon también se duerme, a los 5 minutos sin consultas, y según sus páginas despierta en menos de un segundo: la espera que se nota es la de la aplicación. Medido en el servicio: la aplicación arranca en 70 segundos, y con el servicio dormido la respuesta llegó a los 90 segundos, sumando un pedido que se cortó a los 60 y el siguiente. Es una sola medición. Mientras despierta, el navegador queda esperando; si corta, alcanza con volver a cargar. Después responde con normalidad: el primer login tarda unos 6 segundos y los pedidos siguientes, menos de uno. No se la mantiene despierta: es el funcionamiento previsto del plan gratuito.
- **Con `demo`, que es público, un visitante puede dejar a los demás sin datos nuevos por quince minutos.** Cada búsqueda con un texto distinto es un pedido a SWAPI, que corta a los 100 cada quince minutos: cien búsquedas distintas agotan ese cupo para todos, y hasta que se renueva lo que no está en la caché responde `502`. Es un límite conocido: el límite de pedidos por usuario (REQ-31 del [PRD](docs/requirements/PRD.md)) no entró en ningún ciclo. No se probó contra el servicio, a propósito.
- **Qué se comprobó en el servicio, y qué no.** Con pedidos directos: el control de estado, los dos logins, los cuatro listados, el `401` sin token, que Swagger UI carga y que la documentación declara `https://`. El recorrido a mano dentro de la página de Swagger UI (iniciar sesión, cargar el token y listar), que es la otra mitad del criterio CA-20.3, no lo hizo ninguna sesión de la herramienta de IA: lo hizo el autor el 16/11/2026.

Las alternativas evaluadas y lo medido están en el [ADR-0015](docs/adr/0015-public-deployment.md).

## Tests

Hace falta un JDK 21 o posterior. Maven no se instala: se usa el *wrapper* (`./mvnw`).

| Para | Comando | Necesita |
|---|---|---|
| Tests unitarios y de arquitectura | `./mvnw test` | Ni red ni Docker |
| Todos los tests, incluidos los de aceptación | `./mvnw verify` | Docker |
| Comprobar la cobertura y su distintivo | `bash scripts/check-coverage.sh` | Haber corrido `./mvnw clean verify` |

- Son 1321 tests: 1050 corren con `./mvnw test` y 271 son de aceptación, que son las clases terminadas en `AcceptanceTest`.
- Los de aceptación levantan la aplicación completa, con un PostgreSQL real, y le hacen pedidos por HTTP. Salen de los criterios de aceptación de la [especificación técnica](docs/spec/SPEC.md#6-criterios-de-aceptación) y, algunos, de los hallazgos de las revisiones finales de cada ciclo. Se escribieron antes que el código.
- Ningún test llama a la SWAPI real: usan respuestas reales grabadas, servidas por una SWAPI simulada. Corren aunque SWAPI esté caída.
- Los de arquitectura comprueban las reglas de dependencia de [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
- **La cobertura está a la vista.** El distintivo de arriba muestra la cobertura de líneas: qué parte de las líneas de `src/main`, sin excluir ninguna clase, ejecutan todos los tests juntos (unitarios, de arquitectura y de aceptación), redondeada hacia abajo a un número entero. El informe completo, clase por clase, es el adjunto `coverage-report` de cada [ejecución](https://github.com/erickvaernet/conexa-star-wars-challenge/actions/workflows/ci.yml?query=branch%3Amain) de la verificación automática; para bajarlo hay que iniciar sesión en GitHub. En una máquina de desarrollo, después de `./mvnw clean verify`, el mismo informe queda en `target/site/jacoco/index.html`.
- **El número del distintivo no puede quedar viejo.** `bash scripts/check-coverage.sh`, después de `./mvnw clean verify`, imprime el porcentaje medido, con las líneas cubiertas y las totales, y falla si el distintivo dice otro número, si no está escrito como su línea de arriba o si la cobertura baja de 95 %. El `clean` importa en una máquina de desarrollo: sin él, el informe puede ser el de una corrida anterior, y lo medido en corridas anteriores se suma. La verificación automática lo corre en cada ejecución y deja el porcentaje en la página de esa ejecución: como una anotación, que se ve sin iniciar sesión, y en su resumen, que GitHub muestra solo a quien inició sesión.
- **Cada cambio se verifica solo.** Cada pull request y cada cambio en `main` corren todos los tests sobre un JDK 21 y levantan la aplicación con `docker compose`, en GitHub Actions. El resultado se ve en el pull request, en el distintivo de arriba y en la [lista de ejecuciones](https://github.com/erickvaernet/conexa-star-wars-challenge/actions/workflows/ci.yml). La definición es [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

El porqué de esta estrategia está en el [ADR-0009](docs/adr/0009-testing-strategy.md); el de la verificación automática, en el [ADR-0014](docs/adr/0014-continuous-verification.md); y el de la cobertura a la vista, en el [ADR-0021](docs/adr/0021-visible-test-coverage.md).

## Uso de herramientas de IA

El equipo técnico de Conexa permitió usar herramientas de IA y pidió documentar el flujo. En pocas líneas:

- **Qué se delegó:** la redacción de los documentos, el código y los tests.
- **Qué decidió el autor en el ciclo 1:** los requisitos y sus prioridades, las decisiones técnicas del RFC y las del contrato de la API, una por una y con las alternativas a la vista. En varias corrigió la recomendación de la herramienta.
- **Qué no decidió en el ciclo 2:** fijó el alcance y delegó también las decisiones, incluidas las de contrato: los roles, el alta de usuarios, la política de contraseñas, el parámetro `expanded` y el detalle de los errores. Las tomó una sesión supervisora de la herramienta, y el autor no revisó ninguna antes de que quedara escrita. Está contado en [Ciclo 2](docs/process/AI-USAGE.md#ciclo-2).
- **En el ciclo 3** eligió el alcance, que son tres de los requisitos "Podría" y el despliegue público, y volvió a delegar las decisiones, entre ellas una respuesta nueva del login. Las tomó la sesión supervisora antes de que otra sesión las redactara. Está contado en [Ciclo 3](docs/process/AI-USAGE.md#ciclo-3).
- **Cómo se revisó:** hasta el ticket #5, el autor aprobó cada paso. Desde el ticket #6, los tests y los pull requests los revisó una sesión supervisora de una herramienta de IA, por encargo del autor, que no los revisó uno por uno; la excepción son los tests de aceptación del ticket #35, que aprobó él.
- **Revisión final de cada ciclo:** con todo fusionado, otra sesión de la herramienta revisó el conjunto con la consigna de encontrar y demostrar fallas. En el ciclo 1 encontró cuatro importantes, que se corrigieron en el [pull request #30](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/30). En el ciclo 2 encontró dos, las dos en la documentación: este resumen y el registro decían menos de lo que se había delegado. Se corrigieron, junto con cuatro hallazgos menores, en el [pull request #46](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/46). En el ciclo 3 encontró cuatro: una en el código, donde logins correctos simultáneos recibían un `429` de bloqueo sin que nadie hubiera fallado, y tres en la documentación, entre ellas el registro de uso de herramientas de IA, que abría con un estado viejo. Qué corregir lo eligió la sesión supervisora, y el autor no lo revisó antes; las correcciones están en el [pull request #56](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/56). Quedó sin corregir, y dicho, que en el despliegue público el cliente elige la dirección por la que se cuentan sus intentos de login.
- **Revisión del autor antes de entregar:** con los tres ciclos cerrados, el autor revisó él mismo, una por una, las diez decisiones que había delegado en los ciclos 2 y 3. Aceptó nueve y dejó una anotada como límite: en el despliegue público, el cliente elige la dirección por la que se cuentan sus intentos de login. Además recorrió Swagger UI a mano en el servicio público y probó la colección de pedidos en Postman. Por qué su revisión fue al final y sobre el conjunto, qué cuesta hacerlo así y cómo se haría en un proyecto real, con una rama intermedia antes de `main`, está en [Revisión del autor antes de entregar](docs/process/AI-USAGE.md#revisión-del-autor-antes-de-entregar).

El detalle, con las correcciones y los límites de esa revisión, está en [`docs/process/AI-USAGE.md`](docs/process/AI-USAGE.md).

## Proceso de trabajo

El desafío se trabaja en **ciclos cortos** y con la **documentación primero**. Cada ciclo termina con una versión estable que se puede entregar tal como está. Dentro de cada ciclo, antes de escribir código queda por escrito qué se construye y cómo. El recorrido completo, etapa por etapa, está en [`docs/process/WORKFLOW.md`](docs/process/WORKFLOW.md).

### Ciclos

| Ciclo          | Fecha                       | Alcance                                                                                                          | Versión  | Estado    |
|----------------|-----------------------------|------------------------------------------------------------------------------------------------------------------|----------|-----------|
| **1. MVP**     | 14/11/2026                  | Todo lo que pide la consigna: los requisitos "Debe" del PRD, incluida la caché de las respuestas de SWAPI                                                      | `v0.1.0` | Cerrado |
| **2. Mejoras** | 15/11/2026                  | Los requisitos "Debería": despliegue público, errores claros, tolerancia a fallas de SWAPI, verificación automática, datos completos a pedido en los listados y rol de administrador con alta de usuarios | `v0.2.0` | Cerrado. El despliegue público pasó al ciclo 3 |
| **3. Extras**  | 15/11/2026 y 16/11/2026     | Tres de los requisitos "Podría": cobertura de tests visible, colección de pedidos lista para usar y límite de intentos de login. Además, lo que quedó del ciclo 2: crear el servicio del despliegue público. No entraron, por el plazo de entrega: consulta flexible con GraphQL, límite de pedidos por usuario, cierre de sesión, registro público de usuarios y caché resistente al abuso | `v0.3.0` | Cerrado |

- **El MVP cumple la consigna por sí solo.** Si un ciclo posterior no llega a cerrarse, se entrega la última versión estable.
- **Cada ciclo cierra con la rama principal estable:** todos los tests pasan y la versión queda etiquetada.
- **Al empezar cada ciclo se revisan las prioridades** del PRD; un requisito puede cambiar de ciclo.

### Etapas dentro de un ciclo

Cada etapa empieza recién cuando la anterior está revisada y aprobada.

| # | Etapa              | Qué responde                                                                                              | Dónde                                                   | Estado en el ciclo 1 |
|---|--------------------|-----------------------------------------------------------------------------------------------------------|---------------------------------------------------------|----------------------|
| 1 | **PRD**            | **Qué** y **por qué**: problema, usuarios, requisitos, prioridades y métricas de éxito                     | [`docs/requirements/PRD.md`](docs/requirements/PRD.md) | Aprobado             |
| 2 | **RFC y ADRs**     | **Cómo**, a nivel macro: arquitectura, tecnologías y seguridad, con las alternativas evaluadas. Cada decisión tomada se registra como un ADR | [`docs/rfc/RFC-001-architecture.md`](docs/rfc/RFC-001-architecture.md) y [`docs/adr/`](docs/adr/README.md) | Aceptado: RFC y 13 ADRs |
| 3 | **Especificación técnica** | El detalle ejecutable: contrato de la API, validaciones, casos límite y criterios de aceptación   | [`docs/spec/SPEC.md`](docs/spec/SPEC.md) | Aprobada |
| 4 | **Implementación** | Tests primero y después el código, historia por historia                                                                   | —                                                       | Hecha: doce tickets y las correcciones de la revisión final, 793 tests |
| 5 | **Cierre**         | Rama principal estable, versión etiquetada y documentación al día                                         | —                                                       | Hecho: versión `v0.1.0` |

En el ciclo 1 los tres documentos se escriben completos. En los ciclos siguientes no se rehacen: se revisan las prioridades del PRD, se registra un ADR por cada decisión nueva y se extiende la especificación técnica con lo que entra.

**Por qué así**

- **Cada documento se apoya en el anterior ya cerrado.** No se eligen tecnologías sin tener los requisitos fijos, ni se detalla un contrato sin haber tomado las decisiones de arquitectura.
- **Todo queda trazable.** Un requisito del PRD lleva a una decisión (ADR), esa decisión a una regla de la especificación técnica, y esa regla a un test.
- **Es la base para trabajar con herramientas de IA.** La especificación técnica es la entrada con la que se genera código asistido: cuanto más precisa es, menos margen queda para que la herramienta invente. El flujo prevé una revisión humana de cada etapa antes de avanzar. En el ciclo 1 se cumplió hasta la especificación, los tickets y los tests del primero; después, la revisión también se delegó. Está contado en [Uso de herramientas de IA](#uso-de-herramientas-de-ia).
- **Los ciclos reducen el riesgo del plazo.** Primero se asegura lo que pide la consigna y después se suman las mejoras, siempre sobre una versión que ya funciona.

### Bitácora

| Fecha      | Qué se hizo                                                                                                                                                             |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 13/11/2026 | Consultas al equipo técnico de Conexa antes de empezar: uso de herramientas de IA, alcance (solo API o también interfaz) y tipo de login. Las respuestas están en el PRD como restricciones. |
| 14/11/2026 | Repositorio creado y convenciones definidas: Conventional Commits y Conventional Branch.                                                                                |
| 14/11/2026 | Primer borrador de requisitos: un PRD y una especificación detallada escritos en paralelo para comparar formatos.                                                       |
| 14/11/2026 | Corrección del proceso: los dos borradores mezclaban requisitos con decisiones técnicas tomadas sin evaluar alternativas. Se separó el trabajo en etapas (PRD → RFC → especificación) y se rehízo el PRD solo con el qué y el porqué. |
| 14/11/2026 | Se organizó el trabajo en ciclos: un MVP con todo lo que pide la consigna y, después, ciclos de mejoras sobre una versión que ya funciona.                               |
| 14/11/2026 | Se cerraron las cinco preguntas abiertas del PRD: combinación de filtros, tipos de usuario, alta de usuarios, despliegue y contenido de los listados. Cada respuesta quedó registrada en el PRD con su motivo. |
| 14/11/2026 | PRD aprobado, con 34 requisitos repartidos en tres ciclos. |
| 14/11/2026 | Se definió cómo se organiza la documentación y cómo se trabaja con tickets: un issue de GitHub por requisito, un hito por ciclo y un pull request por ticket. |
| 14/11/2026 | Se acotó el tercer documento: pasa a llamarse especificación técnica y contiene solo el contrato de la API, las validaciones, los casos límite y los criterios de aceptación. Las decisiones y sus motivos quedan en los ADRs. |
| 14/11/2026 | Se ajustó el manejo de tickets: cada ticket es una historia de usuario con sus tareas adentro, y los tests de aceptación se escriben y se revisan antes que el código. |
| 14/11/2026 | Se documentó el flujo de trabajo completo: etapas, aprobaciones, verificación, uso de herramientas de IA y dónde vive cada regla. |
| 14/11/2026 | Se publicó la documentación base en el [pull request #1](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/1). |
| 14/11/2026 | Borrador del RFC-001: catorce decisiones técnicas del MVP, cada una con sus alternativas y una propuesta. Antes se comprobaron las versiones vigentes de las librerías y el entorno de desarrollo. |
| 14/11/2026 | Al releer la documentación de SWAPI y medir sus tiempos se comprobó que demora cada vez más a quien le hace muchos pedidos y corta a los 100 cada 15 minutos. La caché de sus respuestas (REQ-32) pasó de extra del ciclo 3 a requisito del MVP. |
| 14/11/2026 | RFC-001 aceptado. Las quince decisiones se revisaron una por una: dos cambiaron respecto del borrador (la paginación se decide según cómo responde SWAPI, y los usuarios de prueba se cargan con Flyway) y una es nueva (la caché). |
| 14/11/2026 | Se escribieron los once ADRs que salen del RFC, la guía de arquitectura y las instrucciones para agentes de IA. |
| 14/11/2026 | El RFC, los ADRs y la guía de arquitectura se publicaron en el [pull request #2](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/2). |
| 14/11/2026 | Borrador de la especificación técnica: contrato de la API, validaciones, casos límite y 46 criterios de aceptación. Los datos de cada entidad se tomaron de respuestas reales de SWAPI. |
| 14/11/2026 | Se revisaron una por una las siete decisiones de contrato que el borrador de la especificación dejaba abiertas. Una cambió: los identificadores se devuelven como texto, igual que los entrega SWAPI, y no como número. Las otras seis se confirmaron, varias con precisiones: qué pasa si una relación de SWAPI llega mal formada, y que a los textos solo se les quitan los espacios del inicio y del final. |
| 14/11/2026 | La revisión mostró tres huecos en los documentos ya aceptados: el tamaño máximo de página no tenía motivo escrito, tampoco la respuesta ante un ID inexistente, y no estaba definido qué guarda la caché. Se evaluó guardar también los "no existe" de SWAPI y se descartó: casi nadie repite un ID inexistente, así que solo llenaría la caché. Los motivos quedaron en dos ADRs nuevos (0012 y 0013) y en notas fechadas de los ADRs 0002, 0004 y 0011. |
| 14/11/2026 | Se aclaró la regla de los ADRs: uno aceptado no se modifica, pero admite notas fechadas que no cambian la decisión. La frontera hacia SWAPI pasó a nombrarse también por su patrón, capa anticorrupción. |
| 14/11/2026 | Especificación técnica aprobada, con 51 criterios de aceptación. |
| 14/11/2026 | La especificación técnica y los ADRs 0012 y 0013 se publicaron en el [pull request #3](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/3). |
| 14/11/2026 | Al repasar la caché se evaluó guardar solo los listados y no los recursos específicos. Se mantuvo la decisión: los recursos son pocos (163), pesan poco y son lo que más se repite; lo que no tiene límite son las búsquedas. Proteger la caché de una avalancha de búsquedas inútiles quedó como requisito nuevo del ciclo 3 (REQ-35). |
| 14/11/2026 | Se crearon los tickets del MVP: doce historias de usuario en el [hito `v0.1.0`](https://github.com/erickvaernet/conexa-star-wars-challenge/milestone/1), cada una con sus tareas, sus dependencias y sus criterios de aceptación. Tres decisiones del reparto: la protección del acceso (REQ-10) va separada del login, para revisar por separado la emisión del token y su validación; la caché (REQ-32) va separada del listado de People, para aislar la elección de la librería; y las pruebas de integración (REQ-16) no llevan ticket, porque son parte de la definición de terminado. |
| 14/11/2026 | Primer ticket de código ([#5](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/5)): esqueleto del proyecto y entorno local. Los tests se escribieron y se revisaron antes que el código. Se comprobó que las librerías elegidas funcionan con Spring Boot 4.1 y que `docker compose up` deja la aplicación lista, con su base de datos. |
| 15/11/2026 | Login con usuarios de prueba ([#6](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/6)): tabla de usuarios y usuario `demo` cargados con Flyway, token firmado con HS256 y todo cerrado salvo el login, la documentación y el control de estado. Los tests de aceptación se escribieron antes que el código. Al probarlo se vio que apagar la carga de usuarios sobre una base que ya la tenía impedía arrancar, y se corrigió. |
| 15/11/2026 | Acceso solo con sesión ([#7](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/7)): cada pedido valida la firma y el vencimiento del token, y uno ausente, mal formado, alterado o vencido recibe el mismo `401`. Como antes de este ticket todo respondía `401`, casi todos los tests de aceptación pasaban sin código; se sumó un caso provisorio, con un token válido, para tener uno que fallara por el motivo correcto. |
| 15/11/2026 | Listado de People por páginas ([#8](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/8)), la historia de referencia: el núcleo de paginación y el cliente de SWAPI que usan las otras entidades. Los tests de aceptación se escribieron antes que el código, con respuestas reales grabadas. Al grabarlas se vio que SWAPI repite su última página cuando se le pide una posterior, así que la lista vacía de ese caso la arma la aplicación. Los errores de SWAPI responden `502` y sus demoras, `504`. |
| 15/11/2026 | Caché de las respuestas de SWAPI ([#9](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/9)): un pedido repetido ya no vuelve a SWAPI durante una hora, y un error no se guarda. Se eligió Caffeine después de compararla con el mapa que trae Spring, que no vence ni tiene tope, y con escribirla a mano; se usa de forma directa, en una clase que envuelve al cliente de SWAPI. Los tests de aceptación se escribieron antes que el código y prueban el vencimiento adelantando el reloj de la caché, sin esperar. Quedó escrito el motivo del tope de 500 respuestas, que figuraba sin justificar. |
| 15/11/2026 | Filtro por ID y detalle de un personaje ([#10](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/10)): `?id=` devuelve una página con uno o ningún elemento y `/people/{id}` todos los datos, con las relaciones como listas de identificadores. Los dos usan el mismo pedido a SWAPI y la misma respuesta guardada, y un "no existe" no se guarda. Los tests de aceptación se escribieron antes que el código; al escribirlos se vio que el detalle de un personaje inexistente ya respondía `404` antes de existir, porque la ruta no existía, así que los tests comprueban también qué se le pidió a SWAPI. El detalle vive en `catalog` sin ningún nombre de SWAPI y su traducción en `swapi`: desde ahora, sumar una entidad incluye esa pieza. |
| 15/11/2026 | Filtro por nombre, solo y junto con el ID ([#11](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/11)): `?name=` busca por una parte del nombre, sin distinguir mayúsculas, y con `id` y `name` juntos se pide ese elemento y se comprueba su nombre. Los tests de aceptación se escribieron antes que el código, con las respuestas reales de SWAPI a una búsqueda con coincidencias y a una sin ninguna. Al grabarlas se vio que SWAPI interpreta el texto como un patrón: un punto coincidía con cualquier carácter y un paréntesis suelto le hacía responder un error. Ahora el texto se busca tal cual. La caché guarda una búsqueda entera, sin página ni tamaño, así que recorrer sus páginas es un solo pedido a SWAPI; lo mismo va a valer para el listado de Films. |
| 15/11/2026 | Films, la segunda entidad ([#12](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/12)): listado paginado, filtros por ID y por título, y detalle. Fue el primer uso de los pasos para sumar una entidad: una carpeta en `catalog` con su controlador y sus datos de detalle, y su traducción en `swapi`; el núcleo, la validación, los errores y la caché no se tocaron. Los tests de aceptación se escribieron antes que el código, con las respuestas reales de SWAPI, que confirmaron lo previsto: no pagina las películas (devuelve las seis, se le pida la página que se le pida) y las busca por título. La aplicación recorta la página y recorrer las tres páginas es un solo pedido a SWAPI. El episodio es el único valor numérico: si SWAPI no lo envía llega como `null`, y si envía algo que no es un entero la respuesta es `502`, sin convertirlo. |
| 15/11/2026 | Starships, la tercera entidad ([#13](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/13)): listado paginado, filtros por ID y por nombre, y detalle, con los mismos pasos que Films y sin tocar el núcleo, la validación, los errores ni la caché. Los tests de aceptación se escribieron antes que el código, con las respuestas reales de SWAPI, que pagina las naves como los personajes: son 36. Al grabarlas se vio que el orden del listado no es el de los identificadores. Todos los valores de una nave son texto y ninguno se convierte. La nave grabada no tiene pilotos y repite el mismo valor en dos campos, así que la traducción se prueba además con una nave armada en el test, con todos los valores distintos. |
| 15/11/2026 | Vehicles, la cuarta y última entidad ([#14](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/14)): listado paginado, filtros por ID y por nombre, y detalle, con los mismos pasos que Films y Starships y sin tocar el núcleo, la validación, los errores ni la caché. Los tests de aceptación se escribieron antes que el código, con las respuestas reales de SWAPI, que pagina los vehículos como los personajes y las naves: son 39, y tampoco llegan en el orden de los identificadores. SWAPI entrega el largo del vehículo grabado como `"36.8 "`, con un espacio al final, y la API lo devuelve sin el espacio; el test comprueba primero que la grabación lo sigue trayendo. Ese vehículo no tiene pilotos y repite el mismo valor en dos campos, así que la traducción se prueba además con un vehículo armado en el test. Con esto las cuatro entidades tienen listado, filtros y detalle. |
| 15/11/2026 | Documentación interactiva ([#15](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/15)): Swagger UI ya tiene el botón "Authorize" para cargar el token, y con eso se prueba todo desde el navegador. La documentación ya listaba los nueve pedidos; faltaban la información general, el esquema de seguridad y casi toda la del login, que no decía qué devuelve ni con qué errores responde. El token se exige por defecto en todos los pedidos y el login se exceptúa, con el mismo criterio que las reglas de acceso: una entidad nueva queda con candado sin declarar nada. El login pasó a ser el primer grupo de la página y su cuerpo trae de ejemplo al usuario de prueba. Los tests de aceptación se escribieron antes que el código; dos ya pasaban y se dejaron igual, porque protegen lo que no tiene que cambiar. |
| 15/11/2026 | Cierre de la documentación ([#16](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/16)): el README se reordenó para quien llega por primera vez, con los pasos para levantar, probar y correr los tests al principio, y se escribió el [registro de uso de herramientas de IA](docs/process/AI-USAGE.md). Al escribirlo quedó asentado que, desde el ticket #6, los tests y los pull requests los revisó una sesión supervisora de una herramienta de IA y no una persona, como pide el flujo de trabajo: el flujo y las convenciones llevan una nota que lo dice. |
| 15/11/2026 | Revisión final del MVP y sus correcciones ([pull request #30](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/30)). Con los doce tickets fusionados, una sesión aparte de una herramienta de IA revisó el conjunto sobre un clon limpio, con la consigna de encontrar y demostrar fallas y sin conocer las revisiones anteriores. No encontró nada crítico. Encontró cuatro fallas importantes: un parámetro mal codificado (`name=100%`) respondía `500`; el login respondía `500` a un usuario con un carácter nulo y no limitaba el largo de sus campos; una dirección o un método que el servidor rechaza respondían `401` aun con un token válido; y nueve roturas hechas a propósito en el código no hacían fallar ninguno de los 715 tests. Se corrigieron con los tests antes que el código, junto con cuatro hallazgos menores: ahora las tres primeras responden `400` o `405`, y ocho de las nueve roturas hacen fallar un test. La que queda es el tiempo máximo de conexión con SWAPI, que sigue sin test propio. Las decisiones quedaron en notas de los ADR 0003, 0005, 0007, 0008 y 0013. |
| 15/11/2026 | Cierre del ciclo 1: versión `v0.1.0`. Los doce tickets del hito están cerrados, la revisión final está hecha y sus correcciones están fusionadas, y los 793 tests pasan. El `pom.xml` pasó de `0.1.0-SNAPSHOT` a `0.1.0`, que es la versión que muestra la documentación de la API. |
| 15/11/2026 | Verificación automática en cada cambio ([#33](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/33)), el primer ticket del ciclo 2: cada pull request y cada cambio en `main` corren en GitHub Actions todos los tests sobre un JDK 21 y, a la vez, levantan la aplicación con `docker compose` y le hacen tres pedidos. Hasta acá los tests se habían corrido siempre con un JDK más nuevo y `docker compose` se comprobaba a mano. La primera ejecución pasó sin correcciones, con los 793 tests, en menos de dos minutos. Se comprobó una vez que un test roto se ve: un commit que rompía una regla a propósito dejó la ejecución en rojo, y se revirtió. El README muestra el distintivo con el estado de `main`. |
| 15/11/2026 | Errores de validación campo por campo ([#35](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/35)): un `400` que sale de un campo del cuerpo o de un parámetro trae la lista `errors`, con el nombre de cada campo inválido y la regla que tiene que cumplir. En el login cada campo tiene un solo texto fijo, el mismo para todas sus reglas, así que aparece una sola vez; en los listados y el detalle la lista trae el primer parámetro inválido, sin cambiar su validación. El `415` pasó a tener un texto fijo, porque el de Spring repetía el `Content-Type` recibido. La documentación de la API muestra `errors` en el `400` de los nueve pedidos. Los tests de aceptación se escribieron antes que el código; ningún test anterior se tocó. Queda hecho el mecanismo que usa el alta de usuarios: un pedido nuevo con cuerpo solo declara el texto de cada campo. |
| 15/11/2026 | Despliegue público, primera parte ([#34](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/34)): el despliegue quedó descripto en [`render.yaml`](render.yaml), sin ningún secreto y sin cambiar el código de la aplicación. Su test de aceptación lee las variables del propio archivo, así que no pueden desviarse: sin la variable que hace leer las cabeceras del intermediario, la documentación de la API declara su servidor con `http://` y el test falla. La imagen se probó con los límites del plan gratuito, 512 MB y una décima de procesador: arranca en un minuto y medio y usa la mitad de la memoria. El formato del archivo se revisó contra la referencia que publica el servicio, pero no contra el servicio. **Falta crear el servicio,** que es un paso del autor en persona; recién entonces hay una dirección pública, se mide sobre el servicio real y se cierra el ticket. |
| 15/11/2026 | Datos completos a pedido en los listados ([#37](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/37)): con `expanded=true`, cada elemento de un listado es el mismo objeto que devuelve su detalle, para las cuatro entidades y combinado con los filtros. Es un solo pedido a SWAPI, que tiene ese mismo parámetro, y no uno por elemento; la fuente de datos sumó dos pedidos y el núcleo los pagina con el mismo código que a los resúmenes, sin conocer los datos de ninguna entidad. La caché guarda estas respuestas aparte de las comunes y, además, cada elemento que llega por su identificador: su detalle ya no vuelve a SWAPI. Los tests de aceptación se escribieron antes que el código, con las respuestas reales de SWAPI; cinco de los veinticuatro casos ya pasaban, porque describen lo que no cambia. Lo más incierto era documentar que un elemento es "el resumen o el detalle": se probaron seis formas antes de programar y quedó la que la librería entiende sola, a costa de un tipo por entidad que nada implementa y de una conversión que el compilador no puede comprobar, explicadas en el código y en el ADR-0019. |
| 15/11/2026 | Rol de administrador y alta de usuarios ([#36](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/36)): la carga inicial suma a `admin`, con rol `ADMIN`, y en una base que ya tenía a `demo` lo agrega al arrancar sin tocar a `demo`. Las reglas de acceso leen el rol del token y reservan `POST /api/v1/users` al administrador: sin sesión, `401`; con la sesión de un usuario común, `403`, antes de mirar el cuerpo. El alta valida cada campo con su texto fijo, guarda la contraseña cifrada con BCrypt y responde `409` si el nombre ya existe, también cuando dos altas llegan a la vez. Al escribir los tests apareció una contradicción en la especificación: aceptaba una contraseña de solo espacios, que el login rechaza, así que ese usuario no habría podido entrar nunca. Se corrigió la regla: el alta la rechaza. Los tests de aceptación se escribieron antes que el código. |
| 15/11/2026 | Tolerancia a fallas de SWAPI ([#38](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/38)), el último ticket del ciclo 2: un pedido a SWAPI que falla de una forma que suele arreglarse sola (un `502`, `503` o `504`, o una conexión rechazada o cortada) se repite una vez, 200 milisegundos después, y quien consume no se entera. No se repite un `429`, un `500`, un tiempo máximo vencido ni una respuesta que no se puede leer, y no hay corte automático. Se hizo con lo que trae Spring Framework 7, sin dependencias nuevas, dentro del cliente de SWAPI: la caché y el núcleo no se tocaron. Antes de diseñar se probó esa pieza de Spring y se midió qué informa el cliente HTTP ante cada falla; de ahí salió que cada intento traduce su propia falla, lo que deja garantizado que un tiempo vencido nunca se repite. Los tests de aceptación se escribieron antes que el código, con las fallas armadas sobre la SWAPI simulada; cuatro de los ocho ya pasaban, porque describen lo que no se reintenta. Cada reintento deja una línea en el registro, sin el texto de búsqueda de quien consume. |
| 15/11/2026 | Revisión final del ciclo 2 y sus correcciones ([pull request #46](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/46)). Con cinco de los seis tickets fusionados, una sesión aparte de una herramienta de IA revisó el ciclo sobre un clon limpio, con la misma consigna que la del MVP: encontrar y demostrar fallas, sin conocer las revisiones anteriores. No encontró nada crítico ni ningún error de comportamiento, y de diecisiete roturas hechas a propósito en el código nuevo los tests detectaron dieciséis. Encontró dos fallas importantes, las dos en la documentación: el resumen de uso de herramientas de IA de este README decía que el autor había decidido el contrato de la API, que en el ciclo 2 se delegó, y el registro de ese uso había quedado a medio actualizar. Se corrigieron junto con cuatro hallazgos menores, con los tests antes que el código: los ejemplos de Swagger UI son ahora respuestas reales de la API, y un test los compara con lo que responde; el `405` tiene un solo texto, que no repite el método recibido; el test de CA-24.2 comparaba un objeto consigo mismo y ahora pide el detalle a su propia grabación; y quedó escrito qué responde un cuerpo que no se puede leer. También se escribió lo que estaba solo en los ADR: que el administrador de prueba puede crear usuarios en la instalación pública, y que el cliente HTTP puede repetir un pedido por su cuenta. Las casillas de los tickets y la versión se dejan para el cierre del ciclo. |
| 15/11/2026 | Cierre del ciclo 2: versión `v0.2.0`. Cinco de los seis tickets del hito están cerrados, la revisión final está hecha y sus correcciones están fusionadas, y los 1200 tests pasan. El sexto, el despliegue público ([#34](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/34)), tiene fusionado el archivo que lo describe y le falta crear el servicio, que es un paso del autor en persona: pasó al hito del ciclo 3, como piden las [convenciones](docs/CONVENTIONS.md) para un ticket que queda abierto, y el cambio está anotado en el [PRD](docs/requirements/PRD.md#historial-de-cambios). El `pom.xml` pasó de `0.1.0` a `0.2.0`, que es la versión que muestra la documentación de la API. |
| 15/11/2026 | Colección de pedidos lista para usar ([#49](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/49)): el repositorio trae un archivo de Postman, [`docs/api/star-wars-api.postman_collection.json`](docs/api/star-wars-api.postman_collection.json), con el recorrido completo de la API en orden. Son 27 pedidos: el login de `demo`, cinco por cada entidad, cuatro errores, el login de `admin` y el alta de un usuario. Cada login guarda su token en una variable de la colección y los demás pedidos la envían, así que no hay que copiar nada. Un test compara la colección con la documentación de la API en los dos sentidos: un pedido que se sume o se renombre obliga a actualizarla. Los tests se escribieron antes que el archivo, que se escribió a mano y no trae ningún identificador de la herramienta. Se ejecutó una vez, completa, contra la aplicación levantada con `docker compose` y la SWAPI real: los 27 pedidos respondieron lo esperado y SWAPI recibió 17. |
| 15/11/2026 | Límite de intentos de login ([#50](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/50)): los logins fallidos se cuentan por dirección de origen y, al quinto en quince minutos, todo login desde esa dirección responde `429`, con los segundos que faltan en `Retry-After`, también con las credenciales correctas. Se cuenta por dirección y no por usuario porque las credenciales de prueba son públicas: con un bloqueo por usuario, cualquiera dejaría a quien corrige sin poder entrar. Cada intento ocupa su lugar en la cuenta antes de comprobar la contraseña, así que muchos pedidos a la vez tampoco pasan del límite, y un pedido bloqueado no consulta la base de datos ni calcula BCrypt. La cuenta vive en memoria, con la librería de la caché y sin dependencias nuevas, y se ajusta o se apaga con dos variables. Los tests de aceptación se escribieron antes que el código y adelantan un reloj de prueba en vez de esperar; tres de los dieciocho ya pasaban, porque describen lo que el límite no cambia. **Queda sin medir** qué dirección ve la aplicación detrás del intermediario del despliegue público: de eso depende que ahí el límite frene algo, y se prueba cuando exista el servicio. |
| 15/11/2026 | Cobertura de tests visible ([#48](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/48)): el README muestra la cobertura de líneas en un distintivo, al lado del de la verificación automática, y el número no puede quedar viejo. Un guion, `scripts/check-coverage.sh`, lee el informe de JaCoCo, imprime el porcentaje y falla si el distintivo dice otro número o si la cobertura baja de 95 %; la verificación automática lo corre después de los tests. Medido con los otros dos tickets del ciclo ya fusionados: 665 de 667 líneas, 99,70 %, que el distintivo muestra como 99 % porque redondea hacia abajo. Se probó cada mensaje de error del guion y, una vez, en GitHub: un commit con el número cambiado dejó la ejecución en rojo, y el siguiente lo revirtió. Al implementarlo apareció que GitHub muestra el resumen de una ejecución solo a quien inició sesión, así que el guion deja el porcentaje también como una anotación, que se ve sin iniciar sesión. |
| 15/11/2026 | Despliegue público ([#34](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/34)): la aplicación quedó publicada en <https://star-wars-api-7x3l.onrender.com>, en Render, con la base de datos en Neon. `render.yaml` se aceptó como estaba, sin cambios en el repositorio. El primer despliegue falló porque la contraseña de la base se había cargado incompleta; con el valor corregido arrancó, y Flyway creó la tabla y cargó los usuarios de prueba. Desde la dirección pública responden el control de estado, los dos logins, los cuatro listados con sus filtros, los detalles y la documentación, que declara `https://`. La aplicación arranca en 70 segundos y despierta en un minuto y medio. Cada cambio fusionado en `main` quedó desplegado solo, después de la verificación automática. Se hizo además la prueba que el límite de intentos de login dejaba pendiente, y salió mal: en el despliegue la dirección que cuenta la puede elegir el cliente con una cabecera. Quedó dicho como límite medido, sin corregir en este ciclo. |
| 16/11/2026 | Revisión final del ciclo 3 y sus correcciones ([pull request #56](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/56)). Con los cuatro tickets fusionados y el servicio público andando, una sesión aparte de una herramienta de IA revisó el ciclo sobre un clon limpio, con la misma consigna que las dos anteriores: encontrar y demostrar fallas, sin conocer las revisiones previas. No encontró nada crítico. Encontró cuatro fallas importantes. Una en el código: doce logins correctos enviados a la vez, sin ningún fallo previo, recibieron cinco `200` y siete `429` con `Retry-After: 900`, como si la dirección estuviera bloqueada. Tres en la documentación: el registro de uso de herramientas de IA abría diciendo que el servicio público no existía y que el ciclo no tenía código, este README daba por medido un despertar que el ADR decía sin medir, y el recorrido dentro de Swagger UI en el servicio figuraba hecho sin que nadie lo hubiera hecho. De catorce roturas hechas a propósito en el límite de intentos de login, los tests detectaban nueve siempre y una a veces. Se corrigió, con los tests antes que el código: los fallos y los logins en curso se cuentan aparte, y un login que queda sin lugar por logins en curso recibe `Retry-After: 1`; la ventana del límite tiene un mínimo de un segundo, porque un número sin unidad se lee en milisegundos; el guion de cobertura exige la línea exacta del distintivo; el test de la colección compara el guion del token línea por línea y fija los 27 pedidos; y la documentación que había quedado vieja. Comprobó además contra el servicio que el cliente elige su dirección con una cabecera, como ya estaba escrito, y eso sigue sin corregirse. Qué corregir lo eligió la sesión supervisora. |
| 16/11/2026 | Cierre del ciclo 3: versión `v0.3.0`. Los cuatro tickets del hito están cerrados, la revisión final está hecha y sus correcciones están fusionadas, y los 1321 tests pasan, con una cobertura de líneas de 99,71 %. La aplicación está publicada en <https://star-wars-api-7x3l.onrender.com>. El `pom.xml` pasó de `0.2.0` a `0.3.0`, que es la versión que muestra la documentación de la API. Quedan sin hacer cinco requisitos "Podría" (REQ-30, REQ-31, REQ-33, REQ-34 y REQ-35), y como primera mejora pendiente, que el límite de intentos de login cuente por la dirección que escribe el intermediario del despliegue y no por la que envía el cliente. |
| 16/11/2026 | Revisión del autor antes de entregar. Con todo fusionado y desplegado, el autor revisó una por una las diez decisiones de los ciclos 2 y 3 que había delegado (ADR-0014 a ADR-0023), con una herramienta de IA que le presentó cada una con su código, sus tests y su punto más discutible. Aceptó nueve y dejó anotado como límite lo que se había medido en el límite de intentos de login. Recorrió Swagger UI a mano en el servicio público, que era lo que faltaba del criterio CA-20.3, y probó la colección de pedidos en Postman. De la revisión salieron cambios solo de documentación: este README dice ahora las direcciones del servicio público y de una máquina propia, por qué tarda la primera visita y qué valor poner en la colección, y el flujo de trabajo deja escrito que en un proyecto real la revisión final va en una rama intermedia, antes de `main`. Está contado en el [registro de uso de herramientas de IA](docs/process/AI-USAGE.md#revisión-del-autor-antes-de-entregar). |

## Documentación

- Índice de la documentación: [`docs/README.md`](docs/README.md)
- Flujo de trabajo: [`docs/process/WORKFLOW.md`](docs/process/WORKFLOW.md)
- Requisitos (PRD): [`docs/requirements/PRD.md`](docs/requirements/PRD.md)
- Convenciones de commits, ramas, tickets y pull requests: [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md)
- Decisiones técnicas: [RFC-001](docs/rfc/RFC-001-architecture.md), con las alternativas evaluadas, y los [ADRs](docs/adr/README.md), con cada decisión aceptada y sus notas
- Arquitectura: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Instrucciones para agentes de IA: [`AGENTS.md`](AGENTS.md)
- Especificación técnica: [`docs/spec/SPEC.md`](docs/spec/SPEC.md)
- Tickets de cada ciclo: hitos [`v0.1.0`](https://github.com/erickvaernet/conexa-star-wars-challenge/milestone/1), [`v0.2.0`](https://github.com/erickvaernet/conexa-star-wars-challenge/milestone/2) y [`v0.3.0`](https://github.com/erickvaernet/conexa-star-wars-challenge/milestone/3)
- Instrucciones de configuración y uso: en este README, desde el [inicio rápido](#inicio-rápido)
- Colección de pedidos de la API, para Postman y herramientas parecidas: [`docs/api/star-wars-api.postman_collection.json`](docs/api/star-wars-api.postman_collection.json), explicada en [Colección de pedidos](#colección-de-pedidos)
- Registro de uso de herramientas de IA: [`docs/process/AI-USAGE.md`](docs/process/AI-USAGE.md)

## Autor

Erick Adriel Vaernet — [@erickvaernet](https://github.com/erickvaernet)
