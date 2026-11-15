# ADR-0005: Autenticación

| Campo      | Valor                                                        |
|------------|--------------------------------------------------------------|
| Estado     | Aceptado                                                     |
| Fecha      | 14/11/2026                                                   |
| Requisitos | REQ-09, REQ-10                                               |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), decisión D-07     |

## Contexto

La consigna pide un login seguro y que solo los usuarios autenticados accedan a los listados. La aplicación es una API sin interfaz gráfica, que se prueba desde Swagger. En el MVP hay un solo tipo de usuario; el rol de administrador llega en el ciclo 2.

## Decisión

Se usa **JWT sin estado, con el soporte que trae Spring Security** para emitir y validar tokens.

- `POST /api/v1/auth/login` recibe usuario y contraseña y devuelve un token firmado, con vencimiento configurable (60 minutos por defecto).
- Los demás pedidos envían el token en la cabecera `Authorization: Bearer`.
- La clave de firma viene de una variable de entorno. Nunca está en el repositorio.
- Las contraseñas se guardan cifradas con BCrypt.
- **Todo está cerrado por defecto.** Solo son públicos el login, la documentación de la API y el control de estado.
- El error del login es el mismo si falla el usuario o la contraseña, para no revelar qué usuarios existen.

## Alternativas consideradas

| Alternativa                                   | A favor                                                    | En contra                                                             |
|-----------------------------------------------|------------------------------------------------------------|-----------------------------------------------------------------------|
| **JWT con el soporte de Spring Security**     | Sin estado; usa componentes ya probados en vez de código de seguridad propio | Un token no se puede anular antes de que venza |
| JWT con una librería aparte y un filtro propio | Muy difundido en tutoriales                               | Más código de seguridad escrito a mano, que es donde más errores aparecen |
| Sesión en el servidor con *cookies*           | Permite cerrar la sesión en el momento                     | Guarda estado en el servidor; incómodo para probar desde Swagger       |

## Consecuencias

- El servidor no guarda sesiones: cualquier instancia puede validar un token.
- Un token robado sirve hasta que vence. El vencimiento corto acota ese riesgo.
- "Cerrar sesión" (REQ-33, ciclo 3) va a necesitar una lista de tokens anulados, con su propio ADR.
- El token ya incluye el rol del usuario, así que sumar el rol de administrador (REQ-25, ciclo 2) no cambia el mecanismo.
- Un pedido nuevo que no tenga una regla explícita exige autenticación.

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **15/11/2026.** Precisiones que aparecieron al implementar el login (ticket #6):
  - **Dependencia.** El soporte de Spring Security para JWT viene en `spring-boot-starter-security-oauth2-resource-server`. Trae la firma y la validación de tokens; el login usa solo la firma, y la validación se configura con el acceso protegido (REQ-10).
  - **Comprobación de la contraseña.** La hace el propio login con el `PasswordEncoder` de Spring Security (BCrypt): busca al usuario y compara. Se evaluó usar el `AuthenticationManager` de Spring Security, que hace lo mismo, y se descartó porque pide más piezas para el mismo resultado. Lo que sí se tomó de él es una precaución: la comparación se hace también cuando el usuario no existe, para que el tiempo de respuesta no revele qué usuarios existen.
  - **Clave de firma.** Se comprueba al arrancar: si `JWT_SECRET` falta o tiene menos de 32 caracteres, la aplicación no arranca y el mensaje dice por qué. HS256 necesita una clave de al menos 256 bits.
  - **Rutas públicas.** El login se abre solo para `POST` y el control de estado solo para `GET /actuator/health`. La documentación abre `GET /swagger-ui.html`, `/swagger-ui/**` y `/v3/api-docs/**`, que son las rutas que Swagger UI necesita para funcionar.
- **15/11/2026.** Precisiones que aparecieron al validar el token en cada pedido (ticket #7):
  - **Validación.** La hace Spring Security con la misma clave con la que firma el login; las dos partes la toman del mismo lugar, así no pueden quedar con claves distintas. Se acepta solo HS256: un token firmado con otro algoritmo, o que declara no tener firma, se rechaza.
  - **Vencimiento sin tolerancia.** Spring Security acepta por defecto un token hasta 60 segundos después de vencido, pensando en servidores con relojes distintos. Acá el mismo servidor emite y valida, así que no hay diferencia de relojes que tolerar: un token vencido se rechaza desde el primer segundo. La alternativa era dejar el valor por defecto; se descartó porque la especificación técnica dice que un token vencido responde `401`, sin excepciones.
  - **Una sola respuesta para un token rechazado.** Un token mal formado, con la firma alterada o vencido recibe exactamente el mismo `401` que un pedido sin token. La alternativa era un texto propio para cada motivo; se descartó porque el cliente tiene que hacer lo mismo en todos los casos (iniciar sesión de nuevo) y así la respuesta no le cuenta nada a quien está probando tokens.
  - **Todo `401` lleva la cabecera `WWW-Authenticate: Bearer`.** El estándar de HTTP ([RFC 9110](https://www.rfc-editor.org/rfc/rfc9110#section-15.5.2)) la exige en toda respuesta `401`, para decir cómo autenticarse; vale también para el `401` del login. Lleva solo el nombre del esquema. La alternativa era sumar los parámetros que el estándar de los tokens Bearer permite para describir el error; se descartó por el mismo motivo que el punto anterior: no decir por qué se rechazó un token.
  - **Un pedido público con un token rechazado también responde `401`.** Spring Security valida el token antes de mirar las reglas de acceso: un pedido público no necesita token, pero si trae uno, tiene que ser válido. Vale para el login, la documentación y el control de estado. La alternativa era hacer que los pedidos públicos ignoren el token; se descartó porque obliga a escribir a mano una parte del control de acceso, que es justo lo que esta decisión evita. Un cliente con el token vencido tiene que iniciar sesión sin enviarlo.
  - **El rol todavía no se usa.** El token lo lleva, pero ninguna regla lo consulta: en el MVP hay un solo tipo de usuario. Las reglas por rol llegan con el rol de administrador (REQ-25, ciclo 2).
