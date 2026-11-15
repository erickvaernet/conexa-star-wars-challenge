# ADR-0006: Usuarios: base de datos y carga inicial

| Campo      | Valor                                                        |
|------------|--------------------------------------------------------------|
| Estado     | Aceptado                                                     |
| Fecha      | 14/11/2026                                                   |
| Requisitos | REQ-11, REQ-19                                               |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), decisión D-08     |

## Contexto

El equipo técnico de Conexa pidió que los usuarios estén en una base de datos, con una carga inicial (*seed*) al levantar el proyecto en local. Los datos de Star Wars no se guardan: la base contiene solo usuarios, en una tabla.

Quien corrige necesita usuarios de prueba listos, y sus credenciales están publicadas en el README.

## Decisión

| Tema               | Decisión                                                                                   |
|--------------------|--------------------------------------------------------------------------------------------|
| Motor              | **PostgreSQL**                                                                             |
| Acceso a datos     | **Spring Data JPA**                                                                        |
| Cambios de esquema | **Flyway**, con archivos SQL versionados. La generación automática de tablas queda apagada |
| Carga inicial      | **Flyway, en una carpeta aparte** de la del esquema, que se activa por configuración       |

La carga inicial es un archivo SQL con los `INSERT` de los usuarios de prueba y su contraseña ya cifrada con BCrypt.

```
src/main/resources/db/
├── migration/   tablas e índices: corre siempre
└── seed/        usuarios de prueba: corre solo si está activada
```

## Alternativas consideradas

| Alternativa                             | A favor                                                  | En contra                                                              |
|-----------------------------------------|----------------------------------------------------------|------------------------------------------------------------------------|
| **PostgreSQL**                          | Base real, como pidió el equipo técnico; la más usada con Spring | Necesita un contenedor                                         |
| H2 en memoria                           | No hay nada que levantar                                 | No es una base real; los datos se pierden al reiniciar                  |
| **Spring Data JPA**                     | Es lo que se espera en un backend Java                   | Para una sola tabla es más de lo necesario                              |
| Spring Data JDBC                        | Más simple y directo                                     | Menos conocido                                                         |
| **Carga inicial con Flyway, carpeta aparte** | Un archivo SQL que se lee de un vistazo; sin código extra; los datos de prueba no quedan atados al esquema | Cambiar la contraseña de prueba exige otro archivo |
| Carga inicial con Flyway, junto al esquema | La más simple                                         | Los datos de prueba correrían en cualquier ambiente                     |
| Componente que crea los usuarios al arrancar | Contraseñas desde variables de entorno              | Código para escribir y probar; no se ve de un vistazo qué carga         |

## Consecuencias

- Al levantar el proyecto ya hay usuarios para iniciar sesión, sin pasos manuales.
- La contraseña de prueba cifrada queda en el repositorio. No es un riesgo: es una credencial de prueba que el README publica para quien corrige.
- En un sistema real, la carpeta `seed` se desactiva y no se crean usuarios de prueba.
- Usar JPA para una sola tabla es un costo aceptado a cambio de usar la herramienta habitual.
- El borrador del RFC proponía el componente al arrancar. Se descartó porque su única ventaja, no dejar contraseñas en el repositorio, no aplica a una credencial que se publica.
- Los tests corren contra PostgreSQL real, con las mismas migraciones (ver [ADR-0009](0009-testing-strategy.md)).

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **15/11/2026.** Precisiones que aparecieron al implementar la carga inicial (ticket #6):
  - **El archivo de la carga inicial es una migración repetible de Flyway** (`R__seed_test_users.sql`), no una numerada. Los datos de prueba no son un paso en la historia del esquema: como repetible, la carga se puede activar en cualquier momento sin chocar con las migraciones numeradas que se agreguen después. La alternativa, una migración numerada dentro de `seed`, obligaba a reservarle un número y fallaba si se activaba con el esquema ya más avanzado.
  - **No duplica.** Flyway la vuelve a correr solo si el archivo cambia, y el `INSERT` deja como está a un usuario que ya existe.
  - **Se activa con `SEED_ENABLED`.** Apagada por defecto; `docker compose` la enciende. Con la variable en `true`, Flyway lee también la carpeta `seed`.
  - **Se puede apagar sobre una base que ya la corrió.** Flyway recuerda las migraciones aplicadas y se niega a arrancar si no encuentra el archivo de una. Se le indicó que ignore ese caso solo para las repetibles. Apagar la carga no borra a los usuarios de prueba ya creados.
- **15/11/2026.** Precisiones que aparecieron al sumar el administrador de prueba y el alta de usuarios (ticket #36):
  - **`admin` está en el mismo archivo que `demo`,** con su propio `INSERT` y su contraseña ya cifrada con BCrypt. No hizo falta una migración nueva: la columna `role` existe desde la primera.
  - **Cómo llega a una base que ya corrió la carga.** El archivo es una migración repetible: al cambiar, Flyway la vuelve a correr al arrancar. El `INSERT` de `demo` no hace nada, porque ya existe, y el de `admin` lo agrega. Es lo que pasa en el despliegue público al actualizarlo.
  - **Cómo se comprueba.** Un test arma una base tal como la dejó la versión `0.1.0`, con Flyway y con una copia del archivo de esa versión, que está entre los recursos de test. Después arranca la aplicación sobre esa base y compara: `admin` existe, y la fila de `demo` es la misma columna por columna. Se eligió eso en vez de insertar a `demo` a mano porque así se prueba el mecanismo real: que Flyway nota el cambio del archivo.
  - **La aplicación ahora también escribe en la tabla.** Hasta acá sus filas solo llegaban con la carga inicial. El alta de usuarios guarda una fila nueva; no hay nada que modifique ni borre una.
  - **La columna única del nombre es la que impide dos usuarios iguales** cuando dos altas llegan a la vez. La aplicación pregunta antes si el nombre existe, pero esa pregunta sola no alcanza: las dos altas pueden hacerla antes de que alguna guarde ([ADR-0018](0018-user-creation.md), nota de esta fecha).
  - **El momento de creación de un usuario dado de alta se guarda con precisión de milisegundos,** para que la respuesta del alta diga exactamente lo guardado. Los de la carga inicial los escribe la base de datos, con la precisión que ella tiene.
