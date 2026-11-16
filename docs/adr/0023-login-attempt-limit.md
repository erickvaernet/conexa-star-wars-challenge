# ADR-0023: Límite de intentos de login: cinco fallos por dirección de origen en quince minutos

| Campo      | Valor                                                                       |
|------------|-----------------------------------------------------------------------------|
| Estado     | Aceptado                                                                    |
| Fecha      | 15/11/2026                                                                  |
| Requisitos | REQ-29, REQ-09, REQ-20                                                      |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), sección 4 (decisiones postergadas: límites de pedidos) |

## Contexto

El PRD pide "frenar a quien prueba muchas contraseñas seguidas, para que no pueda adivinarlas" (REQ-29). Refuerza el login seguro (REQ-09).

Qué hay hoy:

- **El login no tiene ningún límite.** Cada intento compara la contraseña con BCrypt, que es lento a propósito, también cuando el usuario no existe ([ADR-0005](0005-authentication.md)). Eso encarece cada intento, pero no los cuenta: quien quiera puede probar sin parar.
- **El login solo usa la base de datos.** No depende de SWAPI (CA-16.4).
- **Caffeine ya es una dependencia,** por la caché de SWAPI ([ADR-0011](0011-swapi-response-cache.md)): una tabla en memoria con vencimiento y tope de tamaño.
- El RFC ya anticipaba que estos límites "se guardarían en memoria" y que con varias instancias haría falta un almacén compartido.

Qué condiciona la decisión:

- **Los usuarios de prueba y sus contraseñas son públicos,** en el README y en la instalación pública ([ADR-0015](0015-public-deployment.md)). Cualquiera conoce el nombre de `demo` y de `admin`. Un límite por usuario le daría a cualquiera la forma de dejar a quien corrige sin poder entrar: cinco intentos fallidos como `demo` y `demo` queda bloqueado para todos.
- **Por lo mismo, un login correcto está al alcance de cualquiera.** Si un login correcto borrara la cuenta de fallos, alcanzaría con intercalar uno como `demo` cada cuatro intentos para probar contraseñas de `admin` sin límite.
- Hay una sola instancia, que en el despliegue público se duerme a los 15 minutos sin uso y pierde lo que tiene en memoria.
- El `401` del login es idéntico para un usuario que no existe y para una contraseña equivocada (CA-09.3). Eso no puede cambiar.

**Qué dirección ve la aplicación.** Medido el 15/11/2026 con la aplicación levantada con `docker compose`, con y sin la variable que usa el despliegue público, `SERVER_FORWARD_HEADERS_STRATEGY=framework` ([ADR-0015](0015-public-deployment.md)):

| Qué envía el cliente                                  | Sin la variable (local)        | Con `framework` (despliegue público)      |
|-------------------------------------------------------|--------------------------------|-------------------------------------------|
| Ninguna cabecera                                      | La dirección de la conexión    | La dirección de la conexión               |
| `X-Forwarded-For: 203.0.113.7`                        | La dirección de la conexión    | `203.0.113.7`                             |
| `X-Forwarded-For: 198.51.100.99, 203.0.113.7`         | La dirección de la conexión    | `198.51.100.99`: la **primera** de la lista |
| `Forwarded: for=192.0.2.60`                           | La dirección de la conexión    | `192.0.2.60`                              |
| `X-Forwarded-For: no-es-una-direccion`                | La dirección de la conexión    | `no-es-una-direccion`: el texto tal cual  |

- **Sin la variable, las cabeceras se ignoran:** un cliente no puede elegir su dirección. Con `docker compose`, todos los pedidos que salen de la misma máquina llegan con una misma dirección, la de la red de contenedores.
- **Con la variable, la aplicación le cree a la cabecera:** toma el primer valor de `X-Forwarded-For` (o el de `Forwarded`), sin comprobar que sea una dirección. Si no llega ninguna cabecera, usa la dirección de la conexión.
- **En el despliegue público, entonces, todo depende de cómo arma esa cabecera el intermediario de Render,** y eso no se pudo medir: el servicio todavía no existe. Hay tres casos posibles:
  - Render descarta lo que mandó el cliente y escribe la dirección real: cada cliente tiene la suya y no la puede elegir.
  - Render conserva lo que mandó el cliente y agrega la dirección real al final: el primer valor es el del cliente, que entonces **elige su "dirección"** en cada pedido. Es lo que describe un informe de 2021 en el sitio de sugerencias de Render; no se encontró documentación oficial que lo confirme ni lo desmienta.
  - Render no envía la cabecera: todos los clientes compartirían la dirección del intermediario. No hay indicios de que sea así.

