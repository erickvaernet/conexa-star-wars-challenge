# Especificación técnica

| Campo   | Valor                                                                                          |
|---------|------------------------------------------------------------------------------------------------|
| Estado  | Aprobada el 14/11/2026                                                                         |
| Fecha   | 14/11/2026                                                                                     |
| Autor   | Erick Adriel Vaernet                                                                           |
| Alcance | Ciclo 1 (MVP): REQ-01 a REQ-19 y REQ-32 del [PRD](../requirements/PRD.md)                      |
| Se apoya en | [RFC-001](../rfc/RFC-001-architecture.md) y los [ADRs](../adr/README.md)                   |

> **Cómo leer este documento.** Define con exactitud qué hace la aplicación: el contrato de la API, las validaciones, los casos límite y los criterios de aceptación. No explica por qué: eso está en los ADRs, que se enlazan. Los tests de aceptación se escriben a partir de la [sección 6](#6-criterios-de-aceptación), antes que el código.

## 1. Reglas generales

| Regla                | Valor                                                                                     | Origen                                           |
|----------------------|-------------------------------------------------------------------------------------------|--------------------------------------------------|
| Prefijo de las rutas | `/api/v1`                                                                                 | [ADR-0004](../adr/0004-pagination-and-filters.md) |
| Formato              | JSON, con nombres de campos en *camelCase*                                                | —                                                |
| Autenticación        | Cabecera `Authorization: Bearer <token>` en todos los pedidos, salvo los públicos         | [ADR-0005](../adr/0005-authentication.md)        |
| Errores              | Formato Problem Details, con `Content-Type: application/problem+json`                     | [ADR-0007](../adr/0007-error-format.md)          |
| Solo lectura         | Los pedidos de datos son todos `GET`                                                      | PRD, fuera del alcance                           |
| Identificadores      | Texto, tal como los entrega SWAPI (`"id": "1"`). En la entrada solo se aceptan dígitos    | [ADR-0012](../adr/0012-response-data-shape.md)   |

**Pedidos públicos** (no exigen token):

| Pedido                        | Para qué                           |
|-------------------------------|------------------------------------|
| `POST /api/v1/auth/login`     | Iniciar sesión                     |
| `GET /swagger-ui.html`        | Documentación interactiva          |
| `GET /v3/api-docs`            | Documentación en formato OpenAPI   |
| `GET /actuator/health`        | Control de estado                  |

Cualquier otro pedido exige token, incluidos los que se agreguen más adelante.

## 2. Login

### `POST /api/v1/auth/login`

Pedido:

```json
{
  "username": "demo",
  "password": "la-contraseña"
}
```

Respuesta `200`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

| Campo         | Tipo   | Descripción                                   |
|---------------|--------|-----------------------------------------------|
| `accessToken` | texto  | El JWT que se envía en los pedidos siguientes |
| `tokenType`   | texto  | Siempre `Bearer`                              |
| `expiresIn`   | entero | Segundos hasta que el token vence             |

Errores:

| Situación                                         | Código |
|---------------------------------------------------|--------|
| Falta `username` o `password`, o está vacío       | `400`  |
| El usuario no existe o la contraseña no coincide  | `401`  |

El `401` es idéntico en los dos casos, para no revelar qué usuarios existen.

### Token

| Aspecto     | Valor                                                                 |
|-------------|-----------------------------------------------------------------------|
| Firma       | HS256, con una clave tomada de una variable de entorno                |
| Vencimiento | 60 minutos por defecto, configurable                                  |
| Contenido   | `sub` (nombre de usuario), `role`, `iat` (emisión) y `exp` (vencimiento) |

### Usuarios

Tabla `users`:

| Columna         | Tipo           | Reglas                                   |
|-----------------|----------------|------------------------------------------|
| `id`            | entero largo   | Clave primaria, generada                 |
| `username`      | texto (50)     | Obligatorio y único                      |
| `password_hash` | texto (100)    | Obligatorio. Contraseña cifrada con BCrypt |
| `role`          | texto (20)     | Obligatorio. En el MVP, siempre `USER`   |
| `created_at`    | fecha y hora   | Obligatorio                              |

Usuario de prueba que carga el *seed*: `demo`. Su contraseña se publica en el README.

## 3. Listados

### `GET /api/v1/{entidad}`

`{entidad}` es `people`, `films`, `starships` o `vehicles`. Los cuatro aceptan los mismos parámetros y responden con la misma forma.

Parámetros:

| Parámetro | Tipo   | Por defecto | Valores aceptados                              |
|-----------|--------|-------------|------------------------------------------------|
| `page`    | entero | `1`         | 1 o más. La primera página es la 1             |
| `size`    | entero | `10`        | De 1 a 100, ambos incluidos                    |
| `id`      | texto  | —           | Solo dígitos: de 1 a 10, sin ceros a la izquierda |
| `name`    | texto  | —           | De 1 a 100 caracteres, sin contar espacios al inicio y al final |

Los parámetros que no figuran en esta tabla se ignoran.

Respuesta `200`:

```json
{
  "content": [
    { "id": "1", "name": "Luke Skywalker" },
    { "id": "2", "name": "C-3PO" }
  ],
  "page": 1,
  "size": 2,
  "totalElements": 82,
  "totalPages": 41
}
```

| Campo            | Tipo   | Descripción                                                    |
|------------------|--------|----------------------------------------------------------------|
| `content`        | lista  | Los elementos de la página, en el orden en que los da SWAPI    |
| `content[].id`   | texto  | El identificador del elemento, tal como lo da SWAPI            |
| `content[].name` | texto  | El nombre del elemento. En Films, el título                    |
| `page`           | entero | La página pedida                                               |
| `size`           | entero | El tamaño de página pedido                                     |
| `totalElements`  | entero | Cuántos elementos hay en total, con los filtros aplicados      |
| `totalPages`     | entero | `totalElements / size`, redondeado hacia arriba. Con cero elementos, `0` |

### Filtros

| `id`     | `name`   | Resultado                                                                                   |
|----------|----------|---------------------------------------------------------------------------------------------|
| —        | —        | Todos los elementos, paginados                                                              |
| presente | —        | El elemento con ese ID, si existe                                                           |
| —        | presente | Los elementos cuyo nombre contiene el texto, sin distinguir mayúsculas de minúsculas         |
| presente | presente | El elemento con ese ID, solo si además su nombre contiene el texto                           |

- En Films, `name` se compara con el título.
- Un filtro que no encuentra nada devuelve `200` con `content` vacío. No es un error. Vale también para un `id` que no existe: el `404` queda reservado para el detalle ([ADR-0004](../adr/0004-pagination-and-filters.md)).

## 4. Detalle

### `GET /api/v1/{entidad}/{id}`

Devuelve todos los datos de un elemento. Si no existe, responde `404`.

Reglas comunes a las cuatro entidades:

- Los valores se devuelven como los da SWAPI, que usa texto también para los números (`"172"`) porque algunos no son numéricos (`"unknown"`, `"n/a"`). No se convierten ([ADR-0012](../adr/0012-response-data-shape.md)).
- A los textos solo se les quitan los espacios del inicio y del final: `"36.8 "` se devuelve como `"36.8"`.
- Las relaciones con otras entidades de esta API se devuelven como listas de identificadores. Por ejemplo, `filmIds: ["1", "2", "3"]`. SWAPI las entrega como direcciones (`https://www.swapi.tech/api/films/1`); el identificador es el último tramo de cada una ([ADR-0012](../adr/0012-response-data-shape.md)).
- Si una de esas direcciones no permite obtener el identificador, la respuesta es `502`. La relación no se omite en silencio.
- No se devuelven las relaciones con entidades que esta API no expone: planetas y especies. En SWAPI son `homeworld` en People, y `planets` y `species` en Films ([ADR-0012](../adr/0012-response-data-shape.md)).
- Tampoco se devuelven los datos internos de SWAPI: `created`, `edited`, `url` y `description`.

**People**

| Campo         | Tipo             | Dato de SWAPI |
|---------------|------------------|---------------|
| `id`          | texto            | `uid`         |
| `name`        | texto            | `name`        |
| `birthYear`   | texto            | `birth_year`  |
| `gender`      | texto            | `gender`      |
| `height`      | texto            | `height`      |
| `mass`        | texto            | `mass`        |
| `hairColor`   | texto            | `hair_color`  |
| `skinColor`   | texto            | `skin_color`  |
| `eyeColor`    | texto            | `eye_color`   |
| `filmIds`     | lista de textos  | `films`       |
| `starshipIds` | lista de textos  | `starships`   |
| `vehicleIds`  | lista de textos  | `vehicles`    |

**Films**

| Campo          | Tipo             | Dato de SWAPI   |
|----------------|------------------|-----------------|
| `id`           | texto            | `uid`           |
| `title`        | texto            | `title`         |
| `episodeId`    | entero           | `episode_id`    |
| `openingCrawl` | texto            | `opening_crawl` |
| `director`     | texto            | `director`      |
| `producer`     | texto            | `producer`      |
| `releaseDate`  | texto            | `release_date`  |
| `characterIds` | lista de textos  | `characters`    |
| `starshipIds`  | lista de textos  | `starships`     |
| `vehicleIds`   | lista de textos  | `vehicles`      |

**Starships**

| Campo                  | Tipo             | Dato de SWAPI            |
|------------------------|------------------|--------------------------|
| `id`                   | texto            | `uid`                    |
| `name`                 | texto            | `name`                   |
| `model`                | texto            | `model`                  |
| `starshipClass`        | texto            | `starship_class`         |
| `manufacturer`         | texto            | `manufacturer`           |
| `costInCredits`        | texto            | `cost_in_credits`        |
| `length`               | texto            | `length`                 |
| `crew`                 | texto            | `crew`                   |
| `passengers`           | texto            | `passengers`             |
| `maxAtmospheringSpeed` | texto            | `max_atmosphering_speed` |
| `hyperdriveRating`     | texto            | `hyperdrive_rating`      |
| `mglt`                 | texto            | `MGLT`                   |
| `cargoCapacity`        | texto            | `cargo_capacity`         |
| `consumables`          | texto            | `consumables`            |
| `filmIds`              | lista de textos  | `films`                  |
| `pilotIds`             | lista de textos  | `pilots`                 |

**Vehicles**

| Campo                  | Tipo             | Dato de SWAPI            |
|------------------------|------------------|--------------------------|
| `id`                   | texto            | `uid`                    |
| `name`                 | texto            | `name`                   |
| `model`                | texto            | `model`                  |
| `vehicleClass`         | texto            | `vehicle_class`          |
| `manufacturer`         | texto            | `manufacturer`           |
| `costInCredits`        | texto            | `cost_in_credits`        |
| `length`               | texto            | `length`                 |
| `crew`                 | texto            | `crew`                   |
| `passengers`           | texto            | `passengers`             |
| `maxAtmospheringSpeed` | texto            | `max_atmosphering_speed` |
| `cargoCapacity`        | texto            | `cargo_capacity`         |
| `consumables`          | texto            | `consumables`            |
| `filmIds`              | lista de textos  | `films`                  |
| `pilotIds`             | lista de textos  | `pilots`                 |

Los nombres de los datos de SWAPI se tomaron de respuestas reales del 14/11/2026.

## 5. Validaciones, errores y casos límite

### Validaciones

Un valor fuera de lo aceptado responde `400` e indica qué parámetro falló. No se corrige en silencio ([ADR-0013](../adr/0013-parameter-limits-and-invalid-values.md)).

| Parámetro  | Inválido si                                                        | Ejemplos                              |
|------------|--------------------------------------------------------------------|---------------------------------------|
| `page`     | No es un entero, o es menor que 1                                  | `page=0`, `page=-1`, `page=abc`       |
| `size`     | No es un entero, es menor que 1 o mayor que 100                    | `size=0`, `size=101`, `size=abc`      |
| `id`       | Tiene algo que no es un dígito, empieza con cero o supera los 10 dígitos | `id=0`, `id=-3`, `id=abc`, `id=01` |
| `name`     | Queda vacío al quitarle los espacios, o supera los 100 caracteres  | `name=`, `name=%20%20`                |
| `{id}` de la ruta | La misma regla que `id`                                     | `/people/abc`, `/people/0`            |

### Errores

Todas las respuestas de error tienen esta forma:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "El parámetro 'size' debe estar entre 1 y 100.",
  "instance": "/api/v1/people"
}
```

| Código | Cuándo                                                                             |
|--------|------------------------------------------------------------------------------------|
| `400`  | Un parámetro o el cuerpo del pedido no cumple las validaciones                      |
| `401`  | Falta el token, está mal formado, su firma no es válida o está vencido; o el login falló |
| `404`  | El elemento pedido por `{id}` no existe, o la ruta no existe                        |
| `405`  | El método no está permitido en esa ruta                                             |
| `500`  | Error inesperado. La respuesta no incluye detalles internos                         |
| `502`  | SWAPI respondió con un error o con algo que no se puede interpretar                 |
| `504`  | SWAPI no respondió dentro del tiempo máximo de espera                               |

### Casos límite

| Caso                                                         | Resultado                                                                   |
|--------------------------------------------------------------|-----------------------------------------------------------------------------|
| Se pide una página posterior a la última                     | `200`, `content` vacío y los totales correctos                              |
| `name` sin coincidencias                                     | `200`, `content` vacío, `totalElements` 0 y `totalPages` 0                  |
| `id` de un elemento que no existe, en un listado             | `200`, `content` vacío, `totalElements` 0 y `totalPages` 0                  |
| `id` de un elemento que no existe, en el detalle             | `404`                                                                       |
| `id` y `name` juntos, y el nombre no coincide                | `200`, `content` vacío                                                      |
| `id` presente y `page` mayor que 1                           | `200`, `content` vacío; los totales cuentan el elemento si existe           |
| `name` con mayúsculas o minúsculas distintas                 | Mismo resultado: `name=LUKE` equivale a `name=luke`                         |
| SWAPI responde con la lista completa en vez de una página    | La aplicación recorta la página pedida y calcula los totales                |
| El mismo pedido se repite antes de que venza la caché        | Responde sin volver a llamar a SWAPI                                        |
| Se repite el pedido de un `id` que no existe                 | Vuelve a llamar a SWAPI: el "no existe" no se guarda en caché               |
| Se repite un pedido que falló por un error de SWAPI          | Vuelve a llamar a SWAPI: los errores no se guardan en caché                 |
| SWAPI entrega una relación cuya dirección no termina en un identificador | `502`; la relación no se omite en silencio                      |
| SWAPI está caída                                             | Los listados y el detalle responden `502` o `504`; el login sigue funcionando |

### Tiempos y caché

| Ajuste                                  | Por defecto | Configurable |
|-----------------------------------------|-------------|--------------|
| Tiempo máximo de conexión con SWAPI     | 3 segundos  | Sí           |
| Tiempo máximo de lectura de SWAPI       | 10 segundos | Sí           |
| Vencimiento de la caché de SWAPI        | 1 hora      | Sí           |
| Cantidad máxima de respuestas en caché  | 500         | Sí           |

La caché guarda solo las respuestas correctas de SWAPI. Un "no existe" o un error no se guardan ([ADR-0011](../adr/0011-swapi-response-cache.md)).

### Configuración

| Variable de entorno      | Para qué                                              | Obligatoria                       |
|--------------------------|-------------------------------------------------------|-----------------------------------|
| `JWT_SECRET`             | Clave de firma de los tokens, de al menos 32 caracteres | Sí. Sin ella la aplicación no arranca |
| `JWT_TTL_MINUTES`        | Vencimiento del token                                 | No. Por defecto, 60               |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión con PostgreSQL                     | Sí                                |
| `SEED_ENABLED`           | Cargar los usuarios de prueba                         | No. Por defecto, apagado; encendido en `docker compose` |
| `SWAPI_BASE_URL`         | Dirección de SWAPI                                    | No. Por defecto, `https://www.swapi.tech/api` |

