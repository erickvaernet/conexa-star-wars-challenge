# Decisiones técnicas (ADRs)

Cada archivo registra una decisión: su contexto, lo que se eligió, las alternativas descartadas y las consecuencias. Todas salen del [RFC-001](../rfc/RFC-001-architecture.md), que el autor revisó decisión por decisión.

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

## Reglas

- Un ADR nuevo parte de la [plantilla](0000-template.md).
- Un ADR aceptado no se modifica. Si la decisión cambia, se escribe uno nuevo y el anterior pasa a "Reemplazado por ADR-NNNN". Sí admite notas fechadas que no cambian la decisión, en una sección "Notas posteriores" al final.
- Las decisiones de los ciclos 2 y 3 se registran cuando empieza cada ciclo. Están listadas en la sección 4 del [RFC-001](../rfc/RFC-001-architecture.md).
