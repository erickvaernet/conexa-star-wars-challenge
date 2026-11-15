# ADR-0015: Despliegue público

| Campo      | Valor                                                                       |
|------------|-----------------------------------------------------------------------------|
| Estado     | Aceptado                                                                    |
| Fecha      | 15/11/2026                                                                  |
| Requisitos | REQ-20, REQ-11                                                              |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), sección 4 (decisiones postergadas) |

## Contexto

El PRD pide una URL pública, sin costo y con los usuarios de prueba cargados, para que quien corrige pueda probar la aplicación sin clonar el repositorio (REQ-20). La consigna nombra Heroku y Vercel como ejemplos; Heroku ya no tiene plan gratuito y Vercel no ejecuta Java, así que vale cualquier servicio gratuito (PRD, pregunta 4).

Qué dejó preparado el MVP ([ADR-0010](0010-local-environment.md)): una imagen de Docker que se construye desde el repositorio, toda la configuración por variables de entorno, un control de estado y la carga de usuarios de prueba, que se enciende con una variable.

Qué necesita la aplicación del servicio que la aloje:

- Construir y ejecutar una imagen a partir del `Dockerfile` del repositorio.
- Una base de datos PostgreSQL.
- Memoria suficiente. Medido el 15/11/2026 con la imagen del MVP: unos 340 MB sin límite, y entre 240 y 275 MB dentro de un contenedor de 512 MB con la memoria de Java acotada. Con 256 MB no alcanza.
- Una sola instancia: la caché de SWAPI vive en memoria ([ADR-0011](0011-swapi-response-cache.md)).

Qué ofrecen hoy los servicios gratuitos. Relevado en sus páginas oficiales el 15/11/2026; los planes gratuitos cambian seguido:

| Servicio          | Qué da gratis                               | Memoria             | ¿Se duerme sin uso?                              | ¿Pide tarjeta? | ¿Construye desde el `Dockerfile`? | ¿Se describe en un archivo del repositorio? |
|-------------------|---------------------------------------------|---------------------|--------------------------------------------------|----------------|-----------------------------------|---------------------------------------------|
| **Render**        | Un servicio web; 750 horas por mes          | 512 MB, 0,1 CPU     | Sí, a los 15 minutos. Despertar: "alrededor de un minuto" según el servicio, más el arranque de la aplicación | No | Sí, y despliega con cada cambio | Sí: `render.yaml`, que también genera secretos |
| Google Cloud Run  | Cupo mensual que sobra para este uso        | A elección          | Sí, enseguida. Despierta con el procesador completo | **Sí**      | Sí, con un disparador a configurar | En parte: un archivo de servicio y pasos por consola |
| Railway           | Crédito único de 5 USD por 30 días; después, 1 USD por mes | 0,5 a 1 GB | Opcional                                   | No             | Sí                                | Sí, pero el formato cambia el 01/12/2026    |
| Koyeb             | Ya no ofrece plan gratuito a cuentas nuevas | —                   | —                                                | Sí             | —                                 | —                                           |
| Fly.io            | Solo una prueba de 7 días                   | —                   | —                                                | Después, sí    | —                                 | —                                           |
| Northflank        | Dos servicios y una base, sin dormirse      | Sin dato claro      | No                                               | Dato contradictorio en sus páginas | Sin verificar      | Sin verificar                               |
| Back4App          | Un contenedor                               | 256 MB: no alcanza  | Sin dato                                         | No             | Sí                                | Sin dato                                    |

Bases de datos PostgreSQL gratuitas:

| Servicio          | Espacio | ¿Vence?                                              | ¿Se duerme sin uso?                                    | ¿Pide tarjeta? |
|-------------------|---------|------------------------------------------------------|--------------------------------------------------------|----------------|
| **Neon**          | 1 GB    | No                                                   | Sí, a los 5 minutos; despierta en menos de un segundo. Tiene un tope mensual de horas de cómputo: unas 400 | No |
| Render Postgres   | 1 GB    | **Sí: a los 30 días de creada queda inaccesible**, y 14 días después se borra | No                            | No             |
| Supabase          | 500 MB  | Se pausa tras una semana sin uso y hay que reactivarla a mano | —                                             | Sin dato       |
| Aiven             | 1 GB    | No                                                   | Se apaga tras un tiempo sin uso y hay que encenderla a mano | No        |