## 6. Criterios de aceptación

Cada criterio se identifica como `CA-NN.M`: `NN` es el número del requisito y `M` el número de criterio. Todos suponen la aplicación levantada, SWAPI simulada con respuestas reales grabadas y, salvo que se indique otra cosa, un usuario con sesión iniciada.

### Listados y paginación (REQ-01 a REQ-05)

| Criterio | Dado                                        | Cuando                                        | Entonces                                                                            |
|----------|---------------------------------------------|-----------------------------------------------|-------------------------------------------------------------------------------------|
| CA-01.1  | SWAPI tiene 82 personajes                   | Pide `GET /people`                            | `200`; `page` 1, `size` 10, `totalElements` 82, `totalPages` 9 y 10 elementos        |
| CA-01.2  | SWAPI tiene 82 personajes                   | Pide `GET /people?page=9&size=10`             | `200` con 2 elementos                                                               |
| CA-02.1  | SWAPI tiene 6 películas y no las pagina     | Pide `GET /films?page=2&size=2`               | `200`; `totalElements` 6, `totalPages` 3 y las películas 3.ª y 4.ª en el orden de SWAPI |
| CA-02.2  | SWAPI tiene 6 películas                     | Pide `GET /films`                             | Cada elemento tiene `id` y `name`, y `name` es el título                            |
| CA-03.1  | SWAPI tiene 36 naves                        | Pide `GET /starships?page=1&size=5`           | `200`; `totalElements` 36, `totalPages` 8 y 5 elementos                              |
| CA-04.1  | SWAPI tiene vehículos                       | Pide `GET /vehicles`                          | `200`, con la misma forma de respuesta que las otras tres entidades                 |
| CA-05.1  | Cualquiera de las cuatro entidades          | Pide el listado sin parámetros                | La respuesta tiene exactamente `content`, `page`, `size`, `totalElements` y `totalPages` |
| CA-05.2  | Cualquiera de las cuatro entidades          | Pide el listado sin parámetros                | Cada elemento de `content` tiene exactamente `id` y `name`                          |
| CA-05.3  | SWAPI tiene 82 personajes                   | Pide `GET /people?page=50`                    | `200`; `content` vacío, `totalElements` 82 y `totalPages` 9                          |
| CA-05.4  | —                                           | Pide `page=0`, `page=abc`, `size=0` o `size=101` | `400`, y el detalle nombra el parámetro inválido                                 |
| CA-05.5  | SWAPI tiene 82 personajes                   | Pide `GET /people?size=1` y `GET /people?size=100` | `200` en los dos. Con `size=1`: 1 elemento y `totalPages` 82. Con `size=100`: los 82 elementos y `totalPages` 1 |

