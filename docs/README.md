# Documentación del proyecto

Índice de todo lo que hay en `docs/` y en qué orden conviene leerlo.

## Orden de lectura

Cada documento se apoya en el anterior. El proceso completo está explicado en el [README principal](../README.md#proceso-de-trabajo).

| # | Documento                  | Qué responde                                                             | Dónde                                    | Estado                |
|---|----------------------------|--------------------------------------------------------------------------|------------------------------------------|-----------------------|
| 1 | **PRD**                    | Qué se construye y por qué: requisitos, prioridades y ciclos             | [`requirements/PRD.md`](requirements/PRD.md) | Aprobado (14/11/2026) |
| 2 | **RFC** | Cómo se construye, a nivel macro, con las alternativas evaluadas | [`rfc/RFC-001-architecture.md`](rfc/RFC-001-architecture.md) | Aceptado (14/11/2026) |
| 3 | **ADRs** | Una decisión técnica por archivo, con su contexto y sus consecuencias | [`adr/README.md`](adr/README.md) | 23 aceptados: 13 del ciclo 1 (14/11/2026), 7 del ciclo 2 y 3 del ciclo 3 (15/11/2026) |
| 4 | **Especificación técnica** | El detalle ejecutable: contrato de la API, validaciones, casos límite y criterios de aceptación | [`spec/SPEC.md`](spec/SPEC.md) | Aprobada (14/11/2026); extendida para el ciclo 2 y para el ciclo 3 (15/11/2026); precisada con la revisión final del ciclo 3 (16/11/2026) |
| 5 | **Arquitectura** | Cómo se organiza el código: carpetas, capas y dependencias permitidas | [`ARCHITECTURE.md`](ARCHITECTURE.md) | Vigente |
| 6 | **Registro de uso de IA** | Qué se delegó en herramientas de IA, cómo se revisó y qué se corrigió a mano | [`process/AI-USAGE.md`](process/AI-USAGE.md) | Al 16/11/2026 |

Dos documentos acompañan a todos los demás:

- [`process/WORKFLOW.md`](process/WORKFLOW.md): el flujo de trabajo completo, etapa por etapa, y dónde vive cada regla.
- [`CONVENTIONS.md`](CONVENTIONS.md): las reglas puntuales de commits, ramas, tickets, pull requests y nombres de archivos.

## Estructura

```
docs/
├── README.md              este índice
├── CONVENTIONS.md         reglas de trabajo del repositorio
├── ARCHITECTURE.md        cómo se organiza el código
├── requirements/
│   └── PRD.md             requisitos del producto
├── rfc/
│   └── RFC-001-<tema>.md  propuesta técnica con alternativas
├── adr/
│   ├── README.md          índice de las decisiones
│   ├── 0000-template.md   plantilla para decisiones nuevas
│   └── NNNN-<tema>.md     una decisión por archivo
├── spec/
│   └── SPEC.md            especificación técnica
├── api/
│   └── star-wars-api.postman_collection.json
│                          colección de pedidos, para Postman y herramientas parecidas
└── process/
    ├── WORKFLOW.md        flujo de trabajo completo
    └── AI-USAGE.md        registro de uso de herramientas de IA
```

## Identificadores

Todo lo que se referencia desde otro lugar tiene un identificador estable.

| Qué            | Identificador | Ejemplo    | Dónde se define                          |
|----------------|---------------|------------|------------------------------------------|
| Requisito      | `REQ-NN`      | `REQ-08`   | Tabla de requisitos del PRD              |
| RFC            | `RFC-NNN`     | `RFC-001`  | Nombre del archivo en `rfc/`             |
| Decisión       | `ADR-NNNN`    | `ADR-0003` | Nombre del archivo en `adr/`             |
| Criterio de aceptación | `CA-NN.M` | `CA-08.2` | Sección 6 de la especificación técnica; `NN` es el número del requisito |
| Ticket         | `#N`          | `#12`      | GitHub Issues del repositorio            |
| Versión        | `vX.Y.Z`      | `v0.1.0`   | Etiqueta de git al cerrar cada ciclo     |

## Trazabilidad

Cualquier línea de código se puede seguir hacia atrás hasta el requisito que la originó:

```
Requisito (REQ-08)
   └── Decisión (ADR-0004)
          └── Criterio de aceptación (CA-08.2)
                 └── Ticket (#11)
                        └── Rama y pull request
                               └── Test
```

- Un **ADR** indica qué requisitos resuelve.
- Cada **criterio de aceptación** lleva en su identificador el número del requisito que verifica.
- Cada **ticket** es una historia de usuario: nombra su requisito en el título y enlaza a sus criterios de aceptación.
- Cada **pull request** cierra su ticket.
- Cada **test de aceptación** sale de un criterio de la especificación técnica.