**El punto débil de todos los planes sin tarjeta es el procesador.** Medido en desarrollo, con la imagen del MVP en un contenedor limitado a 512 MB y una décima de procesador, que es lo que da el plan gratuito de Render:

| Configuración de Java                                   | Arranque     | Primer login | Memoria  |
|---------------------------------------------------------|--------------|--------------|----------|
| Sin límite de procesador (referencia)                   | 10 segundos  | —            | 340 MB   |
| 0,1 de procesador, memoria acotada                      | 160 segundos | 11 segundos  | 250 a 275 MB |
| Lo mismo, usando solo el compilador rápido              | 80 segundos  | 6 segundos   | 240 MB   |
| Lo mismo, y además creando los componentes recién al usarlos | 74 segundos | 8 segundos | 260 MB |

Es una medición sobre otra máquina, no sobre el servicio: sirve para el orden de magnitud. Dice que, después de dormirse, el primer pedido tarda entre uno y tres minutos.

## Decisión

La aplicación se despliega en **Render**, en su plan gratuito, y la base de datos en **Neon**, en el suyo.

| Tema                  | Decisión                                                                                                         |
|-----------------------|------------------------------------------------------------------------------------------------------------------|
| Qué queda en el repositorio | Un archivo, `render.yaml`, que describe el servicio y sus variables. Ningún secreto                         |
| Cómo se despliega     | Render construye la imagen desde el `Dockerfile` y despliega cada cambio de `main` que pasó la verificación automática ([ADR-0014](0014-continuous-verification.md)) |
| Secretos              | La clave de firma de los tokens la genera Render. Los tres datos de la base se cargan a mano, una vez, al crear el servicio |
| Usuarios de prueba    | La carga inicial va **encendida**: es lo que pide el requisito                                                    |
| Cambios en el código  | **Ninguno.** Todo lo que el servicio necesita distinto entra por variables de entorno                             |
| Ajustes de Java       | Memoria acotada a la mitad del contenedor y solo el compilador rápido, que baja el arranque a la mitad             |
| HTTPS                 | Lo atiende Render. Una variable le indica a la aplicación que lea las cabeceras que Render agrega                  |
| El arranque lento     | **No se lo mantiene despierto.** El servicio se duerme sin uso, como prevé el plan gratuito, y el README avisa cuánto puede tardar la primera visita |

## Alternativas consideradas

Servicio para la aplicación:

| Alternativa             | A favor                                                                                         | En contra                                                                                           |
|-------------------------|-------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| **Render**              | Sin tarjeta; 512 MB alcanzan; se describe entero en un archivo del repositorio; despliega solo   | Una décima de procesador: arranca en más de un minuto; se duerme a los 15 minutos                    |
| Google Cloud Run        | Es el que mejor encaja en lo técnico: despierta con un procesador completo, en segundos | Exige una cuenta de facturación con tarjeta; más pasos manuales para dejarlo andando; solo una parte queda descripta en el repositorio |
| Railway                 | Más memoria y procesador durante la prueba                                                      | Es una cuenta regresiva: 30 días o 5 USD; su formato de archivo cambia en dos semanas                |
| Una máquina virtual gratuita (Oracle) | Siempre encendida, con más memoria                                                | Hay que administrarla: Docker, HTTPS y dominio a mano; puede recuperarla el proveedor si queda sin uso |

Base de datos:

| Alternativa             | A favor                                                                                         | En contra                                                                                           |
|-------------------------|-------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| **Neon**                | No vence; sin tarjeta; despierta en menos de un segundo                                         | Una cuenta más; tres valores que se pegan a mano; un tope mensual de horas de cómputo                |
| Render Postgres         | Queda en el mismo archivo que el servicio, sin pegar ningún valor                               | Vence a los 30 días: creada hoy, deja de funcionar a mediados de diciembre, en silencio. Además, la aplicación tendría que aprender a armar su dirección a partir de datos sueltos |
| Supabase o Aiven        | Sin vencimiento                                                                                 | Se pausan sin uso y hay que reactivarlas a mano: la demo quedaría caída sin aviso                     |

