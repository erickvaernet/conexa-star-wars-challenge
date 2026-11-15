# ADR-0017: Roles y acceso por rol

| Campo      | Valor                                                                       |
|------------|-----------------------------------------------------------------------------|
| Estado     | Aceptado                                                                    |
| Fecha      | 15/11/2026                                                                  |
| Requisitos | REQ-25, REQ-10, REQ-11                                                      |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), sección 4 (decisiones postergadas) |

## Contexto

La consigna pide autenticación y autorización. El MVP resolvió la primera: hay sesión o no la hay. El PRD dejó para este ciclo mostrar la autorización por rol (pregunta 2): distinguir a los administradores de los usuarios comunes y reservarles una operación, que es el alta de usuarios (REQ-26, [ADR-0018](0018-user-creation.md)).

Qué dejó preparado el MVP ([ADR-0005](0005-authentication.md)):

- La tabla `users` ya tiene la columna `role`, y el usuario de prueba la tiene en `USER`.
- El token ya lleva el rol del usuario, en un campo `role`.
- Ninguna regla de acceso lo lee todavía.
- Las reglas de acceso están en un solo lugar, cerradas por defecto: lo que no tiene una regla propia exige sesión.

Qué fija el PRD: los dos tipos de usuario pueden usar los listados, y los usuarios de prueba pasan a ser uno de cada tipo.

## Decisión

| Tema                    | Decisión                                                                                                         |
|-------------------------|------------------------------------------------------------------------------------------------------------------|
| Roles                   | **Dos: `USER` y `ADMIN`.** Cada usuario tiene exactamente uno                                                    |
| Qué puede cada uno      | Los dos usan los listados y los detalles. Solo `ADMIN` puede dar de alta usuarios                                |
| Cómo se sabe el rol     | **Se lee del token,** del campo `role` que el login ya escribe. No se consulta la base de datos en cada pedido   |
| Dónde está la regla     | **En las reglas de acceso, junto a las demás:** una línea que reserva `POST /api/v1/users` al rol `ADMIN`         |
| Sin permiso             | **`403`**, en Problem Details, con un texto fijo. Sin sesión sigue siendo `401`                                  |
| Usuarios de prueba      | La carga inicial suma un segundo usuario, **`admin`**, con rol `ADMIN`. `demo` sigue igual, con rol `USER`        |

## Alternativas consideradas

Dónde se escribe la regla:

| Alternativa                                   | A favor                                                                                      | En contra                                                                                   |
|-----------------------------------------------|----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **Una línea en las reglas de acceso**         | Todas las reglas siguen en un solo lugar, que se lee de arriba abajo; no suma ninguna pieza   | La regla queda lejos del controlador al que protege                                          |
| Una anotación sobre el método del controlador | La regla se ve junto al pedido                                                               | Hay que encender otro mecanismo de Spring Security; las reglas quedan repartidas en dos lugares, y una anotación que se olvida deja el pedido abierto a cualquier usuario con sesión |
| Comprobar el rol a mano dentro del servicio   | Sin configuración                                                                            | Es código de seguridad propio, que es lo que el [ADR-0005](0005-authentication.md) evita      |

De dónde sale el rol en cada pedido:

| Alternativa                                   | A favor                                                                                      | En contra                                                                                   |
|-----------------------------------------------|----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **Del token**                                 | Sin estado y sin consultas: es para lo que el login ya lo escribe                            | Si el rol de un usuario cambiara, su token viejo conserva el anterior hasta vencer            |
| De la base de datos, en cada pedido           | Siempre al día                                                                               | Una consulta por pedido, y el servidor deja de ser sin estado para algo que hoy no puede pasar: no existe ninguna operación que cambie un rol |

Modelo de roles:

| Alternativa                                   | A favor                                                                                      | En contra                                                                                   |
|-----------------------------------------------|----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **Un rol por usuario, de dos posibles**       | Es lo que pide el PRD; la columna y el campo del token ya existen                            | No sirve si un usuario necesitara dos roles a la vez                                         |
| Varios roles por usuario, o permisos sueltos  | Más flexible                                                                                 | Otra tabla y otro formato de token para una sola operación reservada                         |

Usuario administrador de prueba:

| Alternativa                                   | A favor                                                                                      | En contra                                                                                   |
|-----------------------------------------------|----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **Sumarlo a la carga inicial que ya existe**  | El mismo mecanismo del [ADR-0006](0006-users-database-and-seed.md): un `INSERT` más en el mismo archivo. En una base que ya corrió la carga, se agrega solo al arrancar | La contraseña de un administrador queda publicada en el README, igual que la de `demo` |
| Crearlo a partir de variables de entorno      | Su contraseña no estaría en el repositorio                                                   | Es el componente al arrancar que el ADR-0006 descartó; quien corrige tendría que configurar algo antes de probar |

