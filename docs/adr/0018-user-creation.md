# ADR-0018: Alta de usuarios por el administrador

| Campo      | Valor                                                                       |
|------------|-----------------------------------------------------------------------------|
| Estado     | Aceptado                                                                    |
| Fecha      | 15/11/2026                                                                  |
| Requisitos | REQ-26, REQ-09                                                              |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), sección 4 (decisiones postergadas) |

## Contexto

El PRD reserva al administrador una operación: crear usuarios nuevos, "para dar acceso sin depender de la carga inicial" (REQ-26, pregunta 3). Es el primer pedido de la API que escribe algo: hasta ahora la tabla `users` solo se lee, y sus filas llegan con la carga inicial.

Qué condiciona la decisión:

- Quién puede hacerlo ya está decidido: solo el rol `ADMIN` ([ADR-0017](0017-role-based-access.md)).
- La columna del nombre de usuario admite 50 caracteres y es única, distinguiendo mayúsculas: `Leia` y `leia` serían dos usuarios.
- El login ya limita lo que recibe: usuario de hasta 50 caracteres y sin caracteres de control, contraseña de hasta 72 ([ADR-0005](0005-authentication.md), nota del 15/11/2026).
- BCrypt lee como máximo 72 bytes de una contraseña. Comprobado el 15/11/2026 con la versión de Spring Security del proyecto: al **cifrar** una contraseña más larga, falla con un error, también si tiene 72 caracteres o menos pero ocupa más de 72 bytes (una de 40 letras con tilde ocupa 80). Sin una validación propia, eso sería un `500`.
- La contraseña de prueba que ya publica el README tiene 8 caracteres.
- El registro público (REQ-34) y cualquier otra operación sobre usuarios (listar, modificar, borrar) no son parte de este requisito.

## Decisión

| Tema                 | Decisión                                                                                                          |
|----------------------|-------------------------------------------------------------------------------------------------------------------|
| Pedido               | **`POST /api/v1/users`**, con `username`, `password` y, opcional, `role`                                           |
| Nombre de usuario    | De 3 a 50 caracteres; solo letras minúsculas sin tilde, dígitos, punto, guion y guion bajo                         |
| Contraseña           | **De 8 a 72 caracteres, y no más de 72 bytes.** Sin reglas de composición. Se guarda cifrada con BCrypt, tal como llegó |
| Rol                  | `USER` o `ADMIN`. **Si no se envía, `USER`**                                                                       |
| Respuesta            | **`201`** con `username`, `role` y `createdAt`. Nunca la contraseña ni su *hash*                                  |
| Usuario ya existente | **`409`**, en Problem Details                                                                                     |
| Dato inválido        | `400`, con el detalle campo por campo ([ADR-0016](0016-validation-error-detail.md)). No se corrige nada en silencio |
| Lo que no entra      | Listar, consultar, modificar o borrar usuarios                                                                    |

El usuario creado puede iniciar sesión en el momento.

## Alternativas consideradas

Ruta:

| Alternativa                          | A favor                                                                                  | En contra                                                                                   |
|--------------------------------------|------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **`POST /api/v1/users`**             | Es la forma habitual de crear un recurso; deja el lugar natural para sumar otras operaciones sobre usuarios | — |
| `POST /api/v1/auth/users`            | Queda bajo el mismo prefijo que el login                                                 | Mezcla "iniciar sesión" con "administrar usuarios", que son cosas distintas                  |
| `POST /api/v1/auth/register`         | Nombre conocido                                                                          | Es el nombre del registro público, que es otro requisito (REQ-34) y tendría otras reglas     |

Nombre de usuario:

| Alternativa                          | A favor                                                                                  | En contra                                                                                   |
|--------------------------------------|------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **Solo minúsculas, dígitos y `. _ -`** | No pueden existir dos usuarios que difieran solo en mayúsculas; nada que normalizar; sin espacios ni caracteres invisibles | No admite mayúsculas ni tildes |
| Cualquier texto de hasta 50 caracteres, como acepta el login | La regla más amplia                                              | `Leia` y `leia` serían dos usuarios; admite espacios y caracteres que se ven igual           |
| Aceptar mayúsculas y guardarlo en minúsculas | Más tolerante                                                                    | Corrige en silencio lo que se envió, contra el criterio del [ADR-0013](0013-parameter-limits-and-invalid-values.md); el login tendría que hacer lo mismo |

