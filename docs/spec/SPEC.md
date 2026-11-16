# Especificación técnica

| Campo   | Valor                                                                                          |
|---------|------------------------------------------------------------------------------------------------|
| Estado  | Aprobada el 14/11/2026. Extendida para el ciclo 2 y para el ciclo 3 el 15/11/2026              |
| Fecha   | 14/11/2026                                                                                     |
| Autor   | Erick Adriel Vaernet                                                                           |
| Alcance | Ciclo 1 (MVP): REQ-01 a REQ-19 y REQ-32. Ciclo 2: REQ-20 a REQ-26. Ciclo 3: REQ-27, REQ-28 y REQ-29 del [PRD](../requirements/PRD.md) |
| Se apoya en | [RFC-001](../rfc/RFC-001-architecture.md) y los [ADRs](../adr/README.md); para el ciclo 2, del ADR-0014 al ADR-0020; para el ciclo 3, del ADR-0021 al ADR-0023 |

> **Cómo leer este documento.** Define con exactitud qué hace la aplicación: el contrato de la API, las validaciones, los casos límite y los criterios de aceptación. No explica por qué: eso está en los ADRs, que se enlazan. Los tests de aceptación se escriben a partir de la [sección 6](#6-criterios-de-aceptación), antes que el código.

> **Nota del 15/11/2026.** El autor aprobó este documento el 14/11/2026, con el alcance del ciclo 1. La extensión para el ciclo 2 no la revisó punto por punto: delegó sus decisiones, incluidas las de contrato, en la sesión supervisora de una herramienta de IA, que las tomó sobre propuestas con alternativas y una recomendación. Están en los ADRs, del 0014 al 0020, y el encargo, en el [registro de uso de herramientas de IA](../process/AI-USAGE.md#ciclo-2). En el mismo cambio se escribieron acá reglas que ya estaban decididas en notas de los ADRs del ciclo 1 y que este documento no decía; cada una enlaza a su ADR. Ningún criterio de aceptación del ciclo 1 cambió.

> **Nota del 15/11/2026, ciclo 3.** La extensión para el ciclo 3 suma tres requisitos "Podría": la cobertura de tests visible (REQ-27), la colección de pedidos (REQ-28) y el límite de intentos de login (REQ-29). El autor eligió ese alcance y volvió a delegar las decisiones, incluidas las de contrato, en la sesión supervisora de una herramienta de IA, que las tomó antes de que se redactaran; otra sesión las redactó y comprobó lo que se podía medir. Están en los ADRs, del 0021 al 0023, y el encargo, en el [registro de uso de herramientas de IA](../process/AI-USAGE.md#ciclo-3). El único cambio en el contrato de la API es una respuesta nueva del login, el `429`. El despliegue público (REQ-20) pasó a este ciclo sin cambios: sus criterios son los del ciclo 2. Ningún criterio de aceptación de los ciclos 1 y 2 cambió.

## 1. Reglas generales

| Regla                | Valor                                                                                     | Origen                                           |
|----------------------|-------------------------------------------------------------------------------------------|--------------------------------------------------|
| Prefijo de las rutas | `/api/v1`                                                                                 | [ADR-0004](../adr/0004-pagination-and-filters.md) |
| Formato              | JSON, con nombres de campos en *camelCase*                                                | —                                                |
| Autenticación        | Cabecera `Authorization: Bearer <token>` en todos los pedidos, salvo los públicos         | [ADR-0005](../adr/0005-authentication.md)        |
| Errores              | Formato Problem Details, con `Content-Type: application/problem+json`                     | [ADR-0007](../adr/0007-error-format.md)          |
| Solo lectura         | Los pedidos de datos de Star Wars son todos `GET`. El único pedido que escribe es el alta de usuarios | PRD, fuera del alcance; [ADR-0018](../adr/0018-user-creation.md) |
| Identificadores      | Texto, tal como los entrega SWAPI (`"id": "1"`). En la entrada solo se aceptan dígitos    | [ADR-0012](../adr/0012-response-data-shape.md)   |
| Roles                | Cada usuario tiene un rol, `USER` o `ADMIN`. El token lo lleva, y las reglas de acceso lo leen de ahí | [ADR-0017](../adr/0017-role-based-access.md) |

**Pedidos públicos** (no exigen token):

| Pedido                        | Para qué                           |
|-------------------------------|------------------------------------|
| `POST /api/v1/auth/login`     | Iniciar sesión                     |
| `GET /swagger-ui.html`        | Documentación interactiva          |
| `GET /v3/api-docs`            | Documentación en formato OpenAPI   |
| `GET /actuator/health`        | Control de estado                  |

Cualquier otro pedido exige token, incluidos los que se agreguen más adelante.

Un pedido público no necesita token. Si igual trae uno y ese token es rechazado (está mal formado, vencido o firmado con otra clave), responde `401`: el token se valida antes de mirar si el pedido es público ([ADR-0005](../adr/0005-authentication.md), nota del 15/11/2026).

**Qué puede hacer cada rol:**

| Pedido                                         | `USER`    | `ADMIN` |
|------------------------------------------------|-----------|---------|
| Los cuatro listados y los cuatro detalles      | Sí        | Sí      |
| `POST /api/v1/users`, el alta de usuarios      | No: `403` | Sí      |

Un pedido que exige un rol se comprueba en este orden: primero la sesión (`401`), después el rol (`403`) y recién entonces el contenido del pedido (`400`).

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
| Falta `username` o `password`, está vacío o supera su límite: 50 caracteres y ningún carácter de control en `username`, 72 caracteres en `password` ([ADR-0005](../adr/0005-authentication.md), nota del 15/11/2026). La respuesta dice qué campo, en `errors` ([sección 5](#errores)) | `400`  |
| El usuario no existe o la contraseña no coincide  | `401`  |
| La dirección de origen del pedido ya acumuló cinco logins fallidos dentro de la ventana de quince minutos ([límite de intentos](#límite-de-intentos-de-login)) | `429`  |

El `401` es idéntico en los dos casos, para no revelar qué usuarios existen. Lleva la cabecera `WWW-Authenticate: Bearer`, como todo `401` de la API.

### Límite de intentos de login

Los logins fallidos se cuentan por **dirección de origen** del pedido, no por usuario ([ADR-0023](../adr/0023-login-attempt-limit.md)).

| Regla                | Valor                                                                                                         |
|----------------------|---------------------------------------------------------------------------------------------------------------|
| Qué cuenta           | Un login que responde `401`: el usuario no existe o la contraseña no coincide                                  |
| Qué no cuenta        | Un login correcto, y un pedido rechazado antes de mirar las credenciales (`400`, `415`)                        |
| Umbral               | Cinco fallos, por defecto. Los cinco responden `401`, como siempre                                             |
| Ventana              | Quince minutos, por defecto. Empieza con el primer fallo de esa dirección y no se corre: ni los fallos siguientes ni los pedidos bloqueados la alargan |
| Bloqueo              | Alcanzado el umbral, todo login desde esa dirección responde `429`, también con las credenciales correctas: no se comprueba la contraseña ni se consulta la base de datos |
| Fin del bloqueo      | Cuando vence la ventana: a los quince minutos justos del primer fallo ya venció. La cuenta de esa dirección vuelve a cero |
| Un login correcto    | No borra los fallos ya contados                                                                                |
| Orden                | Primero la forma del pedido (`400`, `415`), después el límite (`429`) y recién entonces las credenciales (`401`) |
| Pedidos simultáneos  | Cada intento se cuenta antes de comprobar la contraseña y se descuenta solo si el login resulta correcto: de los que llegan a la vez desde una dirección, a lo sumo cinco llegan a comprobarla |
| Dirección de origen  | La que ve la aplicación: la de la conexión o, en una instalación configurada para leer las cabeceras de un intermediario, como el despliegue público, la que el intermediario informa. Se usa como texto, recortada a 45 caracteres |
| Alcance              | Solo el login. Un token ya emitido sigue sirviendo, y los demás pedidos no se cuentan ni se bloquean           |
| Dónde se guarda      | En la memoria de la aplicación, con un tope de 10 000 direcciones. Se pierde al reiniciar                      |

Respuesta `429`, con la cabecera `Retry-After`:

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

- `Retry-After` trae los segundos que faltan para que venza la ventana: un número entero, de 1 a la duración de la ventana (900 con el valor por defecto).
- El texto es fijo: no dice cuántos fallos hubo ni repite nada de lo enviado. El `429` no lleva la cabecera `WWW-Authenticate`.
- El `401` del login no cambia: tiene el mismo texto y no dice cuántos intentos quedan.
- Con el umbral en cero, el límite está apagado: no se cuenta nada y el login nunca responde `429`.

### Token

| Aspecto     | Valor                                                                 |
|-------------|-----------------------------------------------------------------------|
| Firma       | HS256, con una clave tomada de una variable de entorno                |
| Vencimiento | 60 minutos por defecto, configurable                                  |
| Contenido   | `sub` (nombre de usuario), `role` (`USER` o `ADMIN`), `iat` (emisión) y `exp` (vencimiento) |

### Usuarios

Tabla `users`:

| Columna         | Tipo           | Reglas                                   |
|-----------------|----------------|------------------------------------------|
| `id`            | entero largo   | Clave primaria, generada                 |
| `username`      | texto (50)     | Obligatorio y único                      |
| `password_hash` | texto (100)    | Obligatorio. Contraseña cifrada con BCrypt |
| `role`          | texto (20)     | Obligatorio. `USER` o `ADMIN`            |
| `created_at`    | fecha y hora   | Obligatorio                              |

Usuarios de prueba que carga el *seed*, uno de cada rol. Sus contraseñas se publican en el README.

| Usuario | Rol     |
|---------|---------|
| `demo`  | `USER`  |
| `admin` | `ADMIN` |

En una base de datos que ya tenía cargado a `demo`, `admin` se agrega al arrancar y `demo` queda como estaba ([ADR-0017](../adr/0017-role-based-access.md)).

### `POST /api/v1/users`

Crea un usuario. Exige el rol `ADMIN` ([ADR-0017](../adr/0017-role-based-access.md) y [ADR-0018](../adr/0018-user-creation.md)).

Pedido:

```json
{
  "username": "leia",
  "password": "alderaan-1977",
  "role": "USER"
}
```

| Campo      | Obligatorio | Valores aceptados                                                                          |
|------------|-------------|--------------------------------------------------------------------------------------------|
| `username` | Sí          | De 3 a 50 caracteres. Solo letras minúsculas de la `a` a la `z`, dígitos, punto, guion y guion bajo |
| `password` | Sí          | De 8 a 72 caracteres, no solo espacios, y no más de 72 bytes en UTF-8. Los espacios cuentan y no se recortan |
| `role`     | No          | `USER` o `ADMIN`, en mayúsculas. Por defecto, `USER`                                  |

Los campos que no figuran en esta tabla se ignoran.

Respuesta `201`:

```json
{
  "username": "leia",
  "role": "USER",
  "createdAt": "2026-11-16T14:03:27.512Z"
}
```

| Campo       | Tipo   | Descripción                                             |
|-------------|--------|---------------------------------------------------------|
| `username`  | texto  | El nombre del usuario creado                            |
| `role`      | texto  | El rol con el que quedó: el enviado o, si no se envió, `USER` |
| `createdAt` | texto  | Cuándo se creó: fecha y hora en UTC, en formato ISO 8601 |

La respuesta nunca incluye la contraseña ni su *hash*, y no lleva la cabecera `Location`: no hay un pedido para consultar un usuario.

Errores:

| Situación                                                      | Código |
|----------------------------------------------------------------|--------|
| Un campo no cumple su regla. La respuesta dice cuáles, en `errors` | `400` |
| El cuerpo no se puede leer: no es JSON, falta, o un campo trae un tipo que no se puede convertir. La respuesta no lleva `errors` | `400` |
| Falta el token, o fue rechazado                                | `401`  |
| El token es de un usuario con rol `USER`                       | `403`  |
| Ya existe un usuario con ese `username`                        | `409`  |

El usuario creado puede iniciar sesión en el momento. La contraseña se guarda cifrada con BCrypt, tal como llegó.

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
| `expanded` | booleano | `false`   | `true` o `false`, en minúsculas                |

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

### Datos completos a pedido

Con `expanded=true`, la página es la misma y cambia solo lo que trae cada elemento: `content[]` pasa a tener **el mismo objeto que devuelve el detalle** de esa entidad ([sección 4](#4-detalle)), con exactamente sus campos ([ADR-0019](../adr/0019-expanded-listings.md)).

`GET /api/v1/people?expanded=true&size=2`, respuesta `200`:

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

- `expanded` no cambia qué elementos se devuelven, ni su orden, ni `page`, `size`, `totalElements` o `totalPages`.
- Se combina con `id` y con `name` como cualquier listado: los filtros deciden qué elementos van, y `expanded`, cuántos datos trae cada uno.
- En Films, un elemento completo tiene `title` y no `name`, igual que su detalle.
- Valen para cada elemento todas las reglas del detalle: valores como texto, relaciones como listas de identificadores, sin planetas ni especies.
- Sin el parámetro, o con `expanded=false`, cada elemento sigue siendo el resumen: `id` y `name`.

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
- Si SWAPI no envía un valor de texto, ese campo llega como `null`; un texto vacío se devuelve vacío. Si no envía una relación, esa lista llega vacía. Si no envía el identificador o el nombre (en Films, el título), la respuesta es `502` ([ADR-0012](../adr/0012-response-data-shape.md), nota del 15/11/2026).
- `episodeId`, de Films, es el único valor numérico. Si SWAPI envía un número entero, se devuelve ese número; si no lo envía, llega como `null`; si envía otra cosa (un texto, aunque sea `"4"`, o un número con decimales), la respuesta es `502` ([ADR-0012](../adr/0012-response-data-shape.md), nota del 15/11/2026).

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
| `expanded` | No es exactamente `true` o `false`                                 | `expanded=1`, `expanded=TRUE`, `expanded=` |
| `{id}` de la ruta | La misma regla que `id`                                     | `/people/abc`, `/people/0`            |

Si varios parámetros son inválidos a la vez, el error nombra el primero en este orden: `page`, `size`, `id`, `name`, `expanded`.

**Campos del cuerpo.** Un campo que no cumple su regla responde `400`. No se corrige en silencio: ni se recortan espacios ni se cambian mayúsculas.

| Pedido | Campo      | Inválido si                                                                 | Ejemplos                                   |
|--------|------------|-----------------------------------------------------------------------------|--------------------------------------------|
| Login  | `username` | Falta, está vacío o tiene solo espacios; supera los 50 caracteres; tiene un carácter de control | `""`, `"   "`                |
| Login  | `password` | Falta, está vacío o tiene solo espacios; supera los 72 caracteres            | `""`                                       |
| Alta   | `username` | Falta; tiene menos de 3 o más de 50 caracteres; tiene algo que no es una minúscula de la `a` a la `z`, un dígito, `.`, `_` o `-` | `"ab"`, `"Leia"`, `"leia organa"`, `"leía"` |
| Alta   | `password` | Falta; tiene menos de 8 o más de 72 caracteres; tiene solo espacios; ocupa más de 72 bytes en UTF-8 | `"1234567"`, 8 espacios, 40 veces `"é"` |
| Alta   | `role`     | Está y no es exactamente `USER` o `ADMIN`                                    | `"admin"`, `"ROOT"`, `""`                  |

> **Nota del 15/11/2026.** La regla de `password` del alta se corrigió al implementarla (ticket #36). La extensión del ciclo 2 aceptaba una contraseña de solo espacios y, a la vez, decía que el usuario creado puede iniciar sesión; el login rechaza una contraseña así, de modo que ese usuario no habría podido entrar nunca. Ahora el alta la rechaza, y su texto de `message` lo dice. La corrección la autorizó la sesión supervisora que había escrito la extensión; el detalle y las alternativas están en el [ADR-0018](../adr/0018-user-creation.md), nota de esta fecha. Ningún criterio de aceptación cambió.

### Errores

Las respuestas de error tienen esta forma:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Parameter 'size' must be an integer between 1 and 100.",
  "instance": "/api/v1/people"
}
```

La única excepción es lo que el servidor web rechaza antes de armar el pedido, como un carácter prohibido sin codificar o una cabecera demasiado grande: responde su propio `400`, sin pasar por la aplicación y sin este formato ([ADR-0007](../adr/0007-error-format.md), nota del 15/11/2026).

Los textos de `detail` están en inglés. Los de validación son fijos y nunca repiten el valor recibido ([ADR-0007](../adr/0007-error-format.md), notas del 15/11/2026).

**Errores de validación.** Un `400` que sale de validar un campo del cuerpo o un parámetro lleva además una lista `errors`, con un elemento por cada campo inválido ([ADR-0016](../adr/0016-validation-error-detail.md)):

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/v1/users",
  "errors": [
    { "field": "password", "message": "must have 8 to 72 characters, not only spaces, and at most 72 bytes in UTF-8" },
    { "field": "username", "message": "must have 3 to 50 characters: lowercase letters, digits, '.', '_' or '-'" }
  ]
}
```

| Campo              | Tipo  | Descripción                                                                  |
|--------------------|-------|------------------------------------------------------------------------------|
| `errors`           | lista | Los campos inválidos, ordenados por `field`. Cada campo aparece una sola vez |
| `errors[].field`   | texto | El nombre del campo o del parámetro, como lo envía el cliente                |
| `errors[].message` | texto | La regla que tiene que cumplir. Es un texto fijo por campo y nunca repite el valor recibido |

Textos de `message`:

| Pedido   | `field`    | `message`                                                                           |
|----------|------------|-------------------------------------------------------------------------------------|
| Login    | `username` | `must not be blank, and must have at most 50 characters and no control characters`   |
| Login    | `password` | `must not be blank, and must have at most 72 characters`                             |
| Alta     | `username` | `must have 3 to 50 characters: lowercase letters, digits, '.', '_' or '-'`           |
| Alta     | `password` | `must have 8 to 72 characters, not only spaces, and at most 72 bytes in UTF-8`       |
| Alta     | `role`     | `must be USER or ADMIN`                                                             |
| Listados | `page`     | `must be an integer greater than or equal to 1`                                     |
| Listados | `size`     | `must be an integer between 1 and 100`                                              |
| Listados y detalle | `id` | `must be a number of 1 to 10 digits, without leading zeros`                     |
| Listados | `name`     | `must have 1 to 100 characters, not counting leading and trailing spaces`           |
| Listados | `expanded` | `must be true or false`                                                             |

- En el cuerpo, `detail` es siempre `Invalid request content.` y `errors` trae todos los campos inválidos.
- En los parámetros, `detail` sigue nombrando el parámetro (`Parameter 'size' must be an integer between 1 and 100.`) y `errors` trae un solo elemento, con ese mismo parámetro: el primero inválido.
- Un `400` que no sale de un campo no lleva `errors`: un cuerpo que no es JSON, un parámetro mal codificado o una dirección rechazada.
- Un cuerpo que no se puede leer con la forma esperada responde `400` con `Failed to read request` y sin `errors`: no es JSON, falta, o un campo trae un tipo que no se puede convertir, como una lista donde va un texto (`"role": ["ADMIN"]`). En el último caso falló un campo, pero el cuerpo no llegó a leerse, así que no hay regla que informar. Un número o un valor lógico donde va un texto sí se convierten, y se validan como texto ([ADR-0016](../adr/0016-validation-error-detail.md), nota del 15/11/2026).

Códigos:

| Código | Cuándo                                                                             |
|--------|------------------------------------------------------------------------------------|
| `400`  | Un parámetro o el cuerpo del pedido no cumple las validaciones. Si falló un campo o un parámetro, `errors` dice cuál |
| `401`  | Falta el token, está mal formado, su firma no es válida o está vencido; o el login falló |
| `403`  | El token es válido, pero su rol no permite el pedido                                |
| `404`  | El elemento pedido por `{id}` no existe, o la ruta no existe                        |
| `405`  | El método no está permitido en esa ruta                                             |
| `406`  | El pedido no acepta JSON como respuesta (cabecera `Accept`)                         |
| `409`  | El alta de un usuario cuyo `username` ya existe                                     |
| `415`  | El cuerpo del pedido no se envió como JSON (cabecera `Content-Type`)                |
| `429`  | El login, desde una dirección de origen que acumuló demasiados intentos fallidos    |
| `500`  | Error inesperado. La respuesta no incluye detalles internos                         |
| `502`  | SWAPI respondió con un error o con algo que no se puede interpretar                 |
| `504`  | SWAPI no respondió dentro del tiempo máximo de espera                               |

- Todo `401` lleva la cabecera `WWW-Authenticate: Bearer`, solo con el nombre del esquema. Cuando el motivo es el token, la respuesta es la misma si falta, está mal formado, tiene otra firma o venció ([ADR-0005](../adr/0005-authentication.md), nota del 15/11/2026). El `403` no lleva esa cabecera ([ADR-0017](../adr/0017-role-based-access.md)).
- `406` y `415` los responde la aplicación desde el MVP, en este mismo formato; se listan acá desde el ciclo 2. El `406` está en el [ADR-0008](../adr/0008-api-documentation.md) (nota del 15/11/2026). El texto del `415` es fijo desde el ciclo 2: antes repetía el tipo de contenido que había enviado el cliente ([ADR-0016](../adr/0016-validation-error-detail.md)).

Textos de `detail` de los códigos que se suman en el ciclo 2, y de los que el ciclo 2 fijó:

| Código | `detail`                                               |
|--------|--------------------------------------------------------|
| `400`, por un cuerpo que no se puede leer | `Failed to read request` |
| `403`  | `You do not have permission to perform this request.`  |
| `405`  | `The method of the request is not supported.`          |
| `409`  | `A user with that username already exists.`            |
| `415`  | `The content type of the request is not supported.`    |

El `405` tiene ese mismo texto en sus dos casos, un método que la ruta no tiene y uno que el servidor web tiene apagado, y no repite el método recibido ([ADR-0007](../adr/0007-error-format.md), nota del 15/11/2026). Cuando la ruta existe, la respuesta lleva la cabecera `Allow` con los métodos que sí tiene.

El ciclo 3 suma un código, con su texto de `detail`:

| Código | `detail`                                               |
|--------|--------------------------------------------------------|
| `429`  | `Too many failed login attempts. Try again later.`     |

El `429` lleva la cabecera `Retry-After`, con los segundos que faltan para poder volver a intentar, y no lleva `WWW-Authenticate` ([ADR-0023](../adr/0023-login-attempt-limit.md)). Solo lo responde el login.

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
| `expanded=true` y una página posterior a la última           | `200`, `content` vacío y los totales correctos                              |
| `expanded=true` con `id` de un elemento que existe           | `200`; un elemento, igual al cuerpo de su detalle                           |
| `expanded=true` con `id` de un elemento que no existe        | `200`, `content` vacío, `totalElements` 0 y `totalPages` 0                  |
| `expanded=true` con `name`                                   | `200`; las coincidencias de la página, cada una con sus datos completos     |
| `expanded=true` en Films                                     | `200`; la página se recorta en memoria, igual que sin `expanded`            |
| `expanded=true` y un elemento de la página tiene una relación cuya dirección no termina en un identificador | `502`; el elemento no se omite en silencio |
| Se repite un listado con `expanded=true` antes de que venza la caché | Responde sin volver a llamar a SWAPI                                |
| Se pide el detalle de un elemento que ya llegó en un listado con `expanded=true` | Responde sin llamar a SWAPI                             |
| Se pide el mismo listado con `expanded=true` y después sin él | Son dos pedidos a SWAPI: las dos formas se guardan por separado            |
| Un usuario con rol `USER` envía un alta con el cuerpo inválido | `403`: el rol se comprueba antes que el contenido                         |
| Alta con un `username` que solo difiere de uno existente en mayúsculas | `400`: las mayúsculas no son válidas en `username`                 |
| Alta con `role` en minúsculas (`"admin"`)                    | `400`; no se corrige                                                        |
| Alta con una contraseña de 72 caracteres o menos que ocupa más de 72 bytes | `400`, no `500`                                                |
| Alta con una contraseña de 8 espacios                        | `400`: el login no acepta una contraseña de solo espacios, y ese usuario no podría entrar |
| Alta con una contraseña con espacios adentro y en los extremos | `201`: los espacios cuentan para el largo y no se recortan; el usuario inicia sesión con ella tal cual |
| Alta con campos que no existen (`id`, `createdAt`)           | Se ignoran                                                                  |
| Dos altas del mismo `username` a la vez                      | Una responde `201` y la otra `409`; no quedan dos usuarios                  |
| El administrador crea a otro administrador                   | `201`; el usuario nuevo puede crear usuarios                          |
| SWAPI responde `502`, `503` o `504`, o corta la conexión, y al reintento responde bien | `200`; quien consume no ve la falla                     |
| SWAPI responde `429`                                         | `502`, sin reintentar: SWAPI recibe un solo pedido                          |
| SWAPI responde `500`                                         | `502`, sin reintentar                                                       |
| SWAPI no responde dentro del tiempo máximo                   | `504`, sin reintentar                                                       |
| Los reintentos están apagados (`SWAPI_MAX_RETRIES=0`)        | Toda falla de SWAPI responde como en el MVP, con un solo pedido             |
| SWAPI cierra la conexión sin enviar nada                     | El cliente HTTP repite el pedido por su cuenta, una vez, antes de informar la falla: SWAPI recibe dos pedidos por intento |
| Cinco logins fallidos desde una dirección, y un sexto con las credenciales correctas | `429`: no se comprueba la contraseña                |
| Cinco logins fallidos desde una dirección, repartidos entre usuarios distintos, existan o no | Cuentan juntos: el sexto responde `429`     |
| Un login correcto entre dos fallidos                         | No borra la cuenta: los fallos de antes y de después se suman               |
| Un login con el cuerpo inválido desde una dirección bloqueada | `400`: la forma del pedido se comprueba antes que el límite, y ese pedido no cuenta |
| Pedidos de login mientras dura el bloqueo                    | `429` en todos; no alargan el bloqueo, y `Retry-After` baja con el tiempo   |
| Vence la ventana de una dirección bloqueada                  | El login vuelve a comprobar las credenciales, y la cuenta empieza de cero   |
| Una dirección bloqueada usa un token que obtuvo antes        | `200`: el límite es solo del login                                          |
| Dos clientes detrás de una misma dirección de origen         | Comparten la cuenta: los fallos de uno cuentan para el otro                 |
| Con cuatro logins fallidos contados, llegan a la vez dos logins correctos desde esa dirección | Uno responde `200` y el otro puede responder `429`: cada intento se cuenta antes de comprobar la contraseña, y el primero ocupa el último lugar mientras la comprueba. Al reintentar, responde `200` |
| La aplicación se reinicia con direcciones bloqueadas         | Los bloqueos y las cuentas se pierden: viven en memoria                     |
| El límite está apagado (`LOGIN_MAX_FAILED_ATTEMPTS=0`)       | El login nunca responde `429`                                               |
| Sin intermediario, un cliente envía `X-Forwarded-For`        | Se ignora: cuenta la dirección de la conexión                               |
| En una instalación que lee las cabeceras de un intermediario, llega `X-Forwarded-For` con varias direcciones | Cuenta la primera de la lista |

### Tiempos y caché

| Ajuste                                  | Por defecto | Configurable |
|-----------------------------------------|-------------|--------------|
| Tiempo máximo de conexión con SWAPI     | 3 segundos  | Sí           |
| Tiempo máximo de lectura de SWAPI       | 10 segundos | Sí           |
| Vencimiento de la caché de SWAPI        | 1 hora      | Sí           |
| Cantidad máxima de respuestas en caché  | 500         | Sí           |
| Reintentos de un pedido a SWAPI         | 1           | Sí           |
| Espera antes de reintentar              | 200 milisegundos | Sí      |
| Logins fallidos antes del bloqueo       | 5           | Sí           |
| Ventana del límite de intentos de login | 15 minutos  | Sí           |

La caché guarda solo las respuestas correctas de SWAPI. Un "no existe" o un error no se guardan ([ADR-0011](../adr/0011-swapi-response-cache.md)). Las respuestas con datos completos (`expanded=true`) se guardan aparte de las de resúmenes, y cada elemento que llega en una de ellas se guarda además como si se hubiera pedido por su identificador ([ADR-0019](../adr/0019-expanded-listings.md)).

Se reintenta solo lo que suele ser pasajero y falla rápido: una respuesta `502`, `503` o `504` de SWAPI, o una conexión rechazada o cortada. No se reintenta un `429`, otro `4xx`, un `500`, un tiempo máximo vencido ni una respuesta que no se puede interpretar ([ADR-0020](../adr/0020-swapi-failure-tolerance.md)). Si el reintento también falla, la respuesta es la de esa última falla. En el peor caso, un pedido espera a SWAPI dos veces el tiempo máximo de lectura más la espera entre intentos.

Los pedidos que recibe SWAPI pueden ser más que los intentos que cuenta esta regla. El cliente HTTP de Java repite por su cuenta, una vez y en el momento, un pedido cuya conexión se cerró antes de recibir nada. Si SWAPI cierra así todas las conexiones, recibe cuatro pedidos con un reintento y dos con los reintentos apagados; ante una respuesta `502`, `503` o `504` no repite nada. No se desactiva ([ADR-0020](../adr/0020-swapi-failure-tolerance.md), nota del 15/11/2026).

### Configuración

| Variable de entorno      | Para qué                                              | Obligatoria                       |
|--------------------------|-------------------------------------------------------|-----------------------------------|
| `JWT_SECRET`             | Clave de firma de los tokens, de al menos 32 caracteres | Sí. Sin ella la aplicación no arranca |
| `JWT_TTL_MINUTES`        | Vencimiento del token                                 | No. Por defecto, 60               |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión con PostgreSQL                     | Sí                                |
| `SEED_ENABLED`           | Cargar los usuarios de prueba                         | No. Por defecto, apagado; encendido en `docker compose` |
| `SWAPI_BASE_URL`         | Dirección de SWAPI                                    | No. Por defecto, `https://www.swapi.tech/api` |
| `SWAPI_CONNECT_TIMEOUT`  | Tiempo máximo de conexión con SWAPI                   | No. Por defecto, `3s`             |
| `SWAPI_READ_TIMEOUT`     | Tiempo máximo de lectura de SWAPI                     | No. Por defecto, `10s`            |
| `SWAPI_CACHE_TTL`        | Vencimiento de la caché de SWAPI                      | No. Por defecto, `1h`             |
| `SWAPI_CACHE_MAX_SIZE`   | Cantidad máxima de respuestas en caché                | No. Por defecto, `500`            |
| `SWAPI_MAX_RETRIES`      | Cuántas veces se reintenta un pedido a SWAPI que falló | No. Por defecto, `1`. De `0` a `3`; con `0` no se reintenta |
| `SWAPI_RETRY_DELAY`      | Espera antes de cada reintento                        | No. Por defecto, `200ms`. Vale `0s`: se reintenta en el momento |
| `LOGIN_MAX_FAILED_ATTEMPTS` | Cuántos logins fallidos desde una dirección de origen bloquean su login | No. Por defecto, `5`. De `0` a `100`; con `0` el límite se apaga |
| `LOGIN_FAILED_ATTEMPTS_WINDOW` | Cuánto dura la ventana en la que se cuentan esos fallos, y con ella el bloqueo | No. Por defecto, `15m`. Mayor que cero y hasta `24h` |

Un valor de `SWAPI_MAX_RETRIES` o de `SWAPI_RETRY_DELAY` fuera de rango, vacío o negativo impide arrancar, y el mensaje dice qué variable corregir, igual que los demás ajustes de SWAPI.

Lo mismo vale para las dos variables del límite de intentos de login: un valor fuera de rango, vacío o que no se puede leer impide arrancar, y el mensaje dice qué variable corregir. La ventana se valida también con el límite apagado.

> **Nota del 15/11/2026.** Precisión que apareció al implementar los reintentos (ticket #38): el rango de `SWAPI_RETRY_DELAY` no estaba escrito. Vale cualquier tiempo que no sea negativo, incluido `0s`, que reintenta en el momento, y no tiene tope; vacío o negativo impide arrancar. A diferencia de los tiempos máximos, donde cero significaría "esperar para siempre", acá cero tiene un significado razonable. La precisión la autorizó la sesión supervisora que había escrito la extensión; las alternativas están en el [ADR-0020](../adr/0020-swapi-failure-tolerance.md), nota de esta fecha. Ningún criterio de aceptación cambió.

> **Nota del 15/11/2026.** Precisiones de la revisión final del ciclo 2, que probó la aplicación ya fusionada. Tres dicen algo que la aplicación ya hacía y este documento no: el `400` de un cuerpo que no se puede leer y su texto, lo que pasa con un campo de otro tipo, y los pedidos que el cliente HTTP repite por su cuenta. Una cambia el comportamiento: el `405` de una ruta que no tiene ese método pasó a tener el mismo texto fijo que el de un método apagado, en vez de repetir el método recibido. Las autorizó la sesión supervisora que había escrito la extensión. Ningún criterio de aceptación cambió; el test de CA-24.2 se corrigió, porque comparaba el elemento del listado con el mismo objeto que el listado había dejado guardado.

> **Nota del 15/11/2026.** Precisiones que aparecieron al implementar el límite de intentos de login (ticket #50). Una corrige la redacción de un criterio: CA-29.18 decía que el cuerpo del `429` es igual al ejemplo de la documentación "salvo `instance`, que el ejemplo no trae", pero ese ejemplo sí lo trae, porque la documentación se lo completa a cada error con la dirección de su pedido; ahora dice que el cuerpo es igual al ejemplo, y el test lo compara entero. La otra suma un caso límite: dos logins correctos que llegan a la vez a una dirección con cuatro fallos contados. Las autorizó la sesión supervisora que había escrito la extensión; el detalle está en el [ADR-0023](../adr/0023-login-attempt-limit.md), nota de esta fecha. Ningún otro criterio de aceptación cambió.

## 6. Criterios de aceptación

Cada criterio se identifica como `CA-NN.M`: `NN` es el número del requisito y `M` el número de criterio. Todos suponen la aplicación levantada, SWAPI simulada con respuestas reales grabadas y, salvo que se indique otra cosa, una sesión iniciada con el usuario de prueba `demo`.

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

> **Nota del 15/11/2026.** CA-19.1 se comprueba en dos partes desde el ciclo 2: su test de aceptación pide el control de estado con la aplicación levantada por el test, y la verificación automática ejecuta `docker compose up` y hace los dos pedidos (CA-23.2). El criterio no cambió.

### Errores de validación (REQ-21)

| Criterio | Dado                                        | Cuando                                              | Entonces                                                                    |
|----------|---------------------------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------|
| CA-21.1  | Sin sesión iniciada                         | Hace login con `username` vacío y una contraseña válida | `400`; `detail` es `Invalid request content.` y `errors` tiene un solo elemento, con `field` "username" y su texto fijo |
| CA-21.2  | Sin sesión iniciada                         | Hace login sin `username` y sin `password`          | `400`; `errors` tiene dos elementos, `password` y `username`, en ese orden   |
| CA-21.3  | Sin sesión iniciada                         | Hace login con un `username` de 51 caracteres       | `400`; `errors` nombra a `username`, y la respuesta no contiene el valor enviado |
| CA-21.4  | —                                           | Pide `GET /people?size=101`                         | `400`; `detail` es el de CA-05.4 y `errors` tiene un elemento: `field` "size", `message` "must be an integer between 1 and 100" |
| CA-21.5  | —                                           | Pide `GET /people?page=0&size=0`                    | `400`; `errors` tiene un solo elemento, el de `page`                         |
| CA-21.6  | —                                           | Pide `GET /people/abc`                              | `400`; `errors` tiene un elemento, con `field` "id"                          |
| CA-21.7  | Sin sesión iniciada                         | Hace login con un cuerpo que no es JSON             | `400`, en Problem Details y sin el campo `errors`                            |
| CA-21.8  | Sin sesión iniciada                         | Hace login con `Content-Type: text/plain`           | `415`, en Problem Details; `detail` es el texto fijo y no contiene `text/plain` |
| CA-21.9  | La aplicación levantada                     | Se pide `GET /v3/api-docs`                          | El `400` del login y el de los listados declaran el campo `errors`, con `field` y `message` |

### Roles (REQ-25)

| Criterio | Dado                                        | Cuando                                              | Entonces                                                                    |
|----------|---------------------------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------|
| CA-25.1  | Una base de datos vacía y el *seed* activado | Arranca la aplicación                              | Existen `demo`, con rol `USER`, y `admin`, con rol `ADMIN`; las dos contraseñas están cifradas con BCrypt |
| CA-25.2  | Existen los dos usuarios de prueba          | Cada uno hace login con su contraseña               | `200` en los dos; el token de `demo` lleva `role` "USER" y el de `admin`, `role` "ADMIN" |
| CA-25.3  | Una sesión iniciada como `admin`            | Pide cada uno de los cuatro listados y un detalle   | `200` en todos: los dos roles pueden usar los listados                       |
| CA-25.4  | La aplicación ya arrancó una vez            | Se reinicia                                         | No falla ni duplica a ninguno de los dos usuarios de prueba                  |
| CA-25.5  | Una base de datos donde ya existe `demo` y no existe `admin` | Arranca la aplicación con el *seed* activado | Se crea `admin`; la fila de `demo` no cambia                    |
| CA-25.6  | Una sesión iniciada como `demo`             | Pide `POST /users` con un cuerpo válido             | `403`, en Problem Details, con el texto fijo y sin la cabecera `WWW-Authenticate`; no se crea ningún usuario |
| CA-25.7  | Una sesión iniciada como `demo`             | Pide `POST /users` con un cuerpo inválido           | `403`, no `400`: el rol se comprueba antes que el contenido                  |
| CA-25.8  | Sin sesión iniciada, o con un token vencido | Pide `POST /users`                                  | `401`, idéntico al de CA-10.1                                                |

### Alta de usuarios (REQ-26)

Salvo que se indique otra cosa, con una sesión iniciada como `admin`.

| Criterio | Dado                                        | Cuando                                              | Entonces                                                                    |
|----------|---------------------------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------|
| CA-26.1  | No existe el usuario "leia"                 | Pide `POST /users` con `username` "leia" y una contraseña válida, sin `role` | `201`; el cuerpo tiene exactamente `username` "leia", `role` "USER" y `createdAt`; no hay cabecera `Location` |
| CA-26.2  | Se creó "leia" con CA-26.1                  | "leia" hace login con esa contraseña y pide `GET /people` | `200` en los dos pedidos; su token lleva `role` "USER"                |
| CA-26.3  | Se creó "leia" con CA-26.1                  | Se consulta su fila en la base de datos             | La contraseña está cifrada con BCrypt; el texto original no aparece; `role` es `USER` |
| CA-26.4  | No existe el usuario "mon.mothma"           | Pide `POST /users` con `role` "ADMIN"               | `201` con `role` "ADMIN"; ese usuario inicia sesión y su `POST /users` responde `201` |
| CA-26.5  | Existe el usuario `demo`                    | Pide `POST /users` con `username` "demo"            | `409`, en Problem Details, con el texto fijo; sigue habiendo un solo `demo`, con su contraseña de antes |
| CA-26.6  | —                                           | Pide `POST /users` con `username` "ab", "Leia" o "leia organa" | `400`; `errors` tiene un elemento, con `field` "username" y su texto fijo; no se crea ningún usuario |
| CA-26.7  | —                                           | Pide `POST /users` con una contraseña de 7 caracteres, y con una de 73 | `400` en los dos; `errors` nombra a `password`; la respuesta no contiene la contraseña enviada |
| CA-26.8  | —                                           | Pide `POST /users` con una contraseña de 40 veces "é" (40 caracteres, 80 bytes) | `400`; `errors` nombra a `password`                   |
| CA-26.9  | —                                           | Pide `POST /users` con `role` "admin" o "ROOT"      | `400`; `errors` tiene un elemento, con `field` "role"                        |
| CA-26.10 | —                                           | Pide `POST /users` con los tres campos inválidos    | `400`; `errors` tiene tres elementos, en el orden `password`, `role`, `username` |
| CA-26.11 | —                                           | Pide `POST /users` con una contraseña de exactamente 8 caracteres y otra de 72 | `201` en los dos; cada usuario inicia sesión con su contraseña |
| CA-26.12 | La aplicación levantada                     | Se pide `GET /v3/api-docs`                          | Incluye `POST /api/v1/users`, con el nombre `createUser`, con candado y con sus respuestas `201`, `400`, `401`, `403` y `409`. La documentación tiene exactamente diez pedidos |

### Datos completos a pedido (REQ-24)

| Criterio | Dado                                        | Cuando                                              | Entonces                                                                    |
|----------|---------------------------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------|
| CA-24.1  | SWAPI tiene 82 personajes                   | Pide `GET /people?expanded=true&size=2`             | `200`; `page` 1, `size` 2, `totalElements` 82, `totalPages` 41 y dos elementos, cada uno con exactamente los campos de la tabla de People |
| CA-24.2  | El personaje 1 es Luke Skywalker            | Pide `GET /people?expanded=true&size=2`             | El primer elemento es igual, campo por campo, al cuerpo de `GET /people/1`   |
| CA-24.3  | Cualquiera de las cuatro entidades          | Pide el listado con `expanded=false`                | El mismo resultado que sin el parámetro: cada elemento tiene exactamente `id` y `name` |
| CA-24.4  | SWAPI tiene 6 películas y no las pagina     | Pide `GET /films?expanded=true&page=2&size=2`       | `200`; `totalElements` 6, `totalPages` 3 y las películas 3.ª y 4.ª, cada una con los campos de la tabla de Films: `title`, sin `name`, y `episodeId` como número |
| CA-24.5  | SWAPI tiene naves y vehículos               | Pide `GET /starships?expanded=true&size=2` y `GET /vehicles?expanded=true&size=2` | `200` en los dos; cada elemento tiene exactamente los campos de la tabla de su entidad |
| CA-24.6  | Hay 3 personajes cuyo nombre contiene "sky" | Pide `GET /people?name=sky&expanded=true&size=2`    | `200`; `totalElements` 3, `totalPages` 2 y dos elementos con sus datos completos |
| CA-24.7  | Existe el personaje 1 y no existe el 9999   | Pide `GET /people?id=1&expanded=true` y `GET /people?id=9999&expanded=true` | El primero: un elemento, igual al cuerpo de `GET /people/1`. El segundo: `content` vacío y `totalElements` 0 |
| CA-24.8  | El personaje 1 es Luke Skywalker            | Pide `GET /people?id=1&name=vader&expanded=true`    | `200`; `content` vacío y `totalElements` 0                                   |
| CA-24.9  | SWAPI tiene 82 personajes                   | Pide `GET /people?page=50&expanded=true`            | `200`; `content` vacío, `totalElements` 82 y `totalPages` 9                  |
| CA-24.10 | —                                           | Pide `expanded=1`, `expanded=TRUE` o `expanded=` vacío | `400`; el detalle nombra el parámetro, y SWAPI no recibe ningún pedido     |
| CA-24.11 | La caché vacía                              | Se pide dos veces `GET /people?expanded=true&size=2` | SWAPI recibe un solo pedido, y ese pedido lleva `expanded=true`             |
| CA-24.12 | La caché vacía                              | Se pide `GET /people?expanded=true&size=2` y después `GET /people/1` | SWAPI recibe un solo pedido: el detalle sale de lo que trajo el listado |
| CA-24.13 | La caché vacía                              | Se pide `GET /people?expanded=true&size=2` y después `GET /people?size=2` | SWAPI recibe dos pedidos, uno con `expanded=true` y otro sin él |
| CA-24.14 | SWAPI entrega una página completa donde un personaje tiene una relación cuya dirección no termina en un identificador | Pide `GET /people?expanded=true&size=2` | `502`, sin detalles internos |
| CA-24.15 | La aplicación levantada                     | Se pide `GET /v3/api-docs`                          | Los cuatro listados declaran `expanded` como booleano, con `false` por defecto, y su respuesta `200` documenta las dos formas de un elemento: el resumen y el detalle de la entidad |

### Tolerancia a fallas de SWAPI (REQ-22)

Con los reintentos en su valor por defecto: uno.

| Criterio | Dado                                        | Cuando                                              | Entonces                                                                    |
|----------|---------------------------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------|
| CA-22.1  | SWAPI responde `503` a un pedido y bien al siguiente | Pide `GET /people`                         | `200`. SWAPI recibe dos pedidos                                              |
| CA-22.2  | SWAPI responde siempre `503`                | Pide `GET /people`                                  | `502`, sin detalles internos. SWAPI recibe exactamente dos pedidos           |
| CA-22.3  | SWAPI corta la conexión en un pedido y responde bien al siguiente | Pide `GET /people/1`          | `200`. SWAPI recibe dos pedidos                                              |
| CA-22.4  | SWAPI responde `429`                        | Pide `GET /people`                                  | `502`. SWAPI recibe un solo pedido                                           |
| CA-22.5  | SWAPI tarda más que el tiempo máximo de lectura | Pide `GET /people`                              | `504`. SWAPI recibe un solo pedido                                           |
| CA-22.6  | No existe el personaje 9999                 | Pide `GET /people/9999`                             | `404`. SWAPI recibe un solo pedido                                           |
| CA-22.7  | SWAPI responde `502` a un pedido y bien al siguiente | Se pide dos veces `GET /people`            | `200` las dos veces. SWAPI recibe dos pedidos en total: la respuesta que llegó en el reintento quedó en la caché |
| CA-22.8  | `SWAPI_MAX_RETRIES=0` y SWAPI responde `503` a un pedido y bien al siguiente | Pide `GET /people` | `502`. SWAPI recibe un solo pedido                                  |

Las fallas de estos criterios no se graban: se arman en el test, sobre la SWAPI simulada, como en CA-16.2 y CA-16.3. Que el login no depende de SWAPI ya lo comprueba CA-16.4, y que un `500` responde `502` con un solo pedido, CA-16.2 y CA-32.4, que no cambian.

### Límite de intentos de login (REQ-29)

Sin sesión iniciada. Con el límite en sus valores por defecto, cinco fallos en quince minutos, salvo que se indique otra cosa. Los pedidos de cada criterio salen de una misma dirección de origen, que empieza sin fallos contados. Ningún test espera quince minutos: el límite lee la hora de un reloj de prueba, que solo avanza cuando el test lo indica, como en CA-32.3. "Un login fallido" es uno con el usuario `demo` y una contraseña incorrecta.

| Criterio | Dado                                        | Cuando                                              | Entonces                                                                    |
|----------|---------------------------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------|
| CA-29.1  | Una dirección sin fallos contados           | Hace cinco logins fallidos seguidos                 | `401` en los cinco, cada uno idéntico al de CA-09.2                          |
| CA-29.2  | Una dirección con cinco logins fallidos     | Hace otro login fallido                             | `429`, en Problem Details, con el texto fijo; lleva la cabecera `Retry-After`, con un número entero entre 1 y 900, y no lleva `WWW-Authenticate` |
| CA-29.3  | Una dirección con cinco logins fallidos     | Hace login como `demo` con su contraseña correcta   | `429`, con el mismo cuerpo que el de CA-29.2: no se entrega ningún token     |
| CA-29.4  | Una dirección con cuatro logins fallidos    | Hace un login correcto, después uno fallido y después otro correcto | `200`, `401` y `429`, en ese orden: el login correcto no borró la cuenta |
| CA-29.5  | Una dirección con dos logins fallidos como `demo`, dos como `admin` y uno como "nadie", que no existe | Hace login como `demo` con su contraseña correcta | `429`: los fallos se cuentan por dirección, no por usuario |
| CA-29.6  | Una dirección sin fallos contados           | Hace seis logins sin `password` y después uno correcto | `400` en los seis y `200` en el último: un `400` no cuenta como fallo      |
| CA-29.7  | Una dirección con cinco logins fallidos     | Hace un login sin `password`                        | `400`, con `errors`, igual que sin el bloqueo: la forma del pedido se comprueba antes que el límite |
| CA-29.8  | Una dirección con cinco logins fallidos     | Pasan quince minutos desde el primero y hace un login correcto | `200`                                                             |
| CA-29.9  | Una dirección que se bloqueó y cuya ventana ya venció | Hace cuatro logins fallidos y después uno correcto | `401` en los cuatro y `200` en el último: la cuenta volvió a cero   |
| CA-29.10 | Una dirección con un login fallido; diez minutos después, cuatro más | Hace un login en ese momento, y otro correcto cinco minutos después | El primero, `429` con `Retry-After` de 300 o menos; el segundo, `200`: la ventana empezó con el primer fallo |
| CA-29.11 | Una dirección con cinco logins fallidos, y catorce minutos después un login más, que responde `429` | Pasa un minuto más y hace un login correcto | `200`: un pedido bloqueado no alarga la ventana |
| CA-29.12 | Una sesión iniciada como `demo`, y después cinco logins fallidos desde la misma dirección | Pide `GET /people` con el token de antes, y el control de estado sin token | `200` en los dos: el límite es solo del login |
| CA-29.13 | `LOGIN_MAX_FAILED_ATTEMPTS=0`               | Hace diez logins fallidos y después uno correcto    | `401` en los diez y `200` en el último: el límite está apagado               |
| CA-29.14 | `LOGIN_MAX_FAILED_ATTEMPTS=2` y `LOGIN_FAILED_ATTEMPTS_WINDOW=1m` | Hace tres logins fallidos; pasa un minuto y hace uno correcto | `401`, `401` y `429` con `Retry-After` de 60 o menos; después, `200` |
| CA-29.15 | La aplicación como se levanta en local, sin leer las cabeceras de un intermediario, y cinco logins fallidos con `X-Forwarded-For: 203.0.113.7` | Hace un login correcto con `X-Forwarded-For: 203.0.113.8` | `429`: la cabecera se ignora, y todos los pedidos cuentan para la dirección de la conexión |
| CA-29.16 | La aplicación configurada como en el despliegue público, y cinco logins fallidos con `X-Forwarded-For: 203.0.113.7` | Hace un login correcto con `X-Forwarded-For: 203.0.113.8` y otro con `X-Forwarded-For: 203.0.113.7` | `200` el primero y `429` el segundo: cada dirección informada tiene su cuenta |
| CA-29.17 | La aplicación levantada                     | Se pide `GET /v3/api-docs`                          | El login declara las respuestas `200`, `400`, `401` y `429`. La `429` usa el esquema de error común, declara la cabecera `Retry-After` y trae un ejemplo. La documentación sigue teniendo exactamente diez pedidos |
| CA-29.18 | Una dirección con cinco logins fallidos     | Hace otro login, y se compara la respuesta con el ejemplo del `429` de `GET /v3/api-docs` | El cuerpo es igual al ejemplo |

Los criterios CA-09.x, CA-16.4 y CA-21.x no cambian: entre todos sus tests hay menos de cinco logins fallidos por aplicación levantada. Que un pedido bloqueado no consulta la base de datos ni compara la contraseña, que los intentos simultáneos no pasan del umbral, el recorte de la dirección y la validación de las dos variables se comprueban con tests unitarios.

### Colección de pedidos (REQ-28)

Los tests leen el archivo de la colección, `docs/api/star-wars-api.postman_collection.json`, y, donde se indica, la documentación de la aplicación levantada. Ningún test ejecuta los pedidos de la colección: no hace falta SWAPI. Un "pedido de login" es un `POST` a `/api/v1/auth/login`.

| Criterio | Dado                                        | Cuando                                              | Entonces                                                                    |
|----------|---------------------------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------|
| CA-28.1  | El archivo de la colección                  | Se lee                                              | Es JSON válido y declara el formato de colección de Postman 2.1. Tiene exactamente dos variables: `baseUrl`, con el valor `http://localhost:8080`, y `token`, vacía |
| CA-28.2  | La aplicación levantada                     | Se compara `GET /v3/api-docs` con la colección      | Cada pedido documentado, por su método y su ruta, tiene al menos un pedido en la colección: los diez |
| CA-28.3  | La aplicación levantada                     | Se compara cada pedido de la colección con `GET /v3/api-docs` | Su dirección empieza con `{{baseUrl}}`; su método y su ruta son los de un pedido documentado, donde un tramo como `1` ocupa el lugar de `{id}`; y cada parámetro que envía en la dirección está declarado en ese pedido |
| CA-28.4  | El archivo de la colección                  | Se lee cómo se autentica cada pedido                | La colección declara, una sola vez, autenticación Bearer con `{{token}}`. Los pedidos de login y un `GET` a un listado, el que muestra el `401`, se declaran sin autenticación. Ningún otro pedido declara la suya, y ninguno escribe la cabecera `Authorization` |
| CA-28.5  | El archivo de la colección                  | Se leen los pedidos de login                        | Son dos: uno con `demo` y `demo1234` y otro con `admin` y `admin1234`, las credenciales de prueba publicadas. Cada uno tiene un guion que guarda el `accessToken` de la respuesta en la variable `token` |
| CA-28.6  | El archivo de la colección                  | Se recorren sus pedidos en orden                    | El primero es el login de `demo`. Hay un `POST /api/v1/users` antes del login de `admin`, el que muestra el `403`, y otro después, el alta |
| CA-28.7  | El archivo de la colección                  | Se buscan los pedidos de cada una de las cuatro entidades | Cada una tiene, al menos, un listado con `page` y `size`, uno con `id`, uno con `name`, uno con `expanded=true` y un detalle |
| CA-28.8  | El archivo de la colección                  | Se leen los pedidos que llevan cuerpo               | Cada cuerpo es JSON válido y se envía con `Content-Type: application/json`   |

Que la colección, ejecutada, responde lo esperado se verifica por revisión: ver la tabla de más abajo.

### Despliegue público y verificación automática (REQ-20 y REQ-23)

| Criterio | Dado                                        | Cuando                                              | Entonces                                                                    |
|----------|---------------------------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------|
| CA-20.1  | La aplicación configurada como en el despliegue público, detrás de un intermediario que atiende HTTPS | Se pide `GET /v3/api-docs` con las cabeceras que agrega ese intermediario (`X-Forwarded-Proto: https` y `X-Forwarded-Host`) | La dirección del servidor que declara la documentación empieza con `https://` y usa ese nombre: Swagger UI envía sus pedidos por HTTPS |

Los demás criterios de estos dos requisitos se verifican por revisión: ver la tabla siguiente.

### Requisitos que se verifican por revisión

No tienen test de aceptación: se comprueban al revisar cada pull request y al cerrar el ciclo.

| Requisito | Qué se comprueba                                                                                              |
|-----------|---------------------------------------------------------------------------------------------------------------|
| REQ-13    | El README explica cómo levantar el proyecto, las credenciales de prueba, ejemplos de pedidos y cómo correr los tests |
| REQ-14    | Las clases e interfaces públicas y los métodos no obvios tienen Javadoc                                        |
| REQ-15    | Hay tests unitarios de la paginación en memoria, los filtros y la traducción de SWAPI; corren sin red ni Docker |
| REQ-17    | Cada decisión técnica nueva tiene su ADR                                                                      |
| REQ-18    | El registro de uso de herramientas de IA está al día                                                          |
| REQ-20    | **CA-20.2:** existe una URL pública, y el README la muestra en su primera pantalla, con el motivo de la elección del servicio y el aviso de cuánto puede tardar el primer pedido. **CA-20.3:** desde esa URL, sin instalar nada, el control de estado responde `UP`, `demo` y `admin` inician sesión, y desde Swagger UI se puede iniciar sesión, cargar el token y listar las cuatro entidades. **CA-20.4:** el despliegue está descripto en un archivo del repositorio, sin ningún secreto. **CA-20.5:** un cambio fusionado en `main` queda desplegado sin pasos manuales |
| REQ-23    | **CA-23.1:** cada pull request y cada *push* a `main` ejecutan `./mvnw verify` sobre un JDK 21, y el resultado se ve en el pull request. **CA-23.2:** la misma ejecución levanta `docker compose up --build --wait` y comprueba el control de estado, el login de `demo` y que un listado sin token responde `401`, sin llamar a SWAPI. **CA-23.3:** el README muestra el distintivo con el estado de `main`. **CA-23.4:** una ejecución con un test roto se ve en rojo (se comprueba una vez, en el pull request que suma la verificación) |
| REQ-27    | **CA-27.1:** el README muestra, en su primera pantalla y junto al distintivo de la verificación automática, un distintivo con la cobertura de líneas como porcentaje entero, y su sección "Tests" dice qué mide ese número y dónde está el informe completo. **CA-27.2:** después de `./mvnw verify`, `bash scripts/check-coverage.sh` imprime el porcentaje medido, con las líneas cubiertas y las totales, y termina bien si el número del README es el medido, redondeado hacia abajo a un entero, y no baja del mínimo de 95 %. **CA-27.3:** con otro número en el README, termina con error, y el mensaje dice el número escrito, el medido y que hay que corregir el README (se comprueba una vez, en el pull request que lo suma: un commit con el número cambiado deja la ejecución en rojo, y el siguiente lo revierte). **CA-27.4:** con la cobertura por debajo del mínimo, termina con error, y el mensaje dice el porcentaje medido y el mínimo (se comprueba una vez, en desarrollo, subiendo el mínimo del guion por encima de lo medido). **CA-27.5:** sin el informe, porque no se corrió `./mvnw verify`, termina con error y dice qué comando correr. **CA-27.6:** cada ejecución de la verificación automática corre el guion después de los tests y deja el porcentaje a la vista en su resumen; la definición sigue pidiendo solo permiso de lectura y no usa ningún secreto |
| REQ-28    | **CA-28.9:** la colección se ejecuta una vez, completa y en orden, contra la aplicación levantada con `docker compose`: los logins, los listados, los detalles y el alta responden `200` o `201`, y los cuatro pedidos que muestran un error responden `401`, `403`, `400` con `errors` y `404`. El resultado queda escrito en el pull request que la suma. **CA-28.10:** el README tiene una sección, enlazada desde el inicio rápido, que dice cómo importar la colección, cómo apuntarla a otra dirección y qué hacer en una herramienta que no ejecuta el guion que guarda el token |

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
| REQ-20               | Sección 6                           | CA-20.1; CA-20.2 a CA-20.5, por revisión |
| REQ-21               | Sección 5                           | CA-21.1 a CA-21.9        |
| REQ-22               | Sección 5                           | CA-22.1 a CA-22.8        |
| REQ-23               | Sección 6, por revisión             | CA-23.1 a CA-23.4        |
| REQ-24               | Secciones 3 y 5                     | CA-24.1 a CA-24.15       |
| REQ-25               | Secciones 1, 2 y 5                  | CA-25.1 a CA-25.8        |
| REQ-26               | Secciones 2 y 5                     | CA-26.1 a CA-26.12       |
| REQ-27               | Sección 6, por revisión             | CA-27.1 a CA-27.6        |
| REQ-28               | Sección 6                           | CA-28.1 a CA-28.8; CA-28.9 y CA-28.10, por revisión |
| REQ-29               | Secciones 2 y 5                     | CA-29.1 a CA-29.18       |