## Decisión

Se cuentan los **intentos fallidos de login por dirección de origen** del pedido. Con **cinco fallos en una ventana de quince minutos**, todo login desde esa dirección responde `429` hasta que la ventana vence.

| Tema                    | Decisión                                                                                                         |
|-------------------------|------------------------------------------------------------------------------------------------------------------|
| Qué se cuenta           | Los logins que responden `401`: el usuario no existe o la contraseña no coincide. No se cuenta por usuario        |
| Qué **no** se cuenta    | Un login correcto, y un pedido que se rechaza antes de mirar las credenciales: un cuerpo inválido (`400`) o que no es JSON (`400`, `415`) |
| Por qué dirección       | La dirección de origen que ve la aplicación: la de la conexión o, donde un intermediario la informa y la aplicación está configurada para leerla, la que informa. Se guarda como texto, recortada a 45 caracteres |
| Umbral y ventana        | Cinco fallos. La ventana empieza con el primer fallo de una dirección y dura quince minutos; no se corre con los fallos siguientes ni con los pedidos bloqueados |
| El bloqueo              | Desde el quinto fallo, todo login desde esa dirección responde `429`, **sin comprobar la contraseña** y sin consultar la base de datos, aunque las credenciales sean correctas. Termina cuando vence la ventana, y la cuenta vuelve a cero |
| Un login correcto       | **No borra la cuenta de fallos**                                                                                  |
| La respuesta            | `429` en Problem Details, con un texto fijo, y la cabecera `Retry-After` con los segundos que faltan para que venza la ventana. No lleva `WWW-Authenticate` |
| El `401`                | No cambia: mismo texto, y no dice cuántos intentos quedan                                                         |
| Orden                   | Primero la forma del pedido (`400`, `415`), después el límite (`429`) y recién entonces las credenciales (`401`)   |
| Pedidos simultáneos     | Un intento ocupa su lugar en la cuenta antes de comprobar la contraseña, y lo libera solo si el login resulta correcto: de los intentos que llegan a la vez desde una dirección, a lo sumo cinco llegan a comprobar una contraseña |
| Dónde se guarda         | En memoria, con Caffeine, con un tope de 10 000 direcciones                                                       |
| Configuración           | `LOGIN_MAX_FAILED_ATTEMPTS` (por defecto `5`; de `0` a `100`; con `0` el límite se apaga) y `LOGIN_FAILED_ATTEMPTS_WINDOW` (por defecto `15m`; mayor que cero y hasta `24h`). Se validan al arrancar, como los ajustes de SWAPI |
| Dónde vive              | En `auth`, dentro del login. El `429` sale de `shared`, por el mismo lugar que los demás errores                   |

## Alternativas consideradas

Qué se cuenta y por qué clave:

| Alternativa                                         | A favor                                                                 | En contra                                                                                          |
|-----------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **Fallos por dirección de origen**                  | Nadie puede bloquearle un usuario a otro; frena a quien prueba muchas contraseñas desde un lugar, que es lo que pide la historia | No frena un ataque repartido entre muchas direcciones; quienes comparten una dirección comparten la cuenta; depende de que la dirección sea confiable (ver "Consecuencias") |
| Bloquear la cuenta del usuario tras varios fallos   | Frena el ataque a una cuenta venga de donde venga; es lo más conocido   | Con `demo` y `admin` públicos, cualquiera deja a quien corrige sin poder entrar con cinco pedidos. Además revela qué usuarios existen, salvo que también se "bloqueen" los que no existen |
| Fallos por dirección y usuario a la vez             | Un vecino de dirección que falla con otro usuario no afecta              | Permite probar una misma contraseña contra muchos usuarios sin llegar nunca al umbral; la clave incluye un texto que elige el cliente |
| Contar todos los logins, no solo los fallidos       | Más simple: no hace falta saber cómo terminó el intento                  | Bloquea a quien usa bien la API: los tests de un cliente o quien recorre la colección de pedidos inician sesión muchas veces |