El arranque lento:

| Alternativa                                         | A favor                                                                  | En contra                                                                                     |
|-----------------------------------------------------|--------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| **Dejar que se duerma y avisar en el README**       | Usa el plan gratuito como está pensado; nada que mantener ni que explicar | El primer contacto de quien corrige con la aplicación puede ser una espera de uno a tres minutos |
| Mantenerlo despierto con un pedido cada 10 minutos  | Quien corrige no espera; las 750 horas gratuitas cubren un mes entero (744) | Es un truco para esquivar el reposo del plan gratuito, y **no se pudo comprobar que las condiciones del servicio lo permitan:** no se deja en el repositorio algo que quizá las incumple. Además obligaría a ajustar la base de datos, que si no nunca se dormiría y agotaría su tope mensual de cómputo |
| Compilar la aplicación a código nativo              | Arranca en menos de un segundo                                           | Otra forma de construir la imagen, con sus propias incompatibilidades; no entra en el plazo     |

## Consecuencias

- Quien corrige puede probar todo desde el navegador, sin instalar nada: Swagger UI, el login y las cuatro entidades.
- **Los pasos que no se pueden automatizar los hace el autor:** crear las dos cuentas, crear la base de datos y crear el servicio a partir del archivo, pegando los tres datos de la base. Son unos 15 minutos, una sola vez. Están uno por uno en el ticket del despliegue ([#34](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/34)).
- El código de la aplicación no cambia. Las variables propias del despliegue están en `render.yaml`, cada una con su motivo.
- **Los usuarios de prueba y sus contraseñas son públicos, y la URL también.** Cualquiera puede iniciar sesión y, como administrador, crear usuarios ([ADR-0017](0017-role-based-access.md)). Es lo que el requisito pide. No hay datos que proteger: la base tiene solo esos usuarios.
- La clave de firma no está en el repositorio ni la conoce nadie: la genera el servicio. Los tokens de la URL pública no sirven en otro lado.
- La caché vive en la memoria de la única instancia. Se vacía cada vez que el servicio se reinicia, se duerme o se despliega; el primer pedido de cada dato vuelve a SWAPI.
- **SWAPI ve los pedidos de la aplicación desde una dirección de Render, que puede compartirse con otros clientes de Render.** Si alguno de ellos también usa SWAPI, su cupo de 100 pedidos cada 15 minutos se comparte. No se pudo comprobar; la caché lo atenúa.
- **La primera visita después de un rato sin uso espera.** El servicio muestra una pantalla de carga mientras la aplicación arranca; según lo medido, entre uno y tres minutos. El README lo dice junto a la URL, para que nadie crea que está caída. Es el costo de no pagar ni poner una tarjeta.
- Mientras la aplicación duerme, nada consulta a la base de datos, que entonces también se duerme. Con este uso, su tope mensual de cómputo queda lejos.
- Las migraciones de la base usan un tipo de bloqueo que no funciona a través del intermediario de conexiones de Neon: hay que usar su dirección directa. Queda dicho en los pasos del ticket.
- El plan gratuito puede reiniciar el servicio en cualquier momento. No guarda nada en disco, así que no pierde nada más que la caché.
- La medición del arranque es de desarrollo. La real se toma con el primer despliegue y se anota acá.
- Si los planes cambian, o si se acepta poner una tarjeta, Google Cloud Run es el reemplazo natural: la imagen y las variables son las mismas, y desaparece el problema del arranque. Se registraría en un ADR nuevo.
- El README explica la elección, como pide el PRD, y muestra la URL en su primera pantalla.

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **15/11/2026.** Lo que apareció al dejar el despliegue descripto en el repositorio (ticket #34). **El servicio todavía no existe:** todo lo que sigue se hizo sin él, y lo que solo se puede saber con el servicio creado está listado al final.
  - **`render.yaml` quedó como estaba previsto,** con dos ajustes de forma: el contexto de la construcción de la imagen está escrito (`dockerContext: .`) y los valores van entre comillas, porque las variables de entorno de Render son siempre textos.
  - **El archivo y el test no pueden desviarse.** El test de CA-20.1 no copia las variables: lee de `render.yaml` las que tienen su valor escrito y se las da a la aplicación como variables de entorno, que es como las entrega el servicio. Sin `SERVER_FORWARD_HEADERS_STRATEGY` en el archivo, el test falla: la documentación declara `http://` y el nombre interno.
  - **Contra qué se revisó el formato.** El sitio de documentación de Render no abrió desde el entorno de trabajo. Se revisó contra la referencia del formato de `render.yaml` que Render publica en su organización de GitHub, actualizada el 08/10/2026. Ahí figuran todas las claves que usa el archivo: `runtime: docker` con `dockerfilePath` y `dockerContext`; `plan: free`; `region`, con `virginia` entre sus valores; `healthCheckPath`; `autoDeployTrigger`, con `commit`, `checksPass` y `off`; y, en las variables, `value`, `sync: false` y `generateValue: true`. **No se validó contra el esquema ni contra el servicio:** las dos formas de validarlo que ofrece Render piden una cuenta. Se valida al crear el servicio, que lee el archivo y muestra sus errores.
  - **Qué hace cada clave que carga un secreto,** según esa referencia. `sync: false` pide el valor una sola vez, al crear el servicio desde el archivo, y no lo vuelve a tocar. `generateValue: true` genera un valor al azar si la variable todavía no existe: la clave de firma no cambia con cada despliegue.
  - **La imagen se probó como la va a correr el servicio:** con las variables de `render.yaml`, 512 MB de memoria y una décima de procesador, contra un PostgreSQL local. Tres arranques desde cero:

    | Arranque, hasta que responde el control de estado | Memoria al arrancar | Memoria después de los primeros pedidos |
    |---|---|---|
    | 170 segundos, con la máquina ocupada en otra tarea | 225 MB | 262 MB |
    | 95 segundos | 226 MB | 255 MB |
    | 93 segundos | — | 254 MB |

    Coincide con lo medido antes de decidir: un minuto y medio si nada más compite por el procesador, y cerca de tres si algo compite. La memoria queda en la mitad del límite, y la aplicación no se reinició ni se quedó sin memoria.
  - **Qué respondió.** El control de estado, `UP`. El primer login del usuario de prueba, `200` en 6 a 7 segundos; el segundo, en menos de un segundo. La documentación de la API, pedida con las cabeceras que agrega el intermediario, declara su servidor con `https://` y el nombre público; sin las cabeceras, con `http://` y el nombre local. La primera vez tarda unos 10 segundos en generarse. Un listado sin token, `401`.
  - **Los primeros pedidos se estorban entre sí.** Con el primer login y la primera lectura de la documentación a la vez, que es lo que provoca quien abre Swagger UI e inicia sesión, cada uno tardó 18 a 19 segundos, y un pedido al control de estado hecho en ese momento esperó 18 segundos. Después todo responde en milésimas. Importa por el punto siguiente.
  - **Lo que no se pudo comprobar sin el servicio,** y se mira con el primer despliegue:
    - Si Render tolera ese control de estado lento. Su referencia dice que da por fallido un control que tarda más de 5 segundos, que saca de servicio a una instancia tras 15 segundos de fallas seguidas y que la reinicia tras 60. Una espera de 18 segundos no llega a los 60 del reinicio, pero sí puede alcanzar los 15: la instancia podría quedar unos segundos fuera de servicio justo en la primera visita.
    - Cuánto tarda de verdad el primer despliegue y el despertar después de dormirse.
    - Qué cabeceras envía Render. El test usa las dos que nombra el criterio, `X-Forwarded-Proto` y `X-Forwarded-Host`.
    - El largo de la clave que genera `generateValue`. La aplicación exige 32 caracteres y no arranca con menos, con un mensaje que lo dice.
    - Que `checksPass` espere a la verificación de GitHub Actions y despliegue recién cuando pasa (CA-20.5).
    - Que la región `virginia` de Render y la de Neon queden cerca. En Neon hay que elegir "AWS US East (N. Virginia)"; la región no se puede cambiar después.
- **15/11/2026.** El ciclo 2 cerró sin el servicio creado: el ticket #34 pasó al ciclo 3 y sigue abierto hasta que la dirección pública responda. Lo que este ADR deja para validar con el primer despliegue (la lista de arriba) sigue sin validarse.
