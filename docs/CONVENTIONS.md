# Convenciones del repositorio

Reglas de trabajo del proyecto, en el orden en que se usan. El recorrido completo y su porqué están en el [flujo de trabajo](process/WORKFLOW.md).

1. [Commits](#commits-conventional-commits)
2. [Ramas](#ramas-conventional-branch)
3. [Tickets](#tickets-github-issues)
4. [Pull requests](#pull-requests)
5. [Versiones](#versiones)
6. [Documentación](#documentación)

**Idioma:** la documentación y los tickets se escriben en español. El código, los commits, las ramas, los títulos de los pull requests y los nombres de archivos y carpetas, en inglés.

## Commits: Conventional Commits

Todos los commits siguen [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) y se escriben **en inglés**.

```
<type>(<scope opcional>): <description>

[body opcional]

[footer(s) opcional(es)]
```

**Tipos permitidos**

| Tipo       | Uso                                                        |
|------------|------------------------------------------------------------|
| `feat`     | Nueva funcionalidad                                        |
| `fix`      | Corrección de un bug                                       |
| `docs`     | Solo documentación                                         |
| `test`     | Agregar o corregir tests                                   |
| `refactor` | Cambio de código que no agrega funcionalidad ni corrige bugs |
| `perf`     | Mejora de rendimiento                                      |
| `style`    | Formato, sin cambios de lógica                             |
| `build`    | Build o dependencias (Maven, Docker)                       |
| `ci`       | Pipelines de integración y despliegue                      |
| `chore`    | Mantenimiento que no entra en las categorías anteriores    |
| `revert`   | Revierte un commit anterior                                |

**Reglas**

- Descripción en inglés, en modo imperativo, en minúscula y sin punto final: `add`, no `added` ni `adds`.
- Primera línea de 72 caracteres como máximo.
- El scope es opcional y nombra el módulo afectado: `auth`, `people`, `films`, `starships`, `vehicles`, `swapi`.
- Cambios incompatibles: `!` después del tipo o scope, o un footer `BREAKING CHANGE: ...`.
- Un commit por cambio lógico.
- Si el commit pertenece a un ticket, lo referencia en un footer: `Refs: #12`.

**Ejemplos**

```
feat(auth): add JWT login endpoint
fix(people): handle empty page from SWAPI
test(films): add integration tests for name filter
docs: add ADR for SWAPI client strategy
build: add Dockerfile and docker-compose
```

## Ramas: Conventional Branch

Las ramas siguen [Conventional Branch 1.1.0](https://conventionalbranch.org/).

```
<type>/<description>
```

**Tipos permitidos**

| Rama        | Uso                                             | Ejemplo                       |
|-------------|-------------------------------------------------|-------------------------------|
| `main`      | Rama principal, siempre estable (sin prefijo)   | `main`                        |
| `feat/`     | Nueva funcionalidad                             | `feat/jwt-authentication`     |
| `fix/`      | Corrección de un bug                            | `fix/pagination-offset`       |
| `hotfix/`   | Corrección urgente sobre `main`                 | `hotfix/security-patch`       |
| `release/`  | Preparación de una versión                      | `release/v1.0.0`              |
| `chore/`    | Tareas sin código: dependencias, docs, config   | `chore/update-dependencies`   |

**Reglas**

- Solo minúsculas (`a-z`), números (`0-9`) y guiones (`-`) para separar palabras. Los puntos solo se usan en versiones (`release/v1.2.0`).
- Sin guiones ni puntos consecutivos, ni al inicio ni al final de la descripción.
- Sin espacios, guiones bajos ni mayúsculas.
- Si hay un issue asociado, va su número: `feat/issue-12-people-filter`.
- Descripción breve y en inglés.
- Las ramas de documentación usan `chore/`, porque Conventional Branch no tiene un prefijo propio para documentación.

## Tickets: GitHub Issues

El trabajo se organiza con los [issues del repositorio](https://github.com/erickvaernet/conexa-star-wars-challenge/issues). Están en el mismo lugar que el código, así que cualquiera puede ver qué se hizo, en qué orden y por qué.

**Qué es un ticket**

- **Cada ticket es una historia de usuario** del [PRD](requirements/PRD.md): algo que el usuario puede hacer y que se puede probar de punta a punta. El título empieza con el identificador del requisito: `REQ-08: Filtrar por ID y nombre a la vez`.
- **Las tareas técnicas van adentro del ticket**, como lista de pasos. No son tickets aparte.
- **El trabajo de base se hace dentro de la primera historia que lo necesita.** Por ejemplo, la conexión con SWAPI se construye en "Listar People". Solo lleva ticket propio, sin identificador de requisito, lo que no pertenece a ninguna historia: el esqueleto del proyecto o un documento como el RFC.
- **Los requisitos que valen para todo el proyecto no son tickets:** las pruebas (REQ-15, REQ-16) y el código documentado (REQ-14) forman parte de la definición de terminado de cada ticket.
- Un ticket puede cubrir varios requisitos si son inseparables, y se puede dividir si resulta demasiado grande para un solo pull request. Cada parte conserva el identificador del requisito.

**Contenido del ticket**

| Sección                 | Qué lleva                                                                                 |
|-------------------------|-------------------------------------------------------------------------------------------|
| Requisito               | Identificador y enlace al PRD                                                             |
| Historia de usuario     | La del PRD, sin cambios                                                                   |
| Criterios de aceptación | Enlace a los criterios de la especificación técnica, en formato "dado / cuando / entonces" |
| Tareas                  | Lista de pasos técnicos, en el orden en que se hacen                                      |
| Depende de              | Tickets que tienen que estar cerrados antes                                               |
| Referencias             | ADRs que aplican                                                                          |

**Cómo se implementa un ticket: los tests primero**

1. **Tests de aceptación.** Se escriben a partir de los criterios de aceptación, antes que el código. Al principio fallan.
2. **Revisión de los tests.** Se comprueba que prueban lo que pide la historia. A partir de acá quedan fijos.
3. **Código, tarea por tarea**, con un test unitario antes de cada parte, hasta que todos los tests pasan.
4. **Pull request** y revisión del código.

Reglas:

- **Los tests de aceptación ya revisados no se modifican para que pasen.** Si un test está mal, se corrige el criterio en la especificación técnica y el cambio se explica en el pull request.
- **Los tests no llaman a SWAPI:** usan respuestas reales grabadas, para que el resultado no dependa de un servicio externo.
- Cuando el código lo genera una herramienta de IA, los tests son su forma de verificar el trabajo: los ejecuta, lee las fallas y corrige. Por eso se escriben y se revisan antes.

**Definición de terminado**

Un ticket está terminado cuando se cumple todo esto:

- [ ] El proyecto compila.
- [ ] Sus tests de aceptación pasan.
- [ ] Los tests unitarios del código nuevo pasan, y los que ya existían siguen pasando.
- [ ] El código nuevo está documentado.
- [ ] La documentación afectada está actualizada: README, documentación de la API y especificación técnica.
- [ ] El pull request fue revisado por una persona.

Que los tests pasen es necesario, pero no alcanza: no dicen si el código es claro, seguro o está bien diseñado. Eso lo cubre la revisión.

**Hitos: uno por ciclo**

| Hito     | Ciclo      | Requisitos             |
|----------|------------|------------------------|
| `v0.1.0` | 1. MVP     | Los marcados "Debe"    |
| `v0.2.0` | 2. Mejoras | Los marcados "Debería" |
| `v0.3.0` | 3. Extras  | Los marcados "Podría"  |

**Etiquetas**

| Etiqueta           | Uso                                             |
|--------------------|-------------------------------------------------|
| `priority: must`   | Requisito "Debe"                                |
| `priority: should` | Requisito "Debería"                             |
| `priority: could`  | Requisito "Podría"                              |
| `type: feature`    | Funcionalidad de la aplicación                  |
| `type: docs`       | Documentación                                   |
| `type: test`       | Pruebas                                         |
| `type: chore`      | Configuración, dependencias y mantenimiento     |

**Ciclo de vida de un ticket**

1. **Abierto:** está en el hito de su ciclo y todavía no se empezó.
2. **En curso:** tiene una rama con su número (`feat/issue-12-people-filter`) y un pull request abierto.
3. **Cerrado:** su pull request se fusionó en `main`. El cierre es automático.

Si al terminar un ciclo queda un ticket abierto, se mueve al hito del ciclo siguiente y el cambio se anota en el PRD.

## Pull requests

Todo cambio llega a `main` por un pull request, aunque el proyecto tenga un solo autor: deja registrado qué se cambió, por qué y cómo se verificó.

- **Un pull request por ticket.** Si dos tickets son inseparables, un mismo pull request puede cerrar los dos.
- **Título** con formato de Conventional Commits y en inglés: `feat(people): add paginated list`.
- **Descripción** con tres partes:
  - `Closes #12`, para que el ticket se cierre solo al fusionar;
  - los requisitos que cubre (`REQ-01`, `REQ-05`);
  - cómo se verificó: tests agregados y pruebas manuales.
- **Antes de fusionar:** el ticket cumple la [definición de terminado](#tickets-github-issues).
- **Fusión con *squash*:** cada pull request queda como un solo commit en `main`, con el título del pull request. El detalle de los commits intermedios sigue visible en el pull request.
- La rama se borra después de fusionar.

## Versiones

- Las versiones siguen [Semantic Versioning](https://semver.org/lang/es/): `vMAYOR.MENOR.PARCHE`.
- Cada ciclo cierra con una etiqueta de git sobre `main`: `v0.1.0` (MVP), `v0.2.0` (mejoras) y `v0.3.0` (extras).
- Una corrección sobre una versión ya cerrada sube el último número: `v0.1.1`.
- La versión solo se etiqueta si todos los tests pasan y el hito no tiene tickets abiertos, o los que quedan se movieron al ciclo siguiente.

## Documentación

La estructura de `docs/`, el orden de lectura y los identificadores están en el [índice de la documentación](README.md).

**Nombres de archivos**

| Documento      | Ubicación y nombre                  | Ejemplo                                |
|----------------|-------------------------------------|----------------------------------------|
| PRD            | `docs/requirements/PRD.md`          | —                                      |
| RFC            | `docs/rfc/RFC-NNN-<tema>.md`        | `docs/rfc/RFC-001-architecture.md`     |
| ADR            | `docs/adr/NNNN-<tema>.md`           | `docs/adr/0004-pagination-and-filters.md` |
| Especificación técnica | `docs/spec/SPEC.md`                 | —                                      |
| Arquitectura   | `docs/ARCHITECTURE.md`              | —                                      |
| Flujo de trabajo | `docs/process/WORKFLOW.md`        | —                                      |

- `<tema>` va en inglés, en minúsculas y con guiones.
- Los números son correlativos y no se reutilizan.

**Cómo se referencia**

- **Entre documentos:** con el identificador y un enlace relativo: `[ADR-0004](../adr/0004-pagination-and-filters.md)`.
- **Requisitos:** siempre por su identificador (`REQ-08`). Si un requisito cambia de número, se actualizan todas sus referencias en el mismo cambio.
- **Desde tickets y pull requests:** con el identificador en el texto y, la primera vez, el enlace al documento.

**Reglas de los ADRs**

- El [índice](adr/README.md) lista todas las decisiones.
- Se parte de la [plantilla](adr/0000-template.md).
- Cada ADR registra **una** decisión e indica qué requisitos resuelve.
- Un ADR aceptado no se modifica. Si la decisión cambia, se escribe un ADR nuevo y el anterior pasa a "Reemplazado por ADR-NNNN". Sí admite notas fechadas que no cambian la decisión, en una sección "Notas posteriores" al final.

**Contenido de la especificación técnica**

Es un documento corto, con cuatro partes y nada más:

| Parte                   | Qué lleva                                                                         |
|-------------------------|-----------------------------------------------------------------------------------|
| Contrato de la API      | Rutas, parámetros, formato de las respuestas y códigos de error                   |
| Validaciones            | Qué valores se aceptan en cada parámetro y qué pasa si no se cumplen              |
| Casos límite            | Situaciones poco frecuentes y su resultado esperado: página inexistente, sin coincidencias, SWAPI caída |
| Criterios de aceptación | Por cada requisito, las condiciones verificables que debe cumplir para darse por hecho |

- No repite las decisiones ni sus motivos: esos están en los ADRs y se enlazan.
- Cada criterio de aceptación se identifica como `CA-NN.M`, donde `NN` es el número del requisito que verifica.
- Los tickets enlazan a sus criterios de aceptación; no los copian.

**Documentos vivos**

- El **PRD** se revisa al empezar cada ciclo: pueden cambiar prioridades y ciclos, no el contenido de un requisito ya implementado.
- La **especificación técnica** crece con cada ciclo: se le agrega lo que entra, sin reescribir lo anterior.