### Filtros (REQ-06 a REQ-08)

| Criterio | Dado                                        | Cuando                                        | Entonces                                                                  |
|----------|---------------------------------------------|-----------------------------------------------|---------------------------------------------------------------------------|
| CA-06.1  | Existe el personaje 1, Luke Skywalker       | Pide `GET /people/1`                          | `200` con sus datos completos, según la tabla de People                   |
| CA-06.2  | No existe el personaje 9999                 | Pide `GET /people/9999`                       | `404`                                                                     |
| CA-06.3  | Existe el personaje 1                       | Pide `GET /people?id=1`                       | `200`; `totalElements` 1 y un elemento con `id` "1" y `name` "Luke Skywalker" |
| CA-06.4  | No existe el personaje 9999                 | Pide `GET /people?id=9999`                    | `200`; `content` vacío y `totalElements` 0                                 |
| CA-06.5  | —                                           | Pide `GET /people/abc` o `GET /people?id=0`   | `400`                                                                     |
| CA-06.6  | Existen la película 1, la nave 9 y el vehículo 4 | Pide el detalle de cada uno              | `200` con los campos de su tabla; las relaciones son listas de identificadores, en texto    |
| CA-06.7  | SWAPI entrega el vehículo 4 con `length` `"36.8 "`, con un espacio al final | Pide `GET /vehicles/4`     | `200`; `length` es `"36.8"`, sin el espacio                               |
| CA-07.1  | Hay 3 personajes cuyo nombre contiene "sky" | Pide `GET /people?name=sky`                   | `200`; `totalElements` 3                                                   |
| CA-07.2  | Hay 3 personajes cuyo nombre contiene "sky" | Pide `GET /people?name=SKY`                   | El mismo resultado que CA-07.1                                             |
| CA-07.3  | Hay 3 personajes cuyo nombre contiene "sky" | Pide `GET /people?name=sky&page=2&size=2`     | `200`; `totalElements` 3, `totalPages` 2 y un elemento                     |
| CA-07.4  | Existe la película "A New Hope"             | Pide `GET /films?name=hope`                   | `200`, e incluye el elemento con `name` "A New Hope"                       |
| CA-07.5  | Ningún personaje contiene "zzzz"            | Pide `GET /people?name=zzzz`                  | `200`; `content` vacío, `totalElements` 0 y `totalPages` 0                 |
| CA-07.6  | —                                           | Pide `name=` vacío o de más de 100 caracteres | `400`                                                                     |
| CA-08.1  | El personaje 1 es Luke Skywalker            | Pide `GET /people?id=1&name=luke`             | `200`; un elemento, con `id` "1"                                           |
| CA-08.2  | El personaje 1 es Luke Skywalker            | Pide `GET /people?id=1&name=vader`            | `200`; `content` vacío y `totalElements` 0                                 |

