# Convenciones del repositorio

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
