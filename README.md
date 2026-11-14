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
- [ ] Entorno local dockerizado
- [ ] Despliegue en un servidor gratuito

## Proceso de trabajo

El desafío se trabaja en **ciclos cortos** y con la **documentación primero**. Cada ciclo termina con una versión estable que se puede entregar tal como está. Dentro de cada ciclo, antes de escribir código queda por escrito qué se construye y cómo. El recorrido completo, etapa por etapa, está en [`docs/process/WORKFLOW.md`](docs/process/WORKFLOW.md).

### Ciclos

| Ciclo          | Fecha                       | Alcance                                                                                                          | Versión  | Estado    |
|----------------|-----------------------------|------------------------------------------------------------------------------------------------------------------|----------|-----------|
| **1. MVP**     | 14/11/2026                  | Todo lo que pide la consigna: los requisitos "Debe" del PRD, incluida la caché de las respuestas de SWAPI                                                      | `v0.1.0` | En curso  |
| **2. Mejoras** | 15/11/2026                  | Los requisitos "Debería": despliegue público, errores claros, tolerancia a fallas de SWAPI, verificación automática, datos completos a pedido en los listados y rol de administrador con alta de usuarios | `v0.2.0` | Pendiente |
| **3. Extras**  | 15/11/2026, si sobra tiempo | Los requisitos "Podría": cobertura de tests visible, colección de pedidos lista para usar, límite de intentos de login, consulta flexible con GraphQL, límite de pedidos por usuario, cierre de sesión y registro público de usuarios | `v0.3.0` | Pendiente |

- **El MVP cumple la consigna por sí solo.** Si un ciclo posterior no llega a cerrarse, se entrega la última versión estable.
- **Cada ciclo cierra con la rama principal estable:** todos los tests pasan y la versión queda etiquetada.
- **Al empezar cada ciclo se revisan las prioridades** del PRD; un requisito puede cambiar de ciclo.

### Etapas dentro de un ciclo

Cada etapa empieza recién cuando la anterior está revisada y aprobada.

| # | Etapa              | Qué responde                                                                                              | Dónde                                                   | Estado en el ciclo 1 |
|---|--------------------|-----------------------------------------------------------------------------------------------------------|---------------------------------------------------------|----------------------|
| 1 | **PRD**            | **Qué** y **por qué**: problema, usuarios, requisitos, prioridades y métricas de éxito                     | [`docs/requirements/PRD.md`](docs/requirements/PRD.md) | Aprobado             |
| 2 | **RFC y ADRs**     | **Cómo**, a nivel macro: arquitectura, tecnologías y seguridad, con las alternativas evaluadas. Cada decisión tomada se registra como un ADR | [`docs/rfc/RFC-001-architecture.md`](docs/rfc/RFC-001-architecture.md) y [`docs/adr/`](docs/adr/README.md) | Aceptado: RFC y 11 ADRs |
| 3 | **Especificación técnica** | El detalle ejecutable: contrato de la API, validaciones, casos límite y criterios de aceptación   | `docs/spec/` | Pendiente            |
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

## Documentación

- Índice de la documentación: [`docs/README.md`](docs/README.md)
- Flujo de trabajo: [`docs/process/WORKFLOW.md`](docs/process/WORKFLOW.md)
- Requisitos (PRD): [`docs/requirements/PRD.md`](docs/requirements/PRD.md)
- Convenciones de commits, ramas, tickets y pull requests: [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md)
- Decisiones técnicas: [RFC-001](docs/rfc/RFC-001-architecture.md), con las alternativas evaluadas, y [11 ADRs](docs/adr/README.md), uno por decisión
- Arquitectura: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Instrucciones para agentes de IA: [`AGENTS.md`](AGENTS.md)
- Especificación técnica: _próximamente_ en `docs/spec/`
- Instrucciones de configuración y uso: _próximamente_
- Registro de uso de herramientas de IA: _próximamente_ en `docs/process/AI-USAGE.md`

## Autor

Erick Adriel Vaernet — [@erickvaernet](https://github.com/erickvaernet)