### Login y acceso (REQ-09 a REQ-11)

| Criterio | Dado                                        | Cuando                                              | Entonces                                                                    |
|----------|---------------------------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------|
| CA-09.1  | Existe el usuario de prueba                 | Hace login con su usuario y su contraseña           | `200`, con `accessToken`, `tokenType` "Bearer" y `expiresIn` 3600            |
| CA-09.2  | Existe el usuario de prueba                 | Hace login con una contraseña incorrecta            | `401`                                                                       |
| CA-09.3  | No existe el usuario "nadie"                | Hace login como "nadie"                             | `401`, idéntico al de CA-09.2                                                |
| CA-09.4  | —                                           | Hace login sin `username` o sin `password`          | `400`                                                                       |
| CA-09.5  | Existe el usuario de prueba                 | Se consulta su fila en la base de datos             | La contraseña está cifrada con BCrypt; el texto original no aparece          |
| CA-10.1  | Sin sesión iniciada                         | Pide cualquiera de los cuatro listados o un detalle | `401`                                                                       |
| CA-10.2  | Un token con la firma alterada              | Pide `GET /people`                                  | `401`                                                                       |
| CA-10.3  | Un token vencido                            | Pide `GET /people`                                  | `401`                                                                       |
| CA-10.4  | Un token válido                             | Pide cada uno de los cuatro listados                | `200` en los cuatro                                                         |
| CA-10.5  | Sin sesión iniciada                         | Pide la documentación de la API o el control de estado | `200`                                                                    |
| CA-11.1  | Una base de datos vacía y el *seed* activado | Arranca la aplicación                              | Se crean las tablas y existe el usuario de prueba                            |
| CA-11.2  | La aplicación ya arrancó una vez            | Se reinicia                                         | No falla ni duplica al usuario de prueba                                    |

