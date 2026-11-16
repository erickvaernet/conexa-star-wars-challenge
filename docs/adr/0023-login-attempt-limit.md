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

## Notas posteriores

- **15/11/2026.** Precisiones que aparecieron al implementar el límite (ticket #50). Las alternativas de cada una se le presentaron a la sesión supervisora, que eligió.
  - **Dónde quedó cada pieza.** La cuenta está en `auth/LoginAttemptLimiter`, y sus dos ajustes, en `auth/LoginAttemptProperties`. El login la consulta desde `auth/LoginService`, y el pedido le pasa la dirección que informa (`getRemoteAddr`), sin leer ninguna cabecera. El `429` sale de `shared/ApiExceptionHandler`, que recibe de `auth` solo los segundos de espera.
  - **La cuenta son lugares.** Cada dirección tiene tantos lugares como el umbral. Un login ocupa uno antes de comprobar nada y lo devuelve si resulta correcto; uno fallido se lo queda. Con todos ocupados, el login responde `429`. Cada cambio de la cuenta lo hace un pedido por vez. Se evaluó hacerlo atómico por dirección, con la operación de la tabla que calcula y guarda en un paso: evita que dos direcciones se esperen entre sí, pero cada cambio dura microsegundos al lado de lo que tarda BCrypt, y el código de un pedido por vez se lee de corrido.
  - **Consecuencia de ocupar el lugar antes de comprobar.** Con cuatro fallos contados, dos logins correctos que llegan a la vez desde esa dirección pueden terminar uno en `200` y el otro en `429`: el primero ocupa el quinto lugar mientras compara su contraseña. Dura lo que tarda esa comparación, y al reintentar responde `200`. Es el precio de que, de los intentos simultáneos, a lo sumo cinco lleguen a comparar una contraseña. Está también entre los casos límite de la [especificación técnica](../spec/SPEC.md#casos-límite).
  - **Un login que termina en un error inesperado no cuenta.** Devuelve su lugar, igual que uno correcto: lo que se cuenta son los `401`.
  - **Un login correcto, solo, no abre una ventana.** Cuando una dirección se queda sin lugares ocupados, se la olvida. La ventana empieza con el primer lugar que queda ocupado.
  - **`Retry-After` se redondea hacia arriba, con un mínimo de 1.** Quien espera lo que dice la cabecera encuentra la ventana vencida, y nunca se le dice que espere cero segundos. La alternativa, redondear hacia abajo, haría que el login siguiente todavía respondiera `429`.
  - **El reloj es el mismo que lee la caché de SWAPI** ([ADR-0011](0011-swapi-response-cache.md)): el del sistema, salvo que un test declare el suyo. La ventana se mide con la hora del primer fallo, guardada junto a la cuenta. El vencimiento de la tabla de Caffeine queda solo para liberar memoria: se reinicia con cada escritura, así que no sirve para medir la ventana. Se evaluó un reloj propio del límite; habrían sido dos relojes que adelantar en cada test.
  - **La tabla descarta en el momento lo que no entra.** Caffeine, por defecto, lo hace después, en otro hilo; acá lo hace el mismo pedido que escribe, para que el tope de 10 000 direcciones valga en todo momento y el test que lo comprueba no dependa de cuándo corre ese otro hilo.
  - **Las dos variables se leen como texto.** Así, un valor que no se puede leer (`LOGIN_MAX_FAILED_ATTEMPTS=muchos`) recibe el mismo mensaje que uno fuera de rango, el que nombra la variable; el mensaje de Spring nombra el ajuste interno. Es la única diferencia con las clases de ajustes de SWAPI.
  - **La línea del registro** se escribe cuando se confirma el fallo que deja bloqueada a una dirección. Lleva la dirección y nada más: ni el usuario ni la contraseña. La dirección va recortada a 45 caracteres y con todo carácter que no sea un dígito, una letra de la `a` a la `f`, un punto o dos puntos reemplazado por `?`: en el despliegue público ese texto lo puede elegir el cliente, y tal cual podría traer saltos de línea y escribir líneas propias en el registro. Si los fallos que bloquean llegan a la vez, la línea puede repetirse.
  - **Un pedido sin dirección** se cuenta bajo un mismo nombre fijo, en vez de fallar.
  - **Los tests vacían la cuenta** con un método de `LoginAttemptLimiter` que la aplicación no llama nunca, y que existe para eso: los tests de aceptación comparten la aplicación levantada y la dirección. Se evaluó que la tabla fuera una pieza a la vista del resto de la aplicación, como la caché de SWAPI, que los tests ya vacían; se descartó porque quince clases de test habrían pasado a vaciar la cuenta sin decirlo.
  - **Un borde conocido, que no se cubre.** Un login correcto que empieza en una ventana y termina cuando otro pedido ya abrió la siguiente devuelve un lugar de la ventana nueva. En el peor caso, un fallo queda sin contar por cada ventana. Cubrirlo pide que cada login recuerde a qué ventana pertenece su lugar; no se justifica.
  - **CA-29.18.** El criterio decía que el cuerpo es igual al ejemplo "salvo `instance`, que el ejemplo no trae". El ejemplo que se lee de la documentación de la API sí lo trae: se escribe sin ese campo y la documentación lo completa con la dirección de su pedido, como en todos los errores. El test compara el cuerpo entero, y la frase del criterio se corrigió.
  - **Lo que sigue sin medirse** es lo mismo que al decidir: cómo arma el intermediario de Render la cabecera con la dirección del cliente. La prueba está en "Consecuencias" y se hace con el servicio creado (ticket #34).
- **15/11/2026.** Se hizo la prueba que este ADR dejaba para cuando existiera el servicio ([ADR-0015](0015-public-deployment.md)), contra el despliegue público con este límite ya desplegado. **El resultado es el caso malo: en el despliegue público, la dirección la elige el cliente.**
  - **La prueba.** Desde un mismo cliente: cinco logins fallidos con `X-Forwarded-For: 198.51.100.1`, que respondieron `401`; un sexto con la misma cabecera, que respondió `429` con `Retry-After: 896`; un login correcto con `X-Forwarded-For: 198.51.100.2`, que respondió `200`; y un login correcto sin la cabecera, que respondió `200`. El registro del servicio escribió la advertencia del bloqueo con la dirección `198.51.100.1`.
  - **Qué muestra.** El intermediario de Render conserva lo que el cliente escribe en esa cabecera, y la aplicación, configurada para leerla, cuenta por su primer valor. El límite funciona, pero cuenta por un texto que el cliente controla.
  - **Qué significa en el despliegue público.** Quien prueba contraseñas puede cambiar la cabecera en cada pedido, y el límite no lo frena. Y quien conoce la dirección de otro puede hacer cinco logins fallidos a su nombre y dejarlo sin poder iniciar sesión hasta quince minutos. No hay un bloqueo para todos: cada cliente que no manda la cabecera cuenta por su propia dirección.
  - **Qué no cambia.** Sin intermediario, como en `docker compose`, la cabecera se ignora y el límite cuenta por la dirección de la conexión (CA-29.15). Y el despliegue no queda peor que antes de tener el límite: hasta este ciclo no había ninguno.
  - **Qué se decidió.** No corregirlo en este ciclo y dejarlo dicho. La corrección es leer la dirección que escribe el intermediario y no la primera de la lista: cambia la variable del ADR-0015, el criterio CA-29.16 y su test, y hay que volver a medir en el servicio. Un error en esa configuración dejaría a todos los clientes bajo una misma dirección, que es peor que lo que hay: cinco fallos de cualquiera bloquearían el login de todos. No se hace a horas de la entrega; queda como la primera mejora pendiente de este límite.
- **16/11/2026.** Lo que encontró sobre este límite la revisión final del ciclo 3, que lo probó ya fusionado, y qué cambió. Qué corregir lo eligió la sesión supervisora.
  - **Logins correctos simultáneos recibían un `429` de bloqueo.** Con la aplicación recién levantada con `docker compose` y sin ningún login fallido, doce logins correctos de `demo` enviados a la vez recibieron cinco `200` y siete `429`, con el texto de los intentos fallidos y `Retry-After: 900`. Un login hecho un instante después respondió `200`. La cuenta sumaba en un solo número los fallos y los logins que se estaban comprobando: con cinco en curso, el sexto encontraba la cuenta llena, y la espera se calculaba como si esos cinco lugares fueran fallos. Este ADR contaba solo un caso más angosto, el de cuatro fallos y dos logins correctos a la vez.
  - **Qué cambió: los fallos y los logins en curso se cuentan aparte.** Un login ocupa un lugar en curso antes de comprobar nada; si resulta correcto lo devuelve, y si falla ese lugar pasa a ser un fallo. La regla de admisión es la misma, fallos más logins en curso por debajo del umbral, así que de los intentos simultáneos siguen llegando a comparar una contraseña cinco como mucho. Lo que cambia es la respuesta a quien queda afuera: con cinco fallos, el `429` de siempre, con lo que falta de la ventana; si algún lugar lo ocupa un login que todavía se está comprobando, `429` con `Retry-After: 1`. El texto del `429` no cambia.
    - **Por qué un segundo.** Es el mínimo que este ADR ya fijaba para la cabecera, y es más de lo que tarda una comprobación, también en la instancia gratuita, donde un login tardó entre 0,75 y 1,1 segundos.
    - **Alternativas descartadas.** Dejar pasar al sexto cuando no hay fallos: cuarenta intentos simultáneos compararían cuarenta contraseñas, que es lo que el lugar tomado de antemano evita. Hacerlo esperar a que se libere un lugar: cada pedido en espera ocupa una conexión, lo mismo que este ADR descartó para las demoras crecientes. Otro código u otro texto para este caso: un cambio de contrato para un caso que dura décimas de segundo.
    - **Solo los fallos bloquean y escriben la advertencia.** Los logins en curso que resultan correctos no dejan nada contado ni abren una ventana.
  - **La ventana empieza cuando se confirma el primer fallo,** no cuando ese login ocupó su lugar. La diferencia es lo que tarda en compararse una contraseña.
  - **El borde conocido de la nota anterior ya no está.** Un login correcto devuelve un lugar en curso, no un fallo, así que no puede descontar un fallo de la ventana siguiente. Y un login que ocupó su lugar en una ventana y falla cuando esa ventana ya venció es el primer fallo de una ventana nueva.
  - **El login avisa siempre cómo terminó.** `LoginService` devuelve el lugar o lo confirma como fallo en un bloque que corre pase lo que pase, también ante un error que no sea una excepción común. Antes lo hacía en cada salida por separado. Ahora importa más: un lugar en curso que nadie devuelve no vence con la ventana.
  - **La ventana dura un segundo como mínimo.** `LOGIN_FAILED_ATTEMPTS_WINDOW` aceptaba cualquier tiempo mayor que cero, y un número sin unidad se lee en milisegundos: `15`, escrito pensando en minutos, dejaba una ventana de 15 milisegundos y el límite sin efecto, sin ningún aviso. También valía `1ns`. Ahora el rango es de `1s` a `24h`, y el mensaje de arranque dice que un número solo se lee en milisegundos. Corrige la fila "Configuración" de la decisión, que decía "mayor que cero". Las variables de tiempo de SWAPI se leen igual y no se tocaron: un valor chico ahí se nota en el primer pedido.
  - **Una dirección IPv6 se escribía en el registro como `?2001:db8::1?`.** Donde se leen las cabeceras del intermediario, Spring entrega una dirección IPv6 entre corchetes, y el saneado los reemplazaba. Ahora los deja pasar. No cambia lo que se cuenta, solo cómo se escribe.
  - **Qué pasa de verdad cuando la tabla se llena.** "Consecuencias" dice que "las direcciones nuevas pueden quedar sin contar". Medido con el reloj de prueba, es al revés: con la tabla llena, una dirección nueva se cuenta bien y queda bloqueada a su quinto fallo; lo que se descarta son cuentas de otras direcciones, y una dirección ya bloqueada quedó desbloqueada después de 10 000 direcciones nuevas con un fallo cada una, no después de 9990. Lo que vale, entonces, es: si la tabla se llena, se descartan cuentas de otras direcciones, y una dirección bloqueada puede quedar desbloqueada antes de tiempo. Hace falta presentarse con 10 000 direcciones: sin intermediario no se puede, y en el despliegue público quien puede hacerlo ya cambia de dirección en cada pedido. La memoria con la tabla llena fue de unos 2,3 MB.
  - **El procesador de la instancia gratuita es 0,15, no una décima,** que es lo que dice "Consecuencias": es lo que muestra el panel del servicio ([ADR-0015](0015-public-deployment.md)).
  - **Dos tests que faltaban.** De catorce roturas que la revisión hizo a propósito en este límite, los tests detectaban nueve siempre. Una la detectaban a veces, en seis corridas de ocho: quitar lo que hace que los intentos de una dirección se cuenten de a uno, que es lo que sostiene el tope de cinco comprobaciones. Otra no la detectaba ninguno: que el login no avisara el fallo, con lo que la advertencia del bloqueo no se escribía nunca; el test de la advertencia llamaba al limitador a mano.
    - **Qué se sumó.** Un test que busca la advertencia pasando por el login. Y otro que lanza dieciséis intentos al mismo tiempo sobre el limitador, con una barrera y sin esperas, cuatrocientas veces: exige que cinco ocupen lugar y que los cinco fallos queden contados. Con ellos, las dos roturas se detectaron en todas las corridas que se hicieron, seis de la primera y una de la segunda. Ahora que el fallo se confirma en un paso propio, no avisarlo deja además sin contar los fallos, y lo detectan otros tres tests.
    - **Las otras tres que no se detectaban** no cambian lo que hace el límite: el borde exacto del final de la ventana, que es de un nanosegundo y lo tapa el vencimiento de la tabla; descartar en segundo plano lo que no entra en la tabla; y quitarle el vencimiento a la tabla, que solo cambia cuándo se libera memoria. La segunda desmiente una frase de la nota anterior: el test del tope de 10 000 direcciones pasa igual sin esa pieza.
  - **Lo que no cambió.** Los dieciocho criterios de aceptación y sus tests, que pasan sin tocarse: CA-29.2 pide un `Retry-After` entre 1 y 900. El contrato del `429`. Y lo que quedó pendiente en la nota anterior: en el despliegue público la dirección la sigue eligiendo el cliente. La revisión lo volvió a medir contra el servicio, con el mismo resultado; lo que sumó está en la nota de esta fecha del [ADR-0015](0015-public-deployment.md).
- **16/11/2026.** En su revisión antes de entregar ([registro de uso de herramientas de IA](../process/AI-USAGE.md#revisión-del-autor-antes-de-entregar)), el autor vio la medición de la nota del 15/11/2026 y tres opciones: dejarlo como límite declarado, corregir cómo se lee la dirección, o apagar el límite en el servicio público. **Eligió dejarlo como límite declarado.** Corregirlo pide desplegar antes una prueba para ver qué envía el intermediario, y cambiar esa configuración a ciegas el día de la entrega tiene un peor caso más grave que el problema: todos los visitantes bajo una misma dirección. Sigue como la primera mejora pendiente de este límite, y quedó como ticket abierto, con sus tareas: [#58](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/58).
