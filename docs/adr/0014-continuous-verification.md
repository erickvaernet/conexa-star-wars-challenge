# ADR-0014: Verificación automática en cada cambio

| Campo      | Valor                                                                       |
|------------|-----------------------------------------------------------------------------|
| Estado     | Aceptado                                                                    |
| Fecha      | 15/11/2026                                                                  |
| Requisitos | REQ-23, REQ-19                                                              |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), sección 4 (decisiones postergadas) |

## Contexto

El PRD pide que quien corrige pueda ver que el proyecto compila y que los tests pasan sin tener que correrlos (REQ-23), y lo cuenta entre sus métricas de éxito: "los tests se ejecutan de forma automática ante cada cambio".

Qué deja preparado el MVP:

- Todos los tests corren con un solo comando, `./mvnw verify`, y ninguno llama a la SWAPI real ([ADR-0009](0009-testing-strategy.md)).
- Los tests de aceptación necesitan Docker, porque levantan PostgreSQL con Testcontainers.
- El repositorio está en GitHub y no tiene ninguna verificación automática.

Dos huecos que hoy no cubre ningún test:

- **El criterio CA-19.1 habla de `docker compose up`, y su test no lo ejecuta.** Levanta la aplicación dentro del test y pide el control de estado. Que la imagen se construya, que `docker compose` espere a la base de datos y que el usuario de prueba pueda iniciar sesión ahí se comprobó a mano.
- **Los tests se corrieron siempre con un JDK más nuevo que el 21** ([ADR-0010](0010-local-environment.md)). El compilador rechaza lo posterior a Java 21, pero ningún test corrió todavía sobre un JDK 21.

Datos verificados el 15/11/2026: los ejecutores de GitHub con Ubuntu 24.04 traen Docker 28, Docker Compose 2.38 y un JDK 21 ya instalado; las acciones oficiales vigentes son `actions/checkout` v7, `actions/setup-java` v6 y `actions/upload-artifact` v7.

## Decisión

Se usa **GitHub Actions**, con un solo archivo, `.github/workflows/ci.yml`, y dos trabajos que corren a la vez:

| Trabajo   | Qué hace                                                                                             | Qué comprueba                                              |
|-----------|------------------------------------------------------------------------------------------------------|------------------------------------------------------------|
| `verify`  | `./mvnw -B -ntp verify` sobre un JDK 21, con el Docker del ejecutor                                    | Que compila y que pasan todos los tests, sobre Java 21      |
| `compose` | `docker compose up --build --wait` y tres pedidos con `curl`: control de estado, login del usuario de prueba y un listado sin token | CA-19.1 tal como está escrito: la imagen, la base de datos y la carga inicial, levantadas como lo hace quien corrige |

- **Se dispara** con cada *push* a `main` y con cada pull request.
- **El README muestra un distintivo** con el resultado de la última ejecución sobre `main`.
- **Ningún trabajo llama a la SWAPI real.** El trabajo `compose` fija `SWAPI_BASE_URL` en una dirección donde nadie atiende: los tres pedidos que hace no la necesitan.
- **No se exige que la verificación pase para poder fusionar.** Eso se configura en GitHub, no en el repositorio, y queda a criterio del autor.

## Alternativas consideradas

| Alternativa                                           | A favor                                                                 | En contra                                                                 |
|-------------------------------------------------------|-------------------------------------------------------------------------|---------------------------------------------------------------------------|
| **GitHub Actions**                                    | El repositorio ya está en GitHub; gratis para repositorios públicos; el resultado se ve en cada pull request y en el README, sin cuentas nuevas | La definición queda atada a GitHub |
| Otro servicio (CircleCI, GitLab CI, Jenkins)          | Equivalentes en lo que hace falta acá                                   | Una cuenta y una integración más, para lo mismo                            |
| **Dos trabajos: tests y `docker compose`**            | Cubre CA-19.1 de verdad; corren a la vez, así que no alargan la espera  | El segundo construye la imagen sin la caché de Maven: unos minutos más de cómputo |
| Solo `./mvnw verify`                                  | Más corto                                                               | CA-19.1 sigue comprobado a mano                                            |
| Un test de Java que ejecute `docker compose`          | Queda junto a los demás tests de aceptación                             | Construir la imagen dentro de `./mvnw verify` lo hace lento y frágil en cualquier máquina; un guion de tres pedidos se lee mejor |
| **Sin SWAPI en el trabajo `compose`**                 | El resultado no depende de un servicio externo ni gasta su cupo         | No comprueba un listado de punta a punta contra la SWAPI real              |
| Un pedido real a SWAPI en cada ejecución              | Detectaría un cambio de formato de SWAPI                                | La verificación fallaría cuando SWAPI esté lenta o caída, que es lo que el [ADR-0009](0009-testing-strategy.md) evita |

## Consecuencias

- Cada pull request muestra si compila y si pasan los tests, antes de fusionar. La revisión deja de depender de que alguien los haya corrido.
- Los tests pasan a correr sobre un JDK 21, el mismo de la imagen que se entrega. Cierra la consecuencia anotada en el [ADR-0010](0010-local-environment.md): una diferencia entre el JDK de desarrollo y el 21 ya no pasaría inadvertida.
- CA-19.1 queda cubierto por una ejecución automática. La especificación técnica lo anota así: el test de Java comprueba el control de estado, y el trabajo `compose` comprueba `docker compose`.
- El trabajo `compose` usa el usuario y la contraseña de prueba, que el README ya publica. No hay ningún secreto en la definición ni hace falta cargar ninguno en GitHub.
- La definición pide solo permiso de lectura sobre el repositorio.
- Una ejecución nueva sobre la misma rama cancela la anterior, para no gastar cómputo en un commit ya reemplazado.
- Las acciones se fijan por su versión mayor (`@v7`), no por el identificador exacto de un commit. Es lo habitual y se lee de un vistazo; fijarlas por commit protege más ante una acción comprometida y queda como mejora posible.
- Si el proyecto se mudara de GitHub, la definición hay que reescribirla. Los comandos que ejecuta (`./mvnw verify` y `docker compose up --wait`) son los mismos en cualquier servicio.
- El informe de cobertura de JaCoCo se guarda como archivo adjunto de cada ejecución. Mostrar el porcentaje en el README es el REQ-27, del ciclo 3.