### Documentación de la API, entorno y dependencia de SWAPI (REQ-12, REQ-16, REQ-19 y REQ-32)

| Criterio | Dado                                        | Cuando                                              | Entonces                                                                    |
|----------|---------------------------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------|
| CA-12.1  | La aplicación levantada                     | Se pide `GET /v3/api-docs`                          | La documentación incluye el login, los cuatro listados y los cuatro detalles |
| CA-12.2  | La aplicación levantada                     | Se pide `GET /v3/api-docs`                          | Declara el esquema de seguridad Bearer y lo aplica a los listados y los detalles |
| CA-16.1  | La SWAPI real no es accesible               | Se ejecutan todos los tests                         | Pasan: ninguno llama a la SWAPI real                                        |
| CA-16.2  | SWAPI responde con un error 500             | Pide `GET /people`                                  | `502`, sin detalles internos                                                |
| CA-16.3  | SWAPI tarda más que el tiempo máximo de lectura | Pide `GET /people`                              | `504`                                                                       |
| CA-16.4  | SWAPI está caída                            | Hace login                                          | `200`: el login no depende de SWAPI                                         |
| CA-16.5  | SWAPI entrega un personaje con una relación cuya dirección no termina en un identificador | Pide el detalle de ese personaje | `502`, sin detalles internos                              |
| CA-19.1  | Una máquina con Docker y sin Java           | Se ejecuta `docker compose up`                      | El control de estado responde que la aplicación está lista, y el login del usuario de prueba funciona |
| CA-32.1  | La caché vacía                              | Se pide dos veces `GET /people?page=1`              | SWAPI recibe un solo pedido                                                 |
| CA-32.2  | La caché vacía                              | Se piden las páginas 1, 2 y 3 de `GET /films?size=2` | SWAPI recibe un solo pedido                                                |
| CA-32.3  | Un vencimiento de caché ya cumplido         | Se repite un pedido                                 | SWAPI recibe un pedido nuevo                                                |
| CA-32.4  | SWAPI responde con un error 500 y después se recupera | Se pide dos veces `GET /people`            | La primera vez, `502`; la segunda, `200`. SWAPI recibe dos pedidos          |
| CA-32.5  | No existe el personaje 9999                 | Se pide dos veces `GET /people/9999`                | `404` las dos veces. SWAPI recibe dos pedidos                               |