Qué pasa al llegar al umbral:

| Alternativa                                         | A favor                                                                 | En contra                                                                                          |
|-----------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **Bloqueo hasta que vence la ventana, con `429` y `Retry-After`** | Regla fácil de explicar y de probar; quien queda bloqueado sabe cuánto esperar; un pedido bloqueado no gasta BCrypt | Quien se equivoca cinco veces espera hasta quince minutos |
| Demoras crecientes en vez de bloqueo                | No deja a nadie afuera del todo                                          | Cada pedido demorado ocupa una conexión mientras espera; es más difícil de probar y de explicar; a quien ataca le alcanza con abrir más conexiones |
| CAPTCHA                                             | Distingue a una persona de un programa                                   | La API no tiene interfaz gráfica (PRD, fuera del alcance); pide un servicio externo y una cuenta      |
| Ventana que se corre con cada fallo                 | Castiga más a quien insiste                                              | `Retry-After` deja de ser cierto si alguien sigue probando; quien comparte la dirección con un atacante no saldría nunca del bloqueo |

Un login correcto:

| Alternativa                                         | A favor                                                                 | En contra                                                                                          |
|-----------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **No borra la cuenta**                              | Con un usuario de prueba público, intercalar un login correcto no sirve para seguir probando | Quien se equivocó cuatro veces y después acertó sigue a un fallo del bloqueo hasta que vence la ventana |
| Borra la cuenta                                     | Es lo habitual: perdona a quien se equivocó y después acertó             | Con `demo` público, anula el límite: un login correcto cada cuatro fallos y se prueba sin fin        |

Qué cuenta como fallo:

| Alternativa                                         | A favor                                                                 | En contra                                                                                          |
|-----------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **Solo el `401` por credenciales**                  | Es lo único que prueba una contraseña; un cliente con un error de formato no se bloquea solo | Un cliente que manda cuerpos inválidos sin parar no tiene límite; eso es materia del REQ-31 |
| También el `400` por un cuerpo inválido             | Frena antes a un cliente ruidoso                                         | Un `400` no compara ninguna contraseña: no acerca a nadie a adivinarla, y contarlo bloquearía por errores de formato |

Dónde se guarda y con qué:

| Alternativa                                         | A favor                                                                 | En contra                                                                                          |
|-----------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **En memoria, con Caffeine**                        | Ya es una dependencia; vencimiento y tope de tamaño resueltos; ni una consulta más por login | Se pierde al reiniciar y no se comparte entre instancias |
| En la base de datos                                 | Sobrevive a un reinicio y se comparte entre instancias                   | Una escritura por cada intento fallido, que es justo lo que manda quien ataca; una tabla y una limpieza más; hay una sola instancia |
| Una librería de límites de pedidos (Bucket4j, Resilience4j) | Resuelven límites más generales, por usuario y por ruta          | Una dependencia nueva para un contador; el límite general de pedidos es el REQ-31, que no entra en este ciclo |
| Dejarlo al intermediario del despliegue             | Sin código                                                               | No existe en local ni en `docker compose`, y no se comprobó que el plan gratuito lo ofrezca          |

## Consecuencias

Qué frena y qué no:

