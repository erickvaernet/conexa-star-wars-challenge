# Conexa · Back-end Java Challenge — Star Wars API

API REST en **Java 21** que se integra con [SWAPI](https://www.swapi.tech/documentation) para listar **People, Films, Starships y Vehicles** de forma paginada, con filtrado por ID y/o nombre y autenticación segura.

> 🚧 **Estado:** en desarrollo. Este README se irá completando con instrucciones de instalación, uso y decisiones de diseño.

## Alcance

- [ ] Integración con la API de Star Wars (People, Films, Starships, Vehicles)
- [ ] Listados paginados
- [ ] Filtrado por ID y/o nombre
- [ ] Login seguro con autenticación y autorización para acceder a los listados (usuarios en base de datos + seed inicial)
- [ ] Documentación de la API con Swagger / OpenAPI
- [ ] Pruebas unitarias y de integración
- [x] Entorno local dockerizado
- [ ] Despliegue en un servidor gratuito

## Cómo levantarlo

Hace falta tener Docker. No hay que instalar Java ni configurar nada.

```bash
docker compose up --build
```

Levanta la aplicación y su base de datos. Cuando termina de arrancar:

| Qué | Dónde |
|---|---|
| Control de estado | <http://localhost:8080/actuator/health> |
| Documentación interactiva (Swagger UI) | <http://localhost:8080/swagger-ui.html> |

Por ahora están el login, la protección del acceso y el listado de People. Los filtros, el detalle, la caché y las otras tres entidades llegan con los próximos tickets.

### Iniciar sesión

`docker compose up` carga un usuario de prueba:

| Usuario | Contraseña |
|---|---|
| `demo` | `demo1234` |

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

Un usuario o una contraseña incorrectos responden `401`, y un pedido al que le falta alguno de los dos, `400`. Los errores usan el formato Problem Details.

Es una credencial de prueba, pensada para quien corrige: en la base de datos solo está su *hash* BCrypt.

### Usar el token

Todos los pedidos necesitan el token, salvo el login, la documentación y el control de estado. Se envía en la cabecera `Authorization`:

```bash
curl http://localhost:8080/api/v1/people \
  -H "Authorization: Bearer eyJ..."
```

Sin token, o con uno mal formado, con la firma alterada o vencido, la respuesta es `401`, siempre la misma.

### Listar People

El pedido de arriba devuelve la primera página de personajes, de a 10. Con `page` y `size` se elige otra página y otro tamaño:

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

| Parámetro | Por defecto | Valores aceptados |
|---|---|---|
| `page` | `1` | Un entero de 1 o más. La primera página es la 1 |
| `size` | `10` | Un entero de 1 a 100 |

- Una página posterior a la última devuelve `content` vacío, con los totales correctos.
- Un valor inválido responde `400` y el `detail` dice qué parámetro corregir: `Parameter 'size' must be an integer between 1 and 100.`
- Los datos salen de [SWAPI](https://www.swapi.tech/documentation). Si SWAPI responde con un error, el listado responde `502`; si no responde a tiempo, `504`. El login no depende de SWAPI.

### Configuración

No hace falta configurar nada: `docker compose` trae valores de desarrollo. Para cambiarlos, copiar `.env.example` a `.env`.

| Variable | Para qué | En `docker compose` | Sin `docker compose` |
|---|---|---|---|
| `JWT_SECRET` | Clave que firma los tokens, de al menos 32 caracteres | Una clave de desarrollo, pública | Obligatoria: sin ella la aplicación no arranca |
| `JWT_TTL_MINUTES` | Minutos hasta que vence un token | `60` | `60` |
| `SEED_ENABLED` | Cargar el usuario de prueba | `true` | `false` |
| `SWAPI_BASE_URL` | Dirección de SWAPI | `https://www.swapi.tech/api` | `https://www.swapi.tech/api` |
| `SWAPI_CONNECT_TIMEOUT` | Tiempo máximo para conectarse con SWAPI | `3s` | `3s` |
| `SWAPI_READ_TIMEOUT` | Tiempo máximo de espera de una respuesta de SWAPI | `10s` | `10s` |

Los tiempos se escriben como un número con su unidad: `500ms`, `3s`.

Para correr los tests hace falta además un JDK 21 o posterior:

| Para | Comando |
|---|---|
| Tests unitarios y de arquitectura, sin red ni Docker | `./mvnw test` |
| Todos los tests, incluidos los de aceptación | `./mvnw verify` |

## Proceso de trabajo

El desafío se trabaja en **ciclos cortos** y con la **documentación primero**. Cada ciclo termina con una versión estable que se puede entregar tal como está. Dentro de cada ciclo, antes de escribir código queda por escrito qué se construye y cómo. El recorrido completo, etapa por etapa, está en [`docs/process/WORKFLOW.md`](docs/process/WORKFLOW.md).

### Ciclos

| Ciclo          | Fecha                       | Alcance                                                                                                          | Versión  | Estado    |
|----------------|-----------------------------|------------------------------------------------------------------------------------------------------------------|----------|-----------|
| **1. MVP**     | 14/11/2026                  | Todo lo que pide la consigna: los requisitos "Debe" del PRD, incluida la caché de las respuestas de SWAPI                                                      | `v0.1.0` | En curso  |
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
| 4 | **Implementación** | Tests primero y después el código, historia por historia                                                                   | —                                                       | Pendiente            |
| 5 | **Cierre**         | Rama principal estable, versión etiquetada y documentación al día                                         | —                                                       | Pendiente            |

En el ciclo 1 los tres documentos se escriben completos. En los ciclos siguientes no se rehacen: se revisan las prioridades del PRD, se registra un ADR por cada decisión nueva y se extiende la especificación técnica con lo que entra.

**Por qué así**

- **Cada documento se apoya en el anterior ya cerrado.** No se eligen tecnologías sin tener los requisitos fijos, ni se detalla un contrato sin haber tomado las decisiones de arquitectura.
- **Todo queda trazable.** Un requisito del PRD lleva a una decisión (ADR), esa decisión a una regla de la especificación técnica, y esa regla a un test.
- **Es la base para trabajar con herramientas de IA.** La especificación técnica es la entrada con la que se genera código asistido: cuanto más precisa es, menos margen queda para que la herramienta invente. Cada etapa pasa por una revisión humana antes de avanzar.
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
| 14/11/2026 | Se escribieron los once ADRs, uno por decisión aceptada, la guía de arquitectura y las instrucciones para agentes de IA. |
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

## Documentación

- Índice de la documentación: [`docs/README.md`](docs/README.md)
- Flujo de trabajo: [`docs/process/WORKFLOW.md`](docs/process/WORKFLOW.md)
- Requisitos (PRD): [`docs/requirements/PRD.md`](docs/requirements/PRD.md)
- Convenciones de commits, ramas, tickets y pull requests: [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md)
- Decisiones técnicas: [RFC-001](docs/rfc/RFC-001-architecture.md), con las alternativas evaluadas, y [13 ADRs](docs/adr/README.md), uno por decisión
- Arquitectura: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Instrucciones para agentes de IA: [`AGENTS.md`](AGENTS.md)
- Especificación técnica: [`docs/spec/SPEC.md`](docs/spec/SPEC.md)
- Tickets del ciclo 1: [hito `v0.1.0`](https://github.com/erickvaernet/conexa-star-wars-challenge/milestone/1)
- Instrucciones de configuración y uso: _próximamente_
- Registro de uso de herramientas de IA: _próximamente_ en `docs/process/AI-USAGE.md`

## Autor

Erick Adriel Vaernet — [@erickvaernet](https://github.com/erickvaernet)
