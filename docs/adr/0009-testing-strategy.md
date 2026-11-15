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
- **15/11/2026.** Precisiones que aparecieron con el primer listado (ticket #8):
  - **Red de seguridad.** La configuración de los tests fija `SWAPI_BASE_URL` en una dirección local donde nadie atiende. Solo los tests que usan la SWAPI simulada la reemplazan. Así, un test que se olvida de simularla recibe un error y no una respuesta de la SWAPI real. Un test de aceptación lo comprueba (CA-16.1).
  - **Las fallas no se graban.** No se puede hacer que la SWAPI real falle o demore a pedido: el error `500`, la respuesta lenta y las respuestas que no se pueden interpretar se arman en el test, sobre la SWAPI simulada, y cada test lo indica.
  - **El cliente de SWAPI se prueba sin Docker,** contra la SWAPI simulada: qué pide, cómo lee una respuesta real y qué hace con cada tipo de falla.
  - **Sesión en los tests de aceptación.** El login del usuario de prueba está en una pieza de apoyo compartida, para no repetirlo en los tests de cada entidad.
- **15/11/2026.** Dos precisiones que aparecieron al implementar (tickets #10 y #12) y que no habían quedado en ningún ADR:
  - **La regla de no modificar un test ya revisado es para los tests de aceptación.** Un test unitario puede adaptarse cuando cambia la firma del código que prueba (cómo arma sus datos o cómo lo llama), mientras siga comprobando lo mismo. La alternativa era fijar también los unitarios; se descartó porque no salen de un criterio aprobado sino del diseño interno, y fijarlos impediría cambiar una firma. Cada adaptación se lista en el pull request, test por test; la primera está en el [#23](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/23).
  - **Una clase de tests de aceptación por entidad,** con su caso de CA-06.6 adentro (`FilmsAcceptanceTest`, `StarshipsAcceptanceTest` y `VehiclesAcceptanceTest`). El criterio nombra tres entidades y la alternativa era un solo test compartido; se descartó para que cada ticket de entidad sume su caso sin tocar un archivo que también toca otro ticket. Los criterios que sí comparten test (CA-05.1, CA-05.2 y CA-10.4) son listas de rutas, y cada entidad les suma una línea.