| Situación                                                        | ¿La frena? |
|------------------------------------------------------------------|------------|
| Alguien prueba contraseñas una tras otra desde una dirección      | **Sí.** Cinco intentos cada quince minutos, 480 por día. Hoy lo único que limita la cantidad es lo que tarda BCrypt en cada uno |
| Lo mismo, intercalando logins correctos de `demo`                 | **Sí:** el login correcto no borra la cuenta |
| Lo mismo, con muchos pedidos a la vez                             | **Sí:** el intento se cuenta antes de comprobar la contraseña |
| Alguien quiere dejar a otro sin poder entrar con `demo` o `admin` | **No puede** por esta vía: el bloqueo es de su propia dirección, no del usuario. La excepción es la fila de la cabecera falsificada |
| Un ataque repartido entre muchas direcciones                      | **No.** Cada dirección tiene sus cinco intentos. Lo que sigue encareciendo cada intento es BCrypt |
| Varios clientes detrás de una misma dirección (una oficina, una red móvil) | Comparten la cuenta: cinco fallos entre todos bloquean el login de todos por hasta quince minutos. Las sesiones ya iniciadas siguen funcionando: el límite es solo del login |
| Una cabecera `X-Forwarded-For` falsificada, sin intermediario (local, `docker compose`) | **No tiene efecto:** sin la variable del despliegue, la aplicación la ignora |
| Una cabecera falsificada, en el despliegue público                | **Depende del intermediario de Render, y no está medido.** Si conserva lo que manda el cliente, quien ataca cambia de "dirección" en cada pedido y el límite no lo frena; y quien conozca la dirección de otra persona puede gastarle sus cinco intentos y bloquearle el login por quince minutos |

- **El límite es una barrera contra el caso simple, no una defensa completa,** y así se presenta en el README. La contraseña de `admin` es pública de todos modos: en esta instalación no hay nada que adivinar. El límite muestra el mecanismo y protege a los usuarios que se crean con el alta.
- **Lo que hay que medir con el servicio creado** (ticket #34), porque de eso depende la última fila: desde un mismo cliente, cinco logins fallidos con `X-Forwarded-For: 198.51.100.1` y un sexto sin la cabecera. Si el sexto responde `429`, Render no deja elegir la dirección; si responde `401`, la deja elegir. El resultado se anota acá.
- **Si resulta que el cliente elige su dirección,** la corrección no es de este límite sino de cómo se lee la cabecera: tomar el valor que agregó el intermediario y no el primero. Tomcat trae una pieza que lee la lista desde el final y descarta lo que no viene de un intermediario conocido; usarla cambia la variable que eligió el [ADR-0015](0015-public-deployment.md), y se decidiría con esa medición a la vista, en una nota o en un ADR nuevo.
- **Si resultara que Render no envía la cabecera,** todos los visitantes compartirían una dirección y cinco fallos de cualquiera bloquearían el login de todos. La misma medición lo muestra: después de los cinco fallos, un login desde otra red respondería `429`. En ese caso el límite se apaga en el despliegue con `LOGIN_MAX_FAILED_ATTEMPTS=0` hasta corregirlo.
- **Quien se equivoca cinco veces espera hasta quince minutos,** aunque después escriba bien la contraseña. La respuesta dice cuántos segundos faltan. Vale también para quien corrige: el README lo avisa junto a las credenciales de prueba.
- **La cuenta vive en la memoria de la única instancia.** Se pierde cuando la aplicación se reinicia, se despliega o, en el plan gratuito, se duerme: un bloqueo no sobrevive a eso. Con más de una instancia cada una contaría por su lado; haría falta un almacén compartido, como Redis, y un ADR nuevo.
- **La memoria está acotada:** como mucho 10 000 direcciones, cada una con un texto de hasta 45 caracteres y un contador. El recorte importa porque, con la cabecera, la "dirección" puede ser cualquier texto. Si la tabla se llena, las direcciones nuevas pueden quedar sin contar: ante una avalancha, el límite cede antes que la memoria.
- **Un pedido bloqueado es barato:** no consulta la base de datos ni calcula BCrypt. En la instancia gratuita, con una décima de procesador, eso también protege a los demás pedidos.
- El contrato del login suma una respuesta, el `429`, con su ejemplo en la documentación de la API. El `400` y el `401` no cambian, ni sus criterios de aceptación (CA-09.x y CA-21.x).
- **Los tests comparten la aplicación levantada y la misma dirección de origen.** Los que hoy hacen logins fallidos a propósito suman tres en una aplicación y uno en otra, y no llegan a cinco; los tests nuevos de este límite vacían la cuenta antes y después de cada caso, y mueven un reloj de prueba en vez de esperar quince minutos, como los de la caché.
- El límite se apaga con una variable, sin tocar el código. `render.yaml` no cambia: el despliegue público usa los valores por defecto.
- El límite general de pedidos por usuario (REQ-31) sigue sin hacerse. Si se hiciera, convendría que los dos límites compartieran una sola pieza; se decidiría en su ADR.
