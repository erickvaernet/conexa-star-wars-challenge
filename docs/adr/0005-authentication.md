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
