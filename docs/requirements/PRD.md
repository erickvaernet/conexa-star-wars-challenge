# PRD: API de Star Wars (Back-end Java Challenge)

| Campo          | Valor                                   |
|----------------|-----------------------------------------|
| Autor          | Erick Adriel Vaernet                    |
| Estado         | Aprobado el 14/11/2026                  |
| Última edición | 15/11/2026                              |
| Entrega        | 16/11/2026, link al repositorio público |

> **Alcance de este documento.** El PRD define **qué** se construye y **por qué**: objetivos, usuarios, requisitos y prioridades. No elige tecnologías ni define la arquitectura o el contrato de la API. Eso se decide después, en este orden: un **RFC** con las decisiones técnicas y sus alternativas (registradas como ADRs) y una **especificación técnica** con el detalle ejecutable (contrato de la API, validaciones, casos límite y criterios de aceptación). Ambos se derivan de este PRD una vez aprobado. Es un documento vivo: al empezar cada ciclo de trabajo se revisan las prioridades.

## Objetivo

### Problema

Conexa necesita evaluar cómo trabaja un candidato a Backend Engineer. Para eso pide una aplicación que tome los datos de la [API de Star Wars (SWAPI)](https://www.swapi.tech/documentation) y los ofrezca de forma **ordenada, filtrable y protegida**.

SWAPI, usada directamente, no resuelve ese problema: es pública (no tiene login), no pagina todos sus datos de la misma manera y busca con criterios distintos según la entidad. Quien la consume tiene que tratar cada caso por separado.

### Qué se construye

Una aplicación en Java 21 que permita a un usuario con sesión iniciada **listar People, Films, Starships y Vehicles** por páginas, y **filtrar por ID y/o nombre**, siempre de la misma forma en las cuatro entidades.

### Para quién

| Usuario                  | Qué necesita                                                                                       |
|--------------------------|----------------------------------------------------------------------------------------------------|
| **Consumidor de la API** | Iniciar sesión, recorrer los listados por páginas y encontrar un elemento por ID o por nombre.     |
| **Evaluador de Conexa**  | Levantar el proyecto, probarlo y entender las decisiones tomadas con el menor esfuerzo posible.    |

El evaluador es el usuario más importante: el código, los tests y la documentación son parte del producto, no un agregado.

## Métricas de éxito

| Meta                                | Métrica                                                                                                   | Se cumple en |
|-------------------------------------|-----------------------------------------------------------------------------------------------------------|--------------|
| Cumplir toda la consigna            | 100 % de los requisitos marcados como **Debe** cumplidos, cada uno verificado por al menos un test automatizado | Ciclo 1 |
| Fácil de levantar para el evaluador | Con un solo comando y sin configuración manual, el proyecto queda funcionando en menos de 5 minutos, con usuarios de prueba listos | Ciclo 1 |
| Datos protegidos                    | Ningún listado es accesible sin haber iniciado sesión; ninguna contraseña se guarda en texto legible       | Ciclo 1      |
| Calidad verificable                 | Todos los tests pasan y cubren al menos el 80 % de la lógica propia                                        | Ciclo 1      |
| Decisiones trazables                | Cada decisión técnica relevante queda registrada con su porqué y las alternativas descartadas              | Todos los ciclos |
| Fácil de probar sin instalar nada   | Existe una URL pública desde la que se puede iniciar sesión y probar todos los listados                    | Ciclo 2      |
| Calidad verificada sin intervención | Los tests se ejecutan de forma automática ante cada cambio                                                 | Ciclo 2      |

## Supuestos

- **SWAPI es la única fuente de datos** de Star Wars y es de solo lectura.
- **SWAPI no se comporta igual en todas las entidades** (verificado el 14/11/2026):
  - pagina los listados de People, Starships y Vehicles, pero no el de Films;
  - las búsquedas por nombre devuelven todos los resultados juntos, sin paginar;
  - los Films se buscan por título, no por nombre;
  - por defecto, los listados traen solo el ID y el nombre de cada elemento, pero SWAPI permite pedir los datos completos en el mismo listado;
  - la búsqueda por nombre acepta una parte del nombre y no distingue mayúsculas de minúsculas;
  - si se pide un ID que no existe, SWAPI responde que no lo encontró; una búsqueda por nombre sin coincidencias devuelve una lista vacía.
- **SWAPI es un servicio externo y gratuito:** puede estar lento o caído en cualquier momento, incluso durante la evaluación.
- **SWAPI limita el uso** (medido el 14/11/2026): dentro de una ventana de 15 minutos, cada pedido tarda 0,1 s más que el anterior a partir del quinto, y corta a los 100 pedidos.
- **El volumen de datos es chico:** decenas de elementos por entidad (por ejemplo, 82 People y 36 Starships).
- **El evaluador puede probar de dos formas:** levantando el proyecto en su máquina o entrando a una URL pública.

## Restricciones

Vienen de la consigna o de las respuestas del equipo técnico de Conexa (13/11/2026). No son decisiones propias y no se discuten en el RFC.

| Restricción                                                                 | Origen           |
|-----------------------------------------------------------------------------|------------------|
| La aplicación se desarrolla en **Java 21**                                  | Consigna         |
| Los datos salen de **SWAPI** (swapi.tech)                                   | Consigna         |
| Las entidades son **People, Films, Starships y Vehicles**                   | Consigna         |
| Debe tener **pruebas unitarias y de integración**                           | Consigna         |
| **No hace falta interfaz gráfica:** alcanza con la API documentada con Swagger | Equipo técnico |
| Los **usuarios se guardan en una base de datos**, idealmente con el proyecto dockerizado y una carga inicial (*seed*) al levantar en local | Equipo técnico |
| Se pueden usar **herramientas de IA**; lo ideal es documentar el flujo de trabajo y las decisiones (ADRs) | Equipo técnico |
| Se entrega el link a un **repositorio público** antes del **16/11/2026**    | Mail de selección |
| Se recomienda un desarrollo **prolijo y fácil de corregir** para quien evalúa | Mail de selección |
| Lo que se sume **por fuera de la consigna es bienvenido**                    | Mail de selección |

## Hitos

El trabajo se organiza en **ciclos cortos**. Cada ciclo termina con una versión estable que se puede entregar tal como está.

| Ciclo          | Fecha                        | Alcance                                                                                          | Cierra con                                   |
|----------------|------------------------------|--------------------------------------------------------------------------------------------------|----------------------------------------------|
| **1. MVP**     | 14/11/2026                   | PRD, RFC y especificación técnica completos. Todos los requisitos **Debe** (REQ-01 a REQ-19 y REQ-32)             | Versión `v0.1.0`, que ya cumple la consigna  |
| **2. Mejoras** | 15/11/2026                   | Requisitos **Debería** (REQ-20 a REQ-26), empezando por la verificación automática y el despliegue público | Versión `v0.2.0`                             |
| **3. Extras**  | 15/11/2026, si sobra tiempo  | Requisitos **Podría** (REQ-27 a REQ-35, salvo REQ-32)                                                          | Versión `v0.3.0`                             |
| **Entrega**    | 16/11/2026 como fecha límite | Envío del link al repositorio público                                                            | —                                            |

Reglas de los ciclos:

- **El MVP cumple la consigna por sí solo.** Si un ciclo posterior no llega a cerrarse, se entrega la última versión estable.
- **Cada ciclo cierra con la rama principal estable:** todos los tests pasan y la versión queda etiquetada.
- **Los documentos no se rehacen en cada ciclo.** En el ciclo 1 se escriben completos. En los siguientes se revisan las prioridades de este PRD, se registra un ADR por cada decisión nueva y se extiende la especificación técnica con lo que entra.
- **Un requisito puede cambiar de ciclo** al revisar las prioridades; el cambio queda anotado en este documento.

El mail de selección pide entregar cuanto antes: el link se envía al cerrar el ciclo 2 o, si ese ciclo se complica, con el MVP ya cerrado.

## Requisitos

Criterio de importancia:

- **Debe:** lo pide la consigna o el equipo técnico. Sin esto no se entrega. Entra en el **ciclo 1 (MVP)**.
- **Debería:** no se pide de forma explícita, pero un evaluador lo espera o la consigna lo recomienda. Entra en el **ciclo 2**.
- **Podría:** suma valor por fuera de la consigna (el mail de selección dice que es bienvenido), pero solo se hace si sobra tiempo. Entra en el **ciclo 3**.

La columna "Issue" enlaza al ticket de cada requisito. Los de los ciclos 1 y 2 ya están creados; los del ciclo 3 se crean al empezarlo. Cuatro requisitos valen para todo el proyecto y no llevan ticket, de REQ-14 a REQ-17: tres forman parte de la definición de terminado de cada ticket, y el cuarto, las decisiones registradas, se revisa en cada pull request.

En los ciclos 2 y 3, los requisitos se hacen en el orden de la tabla: primero los de mayor impacto y menor costo. El orden del ciclo 2 se revisó al empezarlo y está en el [historial de cambios](#historial-de-cambios). Lo que no llegue a hacerse en un ciclo pasa al siguiente.

| ID     | Requisito                                          | Historia de usuario                                                                                                        | Importancia | Ciclo | Issue     | Notas                                                                                              |
|--------|----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|-------------|-------|-----------|----------------------------------------------------------------------------------------------------|
| REQ-01 | Listar **People** por páginas                      | Como consumidor, quiero listar personajes por páginas para recorrerlos sin recibir todo de golpe.                           | Debe        | 1 | [#8](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/8) |                                                                                                    |
| REQ-02 | Listar **Films** por páginas                       | Como consumidor, quiero listar películas por páginas, igual que el resto de las entidades.                                  | Debe        | 1 | [#12](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/12) | SWAPI no pagina las películas: la aplicación tiene que hacerlo.                                    |
| REQ-03 | Listar **Starships** por páginas                   | Como consumidor, quiero listar naves por páginas.                                                                          | Debe        | 1 | [#13](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/13) |                                                                                                    |
| REQ-04 | Listar **Vehicles** por páginas                    | Como consumidor, quiero listar vehículos por páginas.                                                                      | Debe        | 1 | [#14](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/14) |                                                                                                    |
| REQ-05 | **Misma paginación** en las cuatro entidades | Como consumidor, quiero pedir y recibir las páginas de la misma forma en todos los listados, para no tratar cada uno distinto. | Debe | 1 | [#8](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/8) | Incluye saber cuántos elementos y cuántas páginas hay en total. Cada elemento del listado trae un resumen: su ID y su nombre (en Films, el título). |
| REQ-06 | **Filtrar por ID** | Como consumidor, quiero obtener un elemento por su ID para ver todos sus datos. | Debe | 1 | [#10](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/10) | Devuelve los datos completos del elemento. Si el ID no existe, la respuesta lo dice con claridad. |
| REQ-07 | **Filtrar por nombre** | Como consumidor, quiero buscar por nombre, o por una parte del nombre, para encontrar un elemento sin conocer su ID. | Debe | 1 | [#11](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/11) | No distingue mayúsculas de minúsculas. En Films, el nombre es el título. Los resultados también se paginan; si no hay coincidencias, la lista llega vacía. |
| REQ-08 | **Filtrar por ID y nombre** a la vez | Como consumidor, quiero enviar ID y nombre juntos y recibir solo el elemento que cumpla ambos. | Debe | 1 | [#11](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/11) | ID 1 con nombre "luke" devuelve a Luke Skywalker; ID 1 con nombre "vader" devuelve una lista vacía. Decidido en la pregunta 1. |
| REQ-09 | **Login seguro**                                   | Como consumidor, quiero iniciar sesión con usuario y contraseña para poder usar los listados.                               | Debe        | 1 | [#6](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/6) | Las contraseñas nunca se guardan ni se muestran en texto legible.                                  |
| REQ-10 | **Listados solo para usuarios autenticados** | Como dueño de la aplicación, quiero que nadie acceda a los datos sin haber iniciado sesión. | Debe | 1 | [#7](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/7) | La sesión vence después de un tiempo. En el MVP hay un solo tipo de usuario; el rol de administrador llega en el ciclo 2 (REQ-25). |
| REQ-11 | **Usuarios de prueba** cargados al levantar        | Como evaluador, quiero tener usuarios listos para iniciar sesión apenas levanto el proyecto.                                | Debe        | 1 | [#6](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/6) | Las credenciales de prueba se documentan.                                                          |
| REQ-12 | **Documentación interactiva** de la API (Swagger)  | Como evaluador, quiero ver y probar todos los listados desde el navegador, incluido el login.                               | Debe        | 1 | [#15](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/15) |                                                                                                    |
| REQ-13 | **Instrucciones de configuración y uso**           | Como evaluador, quiero saber en pocos pasos cómo configurar, levantar y probar el proyecto.                                 | Debe        | 1 | [#16](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/16) | En el README.                                                                                      |
| REQ-14 | **Código documentado** de forma clara              | Como evaluador, quiero entender qué hace cada parte del código sin tener que deducirlo.                                     | Debe        | 1 | Sin ticket: definición de terminado | La consigna pide documentación de código "clara y detallada".                                      |
| REQ-15 | **Pruebas unitarias**                              | Como desarrollador, quiero verificar cada parte por separado para cambiar el código con confianza.                          | Debe        | 1 | Sin ticket: definición de terminado |                                                                                                    |
| REQ-16 | **Pruebas de integración**                         | Como desarrollador, quiero verificar que las partes funcionan juntas.                                                       | Debe        | 1 | Sin ticket: definición de terminado | Deben poder correr aunque SWAPI esté caída.                                                        |
| REQ-17 | **Decisiones técnicas registradas** (ADRs)         | Como evaluador, quiero leer por qué se tomó cada decisión, para evaluar el criterio además del código.                      | Debe        | 1 | Sin ticket: se revisa en cada pull request | Pedido por el equipo técnico.                                                                      |
| REQ-18 | **Flujo de trabajo con IA** documentado | Como evaluador, quiero saber qué se delegó en herramientas de IA, cómo se revisó y qué se corrigió a mano. | Debe | 1 | [#16](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/16) | Pedido por el equipo técnico junto con los ADRs, y comprometido en la consulta del 13/11/2026: el README lo resume y el detalle está en el [registro de uso de herramientas de IA](../process/AI-USAGE.md). |
| REQ-19 | **Levantar todo con un solo comando** | Como evaluador, quiero levantar la aplicación y su base de datos sin instalar ni configurar nada a mano. | Debe | 1 | [#5](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/5) | El equipo técnico describió la base de datos, el proyecto dockerizado y el *seed* como "el combo perfecto". |
| REQ-32 | **Respuestas rápidas** aunque SWAPI sea lenta | Como consumidor, quiero que los pedidos repetidos respondan rápido y no dependan de cuántos pedidos ya recibió SWAPI. | Debe | 1 | [#9](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/9) | SWAPI demora cada vez más a quien le hace muchos pedidos y corta a los 100 cada 15 minutos. Subió de "Podría" a "Debe" el 14/11/2026: ver el historial de cambios. |
| REQ-20 | **Despliegue público** en un servicio gratuito | Como evaluador, quiero probar la aplicación sin clonar el repositorio. | Debería | 3 | [#34](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/34) | URL pública, sin costo y con los usuarios de prueba cargados. Vale cualquier servicio gratuito: la consigna nombra Heroku y Vercel como ejemplos, pero Heroku ya no tiene plan gratuito y Vercel no ejecuta Java. El README explica la elección. Decidido en la pregunta 4. Pasó del ciclo 2 al ciclo 3 el 15/11/2026: ver el historial de cambios. |
| REQ-21 | **Errores claros y con el mismo formato** | Como consumidor, quiero entender qué salió mal (dato inválido, sesión vencida, elemento inexistente) y poder manejarlo. | Debería | 2 | [#35](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/35) |  |
| REQ-22 | **Tolerancia a fallas de SWAPI** | Como consumidor, quiero una respuesta clara y a tiempo si SWAPI está lenta o caída, en vez de que la aplicación se cuelgue. | Debería | 2 | [#38](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/38) | El login debe seguir funcionando aunque SWAPI no responda. |
| REQ-23 | **Verificación automática** en cada cambio | Como evaluador, quiero ver que el proyecto compila y los tests pasan sin tener que correrlos. | Debería | 2 | [#33](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/33) |  |
| REQ-24 | **Datos completos a pedido** en los listados | Como consumidor, quiero poder pedir los datos completos de cada elemento en el mismo listado, para no hacer un pedido por cada uno. | Debería | 2 | [#37](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/37) | Por defecto el listado sigue trayendo el resumen. Decidido en la pregunta 5. En el ciclo 3 puede sumarse una alternativa con GraphQL (REQ-30). |
| REQ-25 | **Rol de administrador** | Como dueño de la aplicación, quiero distinguir a los administradores de los usuarios comunes, para reservarles ciertas operaciones. | Debería | 2 | [#36](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/36) | Los usuarios de prueba pasan a incluir uno de cada tipo. Los dos tipos pueden usar los listados. Decidido en la pregunta 2. |
| REQ-26 | **Alta de usuarios por el administrador** | Como administrador, quiero crear usuarios nuevos, para dar acceso sin depender de la carga inicial. | Debería | 2 | [#36](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/36) | Es la operación reservada al administrador: si la intenta un usuario común, se rechaza. Decidido en la pregunta 3. |
| REQ-27 | **Cobertura de tests visible** | Como evaluador, quiero ver qué porcentaje del código está cubierto por tests sin tener que correr nada. | Podría | 3 | Por crear | Un indicador en el README. Respalda la recomendación de hacer testing del mail de selección. |
| REQ-28 | **Colección de pedidos lista para usar** | Como evaluador, quiero importar un archivo y recorrer todo el flujo (iniciar sesión, listar y filtrar) sin armar cada pedido a mano. | Podría | 3 | Por crear | Complementa a la documentación interactiva (REQ-12). |
| REQ-29 | **Límite de intentos de login** | Como dueño de la aplicación, quiero frenar a quien prueba muchas contraseñas seguidas, para que no pueda adivinarlas. | Podría | 3 | Por crear | Refuerza el login seguro (REQ-09). |
| REQ-30 | **Consulta flexible con GraphQL** | Como consumidor, quiero pedir en una sola consulta exactamente los datos que necesito de cada elemento, para no recibir de más ni hacer varios pedidos. | Podría | 3 | Por crear | Se suma a la API existente, no la reemplaza: mismas cuatro entidades, solo lectura y con el mismo login. Es otra forma de resolver lo que cubre REQ-24. Solo se empieza si puede terminarse con sus tests y su documentación. |
| REQ-31 | **Límite de pedidos por usuario** | Como dueño de la aplicación, quiero limitar cuántos pedidos puede hacer cada usuario por minuto y por hora, para evitar abusos y no sobrecargar a SWAPI. | Podría | 3 | Por crear | Si se supera el límite, la respuesta avisa que hay que esperar. Complementa al límite de intentos de login (REQ-29). |
| REQ-33 | **Cerrar sesión** | Como consumidor, quiero cerrar mi sesión cuando termino, sin esperar a que venza sola. | Podría | 3 | Por crear | Después de cerrarla, esa sesión ya no sirve para acceder a los listados. |
| REQ-34 | **Registro público de usuarios** | Como visitante, quiero crearme una cuenta sin depender de un administrador. | Podría | 3 | Por crear | Las cuentas creadas así son siempre de usuario común. Decidido en la pregunta 3. |
| REQ-35 | **Caché resistente al abuso** | Como dueño de la aplicación, quiero que una avalancha de búsquedas inútiles no desplace de la caché los datos más pedidos, para que la aplicación siga respondiendo rápido. | Podría | 3 | Por crear | Idea: guardar por separado los recursos específicos y los listados, cada grupo con su tope. Complementa al límite de pedidos por usuario (REQ-31). Agregado el 14/11/2026: ver el historial de cambios. |

## Fuera del alcance

| Fuera del alcance                                                              | Motivo                                                               |
|--------------------------------------------------------------------------------|----------------------------------------------------------------------|
| Interfaz gráfica                                                               | El equipo técnico confirmó que alcanza con la API y Swagger.         |
| Crear, editar o borrar datos de Star Wars                                      | La consigna pide listar y filtrar; SWAPI es de solo lectura.         |
| Otras entidades de SWAPI (Planets, Species)                                    | No están en la consigna.                                             |
| Ordenar los listados o filtrar por otros campos                                | La consigna pide filtrar solo por ID y nombre.                       |
| Recuperación de contraseña, verificación por email e inicio de sesión con terceros | La consigna pide un login seguro, no un sistema de identidad completo. |
| Soportar mucho tráfico o muchos usuarios a la vez                              | Es un desafío de evaluación, no un servicio en producción.           |

## Diseño

No hay diseño visual: la aplicación no tiene interfaz gráfica y se usa a través de su documentación interactiva (REQ-12).

El diseño técnico (arquitectura, tecnologías, seguridad y contrato de la API) no forma parte de este documento. Se define en el RFC y en la especificación técnica, una vez aprobado este PRD.

## Preguntas abiertas

No quedan preguntas pendientes. Las que respondió el autor son decisiones propias sobre puntos que la consigna deja abiertos; cada una indica su motivo.

| # | Pregunta | Respuesta | Quién respondió | Fecha de respuesta |
|---|----------|-----------|-----------------|--------------------|
| 1 | "Filtrado por ID y/o nombre": si llegan los dos juntos, ¿qué se devuelve? | El resultado debe cumplir ambos. Es como funcionan los filtros en la mayoría de las APIs: cada filtro achica el resultado. (REQ-08) | Autor | 14/11/2026 |
| 2 | ¿Hace falta más de un tipo de usuario o alcanza con "inició sesión o no"? | En el MVP, un solo tipo: alcanza para proteger los listados. En el ciclo 2 se suma el rol de administrador, porque la consigna pide "autorización" y conviene mostrarla por rol. (REQ-10, REQ-25) | Autor | 14/11/2026 |
| 3 | Además de los usuarios de prueba, ¿cómo se crean usuarios nuevos? | En el ciclo 2 los crea el administrador; es la operación reservada a su rol. El registro público queda como "Podría" en el ciclo 3. (REQ-26, REQ-34) | Autor | 14/11/2026 |
| 4 | La consigna recomienda desplegar "en algún servidor gratuito, como Heroku o Vercel", pero Heroku ya no tiene plan gratuito y Vercel no ejecuta Java. ¿Vale otro servicio? | Sí: la consigna da ejemplos, no una lista cerrada. Vale cualquier servicio gratuito con URL pública. El proveedor se elige en el RFC. (REQ-20) | Autor | 14/11/2026 |
| 5 | En los listados, ¿cada elemento muestra todos sus datos o solo un resumen? | Por defecto, un resumen (ID y nombre). Los datos completos se obtienen pidiendo el elemento por su ID y, desde el ciclo 2, también a pedido en el mismo listado. (REQ-05, REQ-06, REQ-24) | Autor | 14/11/2026 |
| 6 | ¿Está permitido usar herramientas de IA? | Sí. Lo ideal es documentar el flujo de trabajo y las decisiones (ADRs). (REQ-17, REQ-18) | Equipo técnico de Conexa | 13/11/2026 |
| 7 | ¿Se espera interfaz gráfica o solo la API? | Solo la API con Swagger alcanza. (REQ-12) | Equipo técnico de Conexa | 13/11/2026 |
| 8 | ¿Usuarios fijos en el código o en una base de datos? | Base de datos, con una carga inicial al levantar el proyecto en local. (REQ-11, REQ-19) | Equipo técnico de Conexa | 13/11/2026 |

## Historial de cambios

| Fecha      | Cambio                                                              | Motivo                                                                 |
|------------|---------------------------------------------------------------------|------------------------------------------------------------------------|
| 14/11/2026 | REQ-32, "Respuestas rápidas", pasa de "Podría" (ciclo 3) a "Debe" (ciclo 1) | Al preparar el RFC se midió que SWAPI demora cada vez más a quien le hace muchos pedidos. Sin guardar sus respuestas, quien pruebe la aplicación un rato la vería cada vez más lenta. Decisión D-15 del [RFC-001](../rfc/RFC-001-architecture.md) |
| 14/11/2026 | Se agrega REQ-35, "Caché resistente al abuso", como "Podría" (ciclo 3) | Al revisar la caché después de aprobar la especificación técnica se vio que las búsquedas por nombre y los números de página no tienen un límite de combinaciones, y que muchas búsquedas inútiles podrían desplazar de la caché los datos más pedidos. Con un uso normal no ocurre, por eso no entra en el MVP. El análisis está en una nota del [ADR-0011](../adr/0011-swapi-response-cache.md) |
| 15/11/2026 | Orden del ciclo 2: primero la verificación automática (REQ-23) y después el despliegue público (REQ-20), al revés de lo previsto; siguen REQ-21, REQ-25 junto con REQ-26, REQ-24 y REQ-22. REQ-25 y REQ-26 comparten ticket | La verificación automática es el cambio más chico y, una vez hecha, cada cambio del ciclo se verifica solo, incluido el del despliegue. El rol de administrador solo se puede probar con la operación que reserva. REQ-22 va último porque su historia ya la cumple el MVP: si no entra en el ciclo, pasa al ciclo 3. El autor fijó el alcance y delegó estas decisiones ([registro de uso de herramientas de IA](../process/AI-USAGE.md#ciclo-2)); están en los [ADRs](../adr/README.md), del 0014 al 0020 |
| 15/11/2026 | REQ-20, "Despliegue público", pasa del ciclo 2 al ciclo 3. Sigue siendo "Debería" | El ciclo 2 cerró con la parte del repositorio hecha: el archivo que describe el despliegue, su test de aceptación y la documentación. Falta crear el servicio y su base de datos, que piden dos cuentas que el autor crea en persona, y ese paso no se hizo antes del cierre. El ticket #34 queda abierto y pasa al hito `v0.3.0`; se cierra cuando la dirección pública responda |

## Enlaces de referencia

- Consigna del desafío: [Back-end JAVA Challenge (Notion)](https://conexatech.notion.site/Back-end-JAVA-Challenge-f86b34a27aaa49f38890d65d8ed9f50c)
- Documentación de SWAPI: <https://www.swapi.tech/documentation>
- Decisiones técnicas: [`RFC-001`](../rfc/RFC-001-architecture.md)
- Convenciones del repositorio: [`docs/CONVENTIONS.md`](../CONVENTIONS.md)
- Método MoSCoW de priorización: <https://en.wikipedia.org/wiki/MoSCoW_method>
