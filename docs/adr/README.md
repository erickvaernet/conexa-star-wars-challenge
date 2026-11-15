# Decisiones técnicas (ADRs)

Cada archivo registra una decisión: su contexto, lo que se eligió, las alternativas descartadas y las consecuencias. Las once primeras salen del [RFC-001](../rfc/RFC-001-architecture.md), que el autor revisó decisión por decisión. Las dos siguientes salen de la revisión de la [especificación técnica](../spec/SPEC.md). Las del ciclo 2, de la 0014 a la 0020, salen de las decisiones que el RFC dejó postergadas en su sección 4: las tomó la sesión supervisora de una herramienta de IA, por encargo del autor, sobre propuestas con alternativas y una recomendación ([registro de uso de herramientas de IA](../process/AI-USAGE.md#ciclo-2)).

| ADR                                             | Decisión                                                                        | Estado   |
|-------------------------------------------------|---------------------------------------------------------------------------------|----------|
| [ADR-0001](0001-platform-and-build.md)          | Spring Boot 4.1 sobre Java 21, con Maven                                        | Aceptado |
| [ADR-0002](0002-code-organization.md)           | Código por funcionalidad, con una frontera hacia SWAPI y un núcleo común        | Aceptado |
| [ADR-0003](0003-swapi-client.md)                | Cliente de SWAPI con `RestClient` e hilos virtuales                             | Aceptado |
| [ADR-0004](0004-pagination-and-filters.md)      | Paginación decidida según cómo responde SWAPI; páginas desde 1                  | Aceptado |
| [ADR-0005](0005-authentication.md)              | JWT sin estado, con el soporte de Spring Security                               | Aceptado |
| [ADR-0006](0006-users-database-and-seed.md)     | PostgreSQL, Spring Data JPA y Flyway; usuarios de prueba en una carpeta aparte  | Aceptado |
| [ADR-0007](0007-error-format.md)                | Errores en formato Problem Details                                              | Aceptado |
| [ADR-0008](0008-api-documentation.md)           | Documentación de la API generada desde el código                                | Aceptado |
| [ADR-0009](0009-testing-strategy.md)            | Pruebas unitarias, de aceptación y de arquitectura                              | Aceptado |
| [ADR-0010](0010-local-environment.md)           | Docker Compose; JDK 25 compilando a nivel 21                                    | Aceptado |
| [ADR-0011](0011-swapi-response-cache.md)        | Caché en memoria de las respuestas de SWAPI, desde el MVP                       | Aceptado |
| [ADR-0012](0012-response-data-shape.md)         | Forma de los datos: identificadores y valores como texto; relaciones como listas de identificadores, solo hacia entidades que la API expone | Aceptado |
| [ADR-0013](0013-parameter-limits-and-invalid-values.md) | Tamaño de página de 1 a 100; un valor inválido se rechaza, no se corrige | Aceptado |
| [ADR-0014](0014-continuous-verification.md)     | Verificación automática en cada cambio, con GitHub Actions: los tests y `docker compose` | Aceptado |
| [ADR-0015](0015-public-deployment.md)           | Despliegue público en Render, con la base de datos en Neon                      | Aceptado |
| [ADR-0016](0016-validation-error-detail.md)     | Detalle campo por campo en los errores de validación, en una lista `errors`     | Aceptado |
| [ADR-0017](0017-role-based-access.md)           | Dos roles, `USER` y `ADMIN`; el rol se lee del token y la regla está junto a las demás | Aceptado |
| [ADR-0018](0018-user-creation.md)               | Alta de usuarios por el administrador: `POST /api/v1/users`                     | Aceptado |
| [ADR-0019](0019-expanded-listings.md)           | Datos completos a pedido en los listados, con `expanded=true`                   | Aceptado |
| [ADR-0020](0020-swapi-failure-tolerance.md)     | Un reintento acotado ante fallas de SWAPI, sin corte automático                 | Aceptado |

## Reglas

- Un ADR nuevo parte de la [plantilla](0000-template.md).
- Un ADR aceptado no se modifica. Si la decisión cambia, se escribe uno nuevo y el anterior pasa a "Reemplazado por ADR-NNNN". Sí admite notas fechadas que no cambian la decisión, en una sección "Notas posteriores" al final.
- Las decisiones de cada ciclo se registran cuando empieza. Las del ciclo 2 ya están; las del ciclo 3 están listadas en la sección 4 del [RFC-001](../rfc/RFC-001-architecture.md).
