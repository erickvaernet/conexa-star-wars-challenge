# Conexa · Back-end Java Challenge — Star Wars API

API REST en **Java 21** que se integra con [SWAPI](https://www.swapi.tech/documentation) para listar **People, Films, Starships y Vehicles** de forma paginada, con filtrado por ID y/o nombre y autenticación segura.

> **Estado:** el ciclo 1 (MVP) está cerrado. La versión es la `v0.1.0`, con 793 tests.

## Alcance

- [x] Integración con la API de Star Wars (People, Films, Starships, Vehicles)
- [x] Listados paginados
- [x] Filtrado por ID y/o nombre
- [x] Login seguro con autenticación y autorización para acceder a los listados (usuarios en base de datos + seed inicial)
- [x] Documentación de la API con Swagger / OpenAPI
- [x] Pruebas unitarias y de integración
- [x] Entorno local dockerizado
- [ ] Despliegue en un servidor gratuito

## Inicio rápido

Hace falta tener Docker. No hay que instalar Java ni configurar nada.

**1. Levantar la aplicación y su base de datos**

```bash
docker compose up --build
```

Cuando termina de arrancar:

| Qué | Dónde |
|---|---|
| Documentación interactiva (Swagger UI) | <http://localhost:8080/swagger-ui.html> |
| La misma documentación, en formato OpenAPI | <http://localhost:8080/v3/api-docs> |
| Control de estado | <http://localhost:8080/actuator/health> |

**2. Iniciar sesión con el usuario de prueba**

`docker compose up` lo carga solo:

| Usuario | Contraseña |
|---|---|
| `demo` | `demo1234` |

Es una credencial de prueba, pensada para quien corrige: en la base de datos solo está su *hash* BCrypt.

**3. Probar**

Desde el navegador, con [Swagger UI](#probar-desde-swagger-ui), o desde la terminal, con [`curl`](#probar-con-curl). Están el login, la protección del acceso y las cuatro entidades, People, Films, Starships y Vehicles, cada una con su listado paginado, sus filtros por ID y por nombre y su detalle, con la caché de las respuestas de SWAPI. Todo se puede probar desde la documentación interactiva.

**4. Correr los tests**

Hace falta además un JDK 21 o posterior. Maven no se instala: se usa el *wrapper* (`./mvnw`).

| Para | Comando | Necesita |
|---|---|---|
| Tests unitarios y de arquitectura | `./mvnw test` | Ni red ni Docker |
| Todos los tests, incluidos los de aceptación | `./mvnw verify` | Docker |

Más detalle, en [Tests](#tests).

**Para apagar** y borrar los datos locales:

```bash
docker compose down -v
```

## Probar desde Swagger UI

No hace falta ninguna otra herramienta: alcanza con el navegador.

1. Abrir <http://localhost:8080/swagger-ui.html>. El primer grupo es **Authentication**, con el login.
2. Desplegar `POST /api/v1/auth/login`, pulsar **Try it out** y después **Execute**. El cuerpo ya trae al usuario de prueba, así que no hay que escribir nada.
3. Copiar el valor de `accessToken` de la respuesta, sin las comillas.
4. Pulsar **Authorize**, arriba de la lista de pedidos, pegar el token en `Value`, confirmar con **Authorize** y cerrar el diálogo con **Close**. Va solo el token: la palabra `Bearer` la agrega la página.
5. Probar cualquier pedido. Por ejemplo, `GET /api/v1/people` con `name` igual a `sky`, o `GET /api/v1/films/{id}` con `id` igual a `1`.

- Los pedidos con candado son los que exigen el token: los cuatro listados y los cuatro detalles. El login no lo lleva.
- El token vence a los 60 minutos. Desde ese momento los pedidos responden `401`: se repite el login y se carga el token nuevo. El login no envía el token cargado, así que se puede repetir aunque ese token ya no sirva.
- **Logout**, en el mismo diálogo de **Authorize**, quita el token de la página.
- Cada pedido muestra sus parámetros, su respuesta y sus errores.

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

### Usar el token

Todos los pedidos necesitan el token, salvo el login, la documentación y el control de estado. Se envía en la cabecera `Authorization`:

```bash
curl http://localhost:8080/api/v1/people \
  -H "Authorization: Bearer eyJ..."
```

Sin token, o con uno mal formado, con la firma alterada o vencido, la respuesta es `401`, siempre la misma.

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

### Reglas que valen para las cuatro entidades

**Páginas y parámetros**

- Una página posterior a la última devuelve `content` vacío, con los totales correctos.
- Un valor inválido responde `400` y el `detail` dice qué parámetro corregir: `Parameter 'size' must be an integer between 1 and 100.`
- Un ID con otro formato (`0`, `01`, `abc`) responde `400`, en el filtro y en el detalle, sin consultar a SWAPI: `Parameter 'id' must be a number of 1 to 10 digits, without leading zeros.`
- Un `name` vacío, solo de espacios o de más de 100 caracteres responde `400`, sin consultar a SWAPI: `Parameter 'name' must have 1 to 100 characters, not counting leading and trailing spaces.`
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
- Las respuestas de SWAPI se guardan en memoria durante una hora: un pedido repetido responde sin volver a SWAPI, que demora cada vez más a quien le hace muchos pedidos. Un error no se guarda. El porqué y los detalles están en el [ADR-0011](docs/adr/0011-swapi-response-cache.md).
- El filtro por ID y el detalle del mismo elemento comparten la respuesta guardada: son un solo pedido a SWAPI. Un "no existe" no se guarda.
- SWAPI responde una búsqueda con todas las coincidencias juntas. La aplicación recorta la página pedida y guarda la respuesta completa: recorrer las páginas de una búsqueda es un solo pedido a SWAPI.
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

## Configuración

No hace falta configurar nada: `docker compose` trae valores de desarrollo. Para cambiarlos, copiar `.env.example` a `.env`.

| Variable | Para qué | En `docker compose` | Sin `docker compose` |
|---|---|---|---|
| `DB_URL` | Dirección de la base de datos PostgreSQL | Fija: la base que levanta `docker compose` | Obligatoria |
| `DB_USER` | Usuario de la base de datos | `starwars` | Obligatoria |
| `DB_PASSWORD` | Contraseña de la base de datos | `starwars` | Obligatoria |
| `JWT_SECRET` | Clave que firma los tokens, de al menos 32 caracteres | Una clave de desarrollo, pública | Obligatoria: sin ella la aplicación no arranca |
| `JWT_TTL_MINUTES` | Minutos hasta que vence un token | `60` | `60` |
| `SEED_ENABLED` | Cargar el usuario de prueba | `true` | `false` |
| `SWAPI_BASE_URL` | Dirección de SWAPI | `https://www.swapi.tech/api` | `https://www.swapi.tech/api` |
| `SWAPI_CONNECT_TIMEOUT` | Tiempo máximo para conectarse con SWAPI | `3s` | `3s` |
| `SWAPI_READ_TIMEOUT` | Tiempo máximo de espera de una respuesta de SWAPI | `10s` | `10s` |
| `SWAPI_CACHE_TTL` | Tiempo que se guarda una respuesta de SWAPI | `1h` | `1h` |
| `SWAPI_CACHE_MAX_SIZE` | Cantidad máxima de respuestas de SWAPI guardadas | `500` | `500` |
| `APP_PORT` | Puerto de esta máquina donde se publica la aplicación. La usa `docker compose`, no la aplicación | `8080` | No se usa |

Los tiempos se escriben como un número con su unidad: `500ms`, `3s`, `1h`.

## Tests

Hace falta un JDK 21 o posterior. Maven no se instala: se usa el *wrapper* (`./mvnw`).

| Para | Comando | Necesita |
|---|---|---|
| Tests unitarios y de arquitectura | `./mvnw test` | Ni red ni Docker |
| Todos los tests, incluidos los de aceptación | `./mvnw verify` | Docker |

- Son 793 tests: 648 corren con `./mvnw test` y 145 son de aceptación, que son las clases terminadas en `AcceptanceTest`.
- Los de aceptación levantan la aplicación completa, con un PostgreSQL real, y le hacen pedidos por HTTP. Salen de los criterios de aceptación de la [especificación técnica](docs/spec/SPEC.md#6-criterios-de-aceptación) y, los más recientes, de los hallazgos de la revisión final. Se escribieron antes que el código.
- Ningún test llama a la SWAPI real: usan respuestas reales grabadas, servidas por una SWAPI simulada. Corren aunque SWAPI esté caída.
- Los de arquitectura comprueban las reglas de dependencia de [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
- Para ver la cobertura, después de `./mvnw verify`, abrir `target/site/jacoco/index.html`.

El porqué de esta estrategia está en el [ADR-0009](docs/adr/0009-testing-strategy.md).

## Uso de herramientas de IA

El equipo técnico de Conexa permitió usar herramientas de IA y pidió documentar el flujo. En pocas líneas:

- **Qué se delegó:** la redacción de los documentos, el código y los tests.
- **Qué decidió el autor:** los requisitos y sus prioridades, las decisiones técnicas del RFC y las del contrato de la API, una por una y con las alternativas a la vista. En varias corrigió la recomendación de la herramienta.
- **Cómo se revisó:** hasta el ticket #5, el autor aprobó cada paso. Desde el ticket #6, los tests y los pull requests los revisó una sesión supervisora de una herramienta de IA, por encargo del autor, que no los revisó uno por uno.
- **Revisión final:** con todo fusionado, otra sesión de la herramienta revisó el conjunto con la consigna de encontrar y demostrar fallas. Encontró cuatro importantes, que se corrigieron en el [pull request #30](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/30).

El detalle, con las correcciones y los límites de esa revisión, está en [`docs/process/AI-USAGE.md`](docs/process/AI-USAGE.md).

## Proceso de trabajo

El desafío se trabaja en **ciclos cortos** y con la **documentación primero**. Cada ciclo termina con una versión estable que se puede entregar tal como está. Dentro de cada ciclo, antes de escribir código queda por escrito qué se construye y cómo. El recorrido completo, etapa por etapa, está en [`docs/process/WORKFLOW.md`](docs/process/WORKFLOW.md).

### Ciclos

| Ciclo          | Fecha                       | Alcance                                                                                                          | Versión  | Estado    |
|----------------|-----------------------------|------------------------------------------------------------------------------------------------------------------|----------|-----------|
| **1. MVP**     | 14/11/2026                  | Todo lo que pide la consigna: los requisitos "Debe" del PRD, incluida la caché de las respuestas de SWAPI                                                      | `v0.1.0` | Cerrado |
| **2. Mejoras** | 15/11/2026                  | Los requisitos "Debería": despliegue público, errores claros, tolerancia a fallas de SWAPI, verificación automática, datos completos a pedido en los listados y rol de administrador con alta de usuarios | `v0.2.0` | Pendiente |
| **3. Extras**  | 15/11/2026, si sobra tiempo | Los requisitos "Podría": cobertura de tests visible, colección de pedidos lista para usar, límite de intentos de login, consulta flexible con GraphQL, límite de pedidos por usuario, cierre de sesión, registro público de usuarios y caché resistente al abuso | `v0.3.0` | Pendiente |

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

## Documentación

- Índice de la documentación: [`docs/README.md`](docs/README.md)
- Flujo de trabajo: [`docs/process/WORKFLOW.md`](docs/process/WORKFLOW.md)
- Requisitos (PRD): [`docs/requirements/PRD.md`](docs/requirements/PRD.md)
- Convenciones de commits, ramas, tickets y pull requests: [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md)
- Decisiones técnicas: [RFC-001](docs/rfc/RFC-001-architecture.md), con las alternativas evaluadas, y los [ADRs](docs/adr/README.md), con cada decisión aceptada y sus notas
- Arquitectura: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Instrucciones para agentes de IA: [`AGENTS.md`](AGENTS.md)
- Especificación técnica: [`docs/spec/SPEC.md`](docs/spec/SPEC.md)
- Tickets del ciclo 1: [hito `v0.1.0`](https://github.com/erickvaernet/conexa-star-wars-challenge/milestone/1)
- Instrucciones de configuración y uso: en este README, desde el [inicio rápido](#inicio-rápido)
- Registro de uso de herramientas de IA: [`docs/process/AI-USAGE.md`](docs/process/AI-USAGE.md)

## Autor

Erick Adriel Vaernet — [@erickvaernet](https://github.com/erickvaernet)