### Requisitos que se verifican por revisión

No tienen test de aceptación: se comprueban al revisar cada pull request y al cerrar el ciclo.

| Requisito | Qué se comprueba                                                                                              |
|-----------|---------------------------------------------------------------------------------------------------------------|
| REQ-13    | El README explica cómo levantar el proyecto, las credenciales de prueba, ejemplos de pedidos y cómo correr los tests |
| REQ-14    | Las clases e interfaces públicas y los métodos no obvios tienen Javadoc                                        |
| REQ-15    | Hay tests unitarios de la paginación en memoria, los filtros y la traducción de SWAPI; corren sin red ni Docker |
| REQ-17    | Cada decisión técnica nueva tiene su ADR                                                                      |
| REQ-18    | El registro de uso de herramientas de IA está al día                                                          |

## 7. Trazabilidad

| Requisito            | Dónde se especifica                 | Criterios                |
|----------------------|-------------------------------------|--------------------------|
| REQ-01 a REQ-04      | Sección 3                           | CA-01.1 a CA-04.1        |
| REQ-05               | Secciones 3 y 5                     | CA-05.1 a CA-05.5        |
| REQ-06               | Secciones 3, 4 y 5                  | CA-06.1 a CA-06.7        |
| REQ-07               | Sección 3                           | CA-07.1 a CA-07.6        |
| REQ-08               | Sección 3                           | CA-08.1 y CA-08.2        |
| REQ-09               | Sección 2                           | CA-09.1 a CA-09.5        |
| REQ-10               | Secciones 1 y 5                     | CA-10.1 a CA-10.5        |
| REQ-11               | Sección 2                           | CA-11.1 y CA-11.2        |
| REQ-12               | Sección 1                           | CA-12.1 y CA-12.2        |
| REQ-13, REQ-14, REQ-15, REQ-17, REQ-18 | Sección 6, por revisión | —                    |
| REQ-16               | Secciones 4 y 5                     | CA-16.1 a CA-16.5        |
| REQ-19               | Sección 5                           | CA-19.1                  |
| REQ-32               | Sección 5                           | CA-32.1 a CA-32.5        |