Contraseña:

| Alternativa                          | A favor                                                                                  | En contra                                                                                   |
|--------------------------------------|------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **Solo largo: de 8 a 72**            | Una regla que se explica en una línea; sigue a la guía de referencia ([NIST SP 800-63B](https://pages.nist.gov/800-63-4/sp800-63b.html), sección 3.1.1.2) en lo principal: exigir largo y no composición. Las contraseñas de prueba que publica el README la cumplen | **El mínimo queda por debajo de esa guía,** que en su revisión vigente pide 15 caracteres cuando la contraseña es el único factor, y deja 8 para cuando hay un segundo |
| Solo largo: de 15 a 72               | Cumple la guía vigente tal cual                                                          | Las contraseñas de prueba (`demo1234`) no cumplirían la regla que la propia API exige; más incómodo de probar a mano |
| Largo y composición (mayúscula, dígito, símbolo) | Es lo que mucha gente espera ver                                               | La misma guía lo prohíbe: lleva a contraseñas previsibles; más reglas y más textos de error |
| Sin mínimo                           | Nada que validar                                                                         | Permite una contraseña de un carácter                                                        |

Rol:

| Alternativa                          | A favor                                                                                  | En contra                                                                                   |
|--------------------------------------|------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **Opcional, `USER` por defecto; se puede crear un `ADMIN`** | El caso común no necesita escribirlo; no deja a la aplicación con un único administrador posible | Un administrador puede crear a otro |
| Siempre `USER`                       | La más simple y la más restrictiva                                                       | El único administrador sería el de la carga inicial; no se podría crear otro sin tocar la base de datos |
| Obligatorio                          | Nada implícito                                                                           | Un campo más que escribir para el caso de siempre                                            |

Respuesta:

| Alternativa                          | A favor                                                                                  | En contra                                                                                   |
|--------------------------------------|------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **`201` con `username`, `role` y `createdAt`** | Confirma lo que se creó, incluido el rol que se aplicó por defecto              | —                                                                                           |
| `201` sin cuerpo                     | Mínima                                                                                   | Quien no envió el rol no ve cuál quedó                                                       |
| Incluir el identificador numérico    | Es lo habitual cuando existe un pedido para consultar el recurso                         | No hay ningún pedido que lo use: a un usuario se lo identifica por su nombre, que es lo que lleva el token |

## Consecuencias

- Se puede dar acceso a alguien sin tocar la base de datos ni la carga inicial.
- La API deja de ser de solo lectura en un punto: este pedido escribe en la tabla `users`. Los datos de Star Wars siguen siendo de solo lectura.
- El nombre de usuario del alta es más estricto que lo que acepta el login. No es una contradicción: el login rechaza lo que no puede ser de ningún usuario; el alta define cómo es un usuario nuevo. `demo` y `admin` cumplen la regla.
- La respuesta no lleva la cabecera `Location`: no existe un pedido para consultar un usuario, y no se anuncia una dirección que no responde.
- El `409` le dice a un administrador que ese nombre ya existe. No contradice la regla del login de no revelar qué usuarios existen: esa regla protege un pedido público, y este exige el rol de administrador.
- Dos altas del mismo nombre que llegan a la vez no crean dos usuarios: la base de datos lo impide, y la segunda recibe el mismo `409`.
- El mínimo de 8 caracteres es una concesión declarada: alcanza para una aplicación de evaluación con contraseñas de prueba publicadas, y no para un sistema real sin segundo factor. Subirlo es cambiar un número y el texto de un error; sería un cambio incompatible solo para quien quiera crear usuarios con contraseñas cortas.
- La contraseña no se recorta ni se modifica: los espacios cuentan. Viaja en el cuerpo del pedido, igual que en el login, y no se escribe en el registro de la aplicación ni vuelve en ninguna respuesta.
- No hay forma de cambiar una contraseña, ni de borrar o listar usuarios. Es un límite declarado: el requisito pide el alta. Cualquiera de esas operaciones se suma después como un pedido nuevo, sin romper este.
- El costo de BCrypt hace que cada alta tarde unas décimas de segundo. No hay límite de cantidad de altas; el límite de pedidos por usuario es el REQ-31, del ciclo 3.
- La documentación de la API muestra el pedido en un grupo propio, "Users", con candado, y dice que exige el rol de administrador.
