# ADR-0009: Estrategia de pruebas

| Campo      | Valor                                                        |
|------------|--------------------------------------------------------------|
| Estado     | Aceptado                                                     |
| Fecha      | 14/11/2026                                                   |
| Requisitos | REQ-15, REQ-16                                               |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), decisión D-11     |

## Contexto

La consigna pide pruebas unitarias y de integración. En este proyecto, los tests de aceptación de cada historia se escriben antes que el código y son la forma de verificar el trabajo, también cuando lo genera una herramienta de IA (ver el [flujo de trabajo](../process/WORKFLOW.md#verificación)).

SWAPI no puede usarse en los tests: es un servicio externo que demora cada vez más a quien le hace muchos pedidos y puede estar caído.

## Decisión

Tres niveles de pruebas:

| Nivel               | Qué prueba                                                              | Con qué                                                        |
|---------------------|-------------------------------------------------------------------------|----------------------------------------------------------------|
| **Unitarias**       | Cada parte por separado: paginación en memoria, filtros, traducción de SWAPI | JUnit 5, Mockito y AssertJ. Sin red ni Docker             |
| **De aceptación**   | Cada historia de punta a punta, por HTTP, con la aplicación levantada   | SWAPI simulada con WireMock y PostgreSQL real con Testcontainers |
| **De arquitectura** | Las reglas de dependencia del [ADR-0002](0002-code-organization.md)     | ArchUnit                                                       |

- **Los tests nunca llaman a la SWAPI real.** El servidor simulado devuelve respuestas reales grabadas, no inventadas.
- La cobertura se mide con JaCoCo. El objetivo del PRD es al menos 80 % de la lógica propia.

## Alternativas consideradas

| Alternativa                                   | A favor                          | En contra                                                                       |
|-----------------------------------------------|----------------------------------|---------------------------------------------------------------------------------|
| **PostgreSQL real con Testcontainers**        | Prueba contra la misma base que se usa al ejecutar | Necesita Docker; los tests tardan más                          |
| H2 en memoria                                 | Más rápido, sin Docker           | Prueba contra una base distinta de la real                                       |
| **SWAPI simulada con respuestas grabadas**    | Prueba también la traducción de las respuestas, que es donde SWAPI es irregular | Hay que grabar y mantener las respuestas |
| Reemplazar el cliente por un objeto falso     | Más simple                       | No prueba la traducción de las respuestas                                        |
| Llamar a la SWAPI real                        | Prueba el servicio verdadero     | Los tests fallarían cuando SWAPI esté lenta o caída                              |
| **Reglas de arquitectura con tests**          | Una regla rota hace fallar los tests de inmediato | Unos pocos tests más para mantener                           |
| Reglas solo escritas                          | Nada que mantener                | Una regla que nadie verifica se termina rompiendo                                |

## Consecuencias

- Los tests de aceptación necesitan Docker, que ya es un requisito para levantar el proyecto.
- Los tests verifican lo que se cree que SWAPI responde. Si SWAPI cambia su formato, hay que volver a grabar las respuestas.
- Los tests unitarios corren sin red y en segundos; los de aceptación tardan más porque levantan contenedores.
- Las reglas de arquitectura protegen la estructura aunque el código lo escriba una herramienta de IA.

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **14/11/2026.** Este ADR nombra JUnit 5. Al armar el esqueleto del proyecto se vio que Spring Boot 4.1 trae JUnit 6 (la 6.0.3), y esa es la versión que se usa. Los tests se escriben igual: las mismas anotaciones y la misma forma de organizarlos. La decisión no cambia: se usa la versión de JUnit que acompaña a Spring Boot, junto con Mockito y AssertJ.
