# Instrucciones para agentes de IA

Reglas para cualquier herramienta de IA que trabaje en este repositorio. Este archivo no repite las reglas: indica dónde están y en qué orden leerlas.

## Antes de cambiar algo

Leer, en este orden:

1. [`docs/process/WORKFLOW.md`](docs/process/WORKFLOW.md): cómo se trabaja y qué aprobación necesita cada etapa.
2. [`docs/requirements/PRD.md`](docs/requirements/PRD.md): qué se construye y con qué prioridad.
3. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): dónde va cada cosa y qué puede depender de qué.
4. [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md): commits, ramas, tickets y pull requests.
5. El ticket en el que se trabaja y los criterios de aceptación que enlaza.

Las decisiones técnicas y sus motivos están en [`docs/adr/`](docs/adr/README.md).

## Reglas que no se negocian

- **No se avanza de etapa sin aprobación del autor.** Ante una duda, se pregunta; no se asume.
- **Los tests de aceptación se escriben antes que el código,** a partir de los criterios del ticket.
- **Un test de aceptación ya revisado no se modifica para que pase.** Si está mal, se corrige el criterio y se explica el cambio.
- **Los tests nunca llaman a la SWAPI real.**
- **Se respetan las reglas de dependencia** de `ARCHITECTURE.md`. Hay tests que las comprueban.
- **No se cambia una decisión registrada en un ADR.** Si hace falta otra cosa, se propone un ADR nuevo.
- **Ningún secreto va al repositorio.**
- **No se hace commit ni push, ni se crean tickets, sin que el autor lo pida.**

## Idioma

- Documentación y tickets: español.
- Código, commits, ramas, títulos de pull requests y nombres de archivos: inglés.

## Antes de dar un ticket por terminado

Comprobar la definición de terminado de [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md#tickets-github-issues): el proyecto compila, pasan los tests de aceptación y los unitarios, pasa la verificación automática del pull request, el código nuevo está documentado y la documentación afectada está al día.

## Comandos

Hace falta un JDK 21 o posterior. Maven no se instala: se usa el *wrapper* (`./mvnw`). Los tests de aceptación y el entorno local necesitan Docker.

| Para | Comando |
|---|---|
| Compilar | `./mvnw compile` |
| Tests unitarios y de arquitectura, sin red ni Docker | `./mvnw test` |
| Todos los tests, incluidos los de aceptación | `./mvnw verify` |
| Ver la cobertura, después de `./mvnw verify` | Abrir `target/site/jacoco/index.html` |
| Levantar la aplicación y la base de datos | `docker compose up --build` |
| Probar la aplicación levantada | Abrir <http://localhost:8080/swagger-ui.html>; el usuario de prueba está en el [README](README.md#inicio-rápido) |
| Apagar y borrar los datos locales | `docker compose down -v` |

Los tests de aceptación son las clases que terminan en `AcceptanceTest`.