## Consecuencias

- La consigna pide autorización, y ahora se puede ver: el mismo pedido responde `201` a un administrador y `403` a un usuario común.
- El mecanismo de autenticación no cambia: mismo login, mismo token, misma validación. Se suma leer un campo que el token ya traía.
- Un pedido nuevo sigue cerrado por defecto. Reservarlo a un rol es una línea más en el mismo lugar.
- **`401` y `403` no se confunden.** Sin token, o con uno rechazado, `401`: hay que iniciar sesión. Con un token válido que no alcanza, `403`: iniciar sesión de nuevo no cambia nada. El `403` usa el mismo formato y pasa por el mismo lugar que el resto de los errores.
- El `403` no lleva la cabecera `WWW-Authenticate`. Spring Security la agrega por defecto con una descripción del error; se quita por el mismo motivo que en el `401` ([ADR-0005](0005-authentication.md), nota del 15/11/2026): la respuesta ya dice lo necesario en su cuerpo.
- Las reglas se evalúan en este orden: primero la sesión (`401`), después el rol (`403`) y recién entonces el contenido del pedido (`400`). Un usuario común que envía un alta mal armada recibe `403`, no `400`.
- La contraseña del administrador de prueba se publica en el README. Es el mismo criterio que con `demo`: una credencial de prueba para quien corrige, que solo existe donde la carga inicial está encendida. La diferencia es que esta permite crear usuarios: en el despliegue público cualquiera que lea el README puede hacerlo. Se acepta, porque es justo lo que quien corrige tiene que poder probar; los usuarios creados no ven nada que `demo` no vea, y no existe ningún pedido que los liste.
- **Con la carga inicial apagada no hay ningún administrador,** y entonces nadie puede crear usuarios. En un sistema real, el primero se crea a mano en la base de datos. Un mecanismo para eso queda fuera del alcance.
- No hace falta cambiar el esquema: la columna `role` existe desde la primera migración. Los valores posibles se controlan en el código; la base de datos no los restringe.
- Un token emitido antes de este cambio sigue valiendo hasta vencer, con el rol que ya traía.
- Si más adelante un rol pudiera cambiar o un usuario pudiera darse de baja, leer el rol del token dejaría una ventana de hasta una hora con el dato viejo. Habría que acortar el vencimiento o consultar la base; se registra en un ADR nuevo.

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **15/11/2026.** Precisiones que aparecieron al implementarlo (ticket #36):
  - **Cómo se lee el rol.** Spring Security convierte cada token en permisos. Por defecto los busca en los campos `scope` y `scp`, que estos tokens no tienen. Se le indicó que lea el campo `role` y que nombre lo que encuentra como él espera un rol. Son dos piezas que ya trae; no se escribió ninguna propia. La regla quedó en una línea, junto a las demás.
  - **Alternativa descartada.** Una regla escrita a mano que lea el campo del token. Hace lo mismo con código de seguridad propio, que es lo que el [ADR-0005](0005-authentication.md) evita.
  - **Solo el nombre exacto del rol abre el pedido.** Un token válido sin el campo `role`, o con `admin`, `ROOT` o cualquier otro valor, recibe `403`. Ningún token así sale del login: lo fijan los tests, por si el formato del token cambiara.
  - **El `403` lo responde el mismo lugar que el resto de los errores.** Las reglas de acceso le pasan el error, igual que hacen con el `401`. Sin eso, Spring Security responde un `403` sin cuerpo y con una cabecera `WWW-Authenticate` que describe el error.
  - **Vale para toda la aplicación, no solo para el alta.** Cualquier pedido que se reserve a un rol más adelante responde el mismo `403`, con el mismo texto. También si un `403` llegara por el reenvío de errores del servidor ([ADR-0007](0007-error-format.md), nota del 15/11/2026): ese camino usa el mismo texto.
  - **El orden sale solo.** El token y la regla del rol se evalúan antes de que la aplicación lea el cuerpo del pedido. Por eso un usuario común recibe `403` también con un cuerpo que no es JSON o que no se envía como JSON, y no un `400` ni un `415`.
  - **Un límite de la forma elegida.** Spring Security separa por espacios el valor del campo: un token con `role` igual a `USER ADMIN` tendría los dos roles. No puede pasar: el login escribe el rol de la base de datos, y ahí solo llegan `USER` o `ADMIN`, por la carga inicial o por el alta, que valida el valor exacto.
