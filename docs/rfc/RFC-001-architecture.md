# RFC-001: Arquitectura y decisiones técnicas del MVP

| Campo      | Valor                                                                 |
|------------|-----------------------------------------------------------------------|
| Estado     | Aceptado el 14/11/2026                                                |
| Fecha      | 14/11/2026                                                            |
| Autor      | Erick Adriel Vaernet                                                  |
| Alcance    | Ciclo 1 (MVP): requisitos REQ-01 a REQ-19 del [PRD](../requirements/PRD.md) |
| Resultado  | Once ADRs aceptados, en [`adr/`](../adr/README.md)                    |

> **Cómo leer este documento.** Cada decisión plantea una pregunta, compara alternativas y termina con la opción elegida. El autor las revisó una por una y las aceptó el 14/11/2026; el resumen está en la [sección 6](#6-decisiones-tomadas). Cada una se registra como ADR. Las decisiones de los ciclos 2 y 3 se listan al final y se toman cuando empiece cada ciclo.

## 1. Resumen

- **Plataforma:** Java 21 con Spring Boot 4.1 y Maven.
- **Código organizado por funcionalidad**, con una única frontera hacia SWAPI. Sin arquitectura hexagonal completa ni DDD.
- **Un núcleo común** de paginación y filtros para las cuatro entidades, en vez de cuatro copias.
- **Cliente de SWAPI** síncrono, detrás de una interfaz propia, con tiempos máximos de espera.
- **Paginación propia y uniforme**, con páginas desde 1: siempre se le pide la página a SWAPI y, si SWAPI no pagina, se recorta en memoria.
- **Caché en memoria** de las respuestas de SWAPI, porque SWAPI demora cada vez más a quien le hace muchos pedidos.
- **Login con JWT** sin estado, usando el soporte que ya trae Spring Security.
- **Usuarios en PostgreSQL**, con el esquema y los usuarios de prueba cargados con Flyway.
- **Tests en tres niveles:** unitarios, de aceptación sobre HTTP con SWAPI simulada y base de datos real, y de arquitectura.
- **Un comando para levantar todo:** `docker compose up`.

## 2. Contexto

**Lo que fija el PRD**

- Java 21, SWAPI como fuente de datos, cuatro entidades, API documentada con Swagger, usuarios en base de datos con carga inicial, pruebas unitarias y de integración.
- Listados con resumen (ID y nombre), detalle completo por ID, filtro por nombre parcial y combinación de ID y nombre que debe cumplir ambos.
- En el MVP hay un solo tipo de usuario.

**Cómo se comporta SWAPI** (verificado con pedidos reales el 14/11/2026)

| Pedido                                   | Respuesta                                                    | ¿Pagina? |
|------------------------------------------|--------------------------------------------------------------|----------|
| `/people?page=N&limit=M` (también `starships` y `vehicles`) | Total de registros, total de páginas y, por elemento, ID, nombre y URL | Sí |
| El mismo pedido con `expanded=true`      | Igual, pero cada elemento trae todos sus datos               | Sí       |
| `/people?name=sky`                       | Todas las coincidencias con sus datos completos. Ignora `page` y `limit`. Busca por parte del nombre y no distingue mayúsculas | No |
| `/films`                                 | Las seis películas con sus datos completos. Ignora `page` y `limit` | No |
| `/films?title=hope`                      | Filtra por título, no por nombre                             | No       |
| `/people/1`                              | Un elemento con sus datos completos                          | —        |
| `/people/9999`                           | Código 404                                                   | —        |
| `/people?name=zzzz`                      | Código 200 con una lista vacía                               | —        |

Además, `limit=200` devolvió los 82 personajes en una sola página: SWAPI no impuso un máximo.

**Qué dice su documentación**

- No menciona `page` ni `limit` para ningún recurso. Esos parámetros solo aparecen en los enlaces `next` y `previous` que devuelven People, Starships y Vehicles. Es un contrato de hecho, no publicado, y puede cambiar.
- Sí documenta `expanded=true` y la búsqueda por nombre (por título en Films), parcial y sin distinguir mayúsculas.

**Límites de uso** (documentados y medidos el 14/11/2026)

- **Demora progresiva:** dentro de una ventana de 15 minutos, cada pedido tarda 0,1 s más que el anterior a partir del quinto. Medido: los tres primeros pedidos tardaron 0,3 s; el cuarto, 0,9 s; el décimo, 1,5 s. A ese ritmo, el pedido número 50 tarda unos 5 s.
- **Tope:** 100 pedidos por ventana de 15 minutos, según las cabeceras de la respuesta, y 10.000 por día según la documentación.

**Entorno de desarrollo** (verificado el 14/11/2026)

- Hay Docker 29 y Docker Compose 5.
- El JDK instalado es el 25, no el 21. No hay Maven ni Gradle instalados.

**Versiones estables actuales** (Maven Central, 14/11/2026)

| Librería            | Versión |
|---------------------|---------|
| Spring Boot         | 4.1.2   |
| springdoc-openapi   | 3.2.0   |
| Flyway              | 13.12.2 |
| Testcontainers      | 2.0.5   |
| WireMock            | 3.13.2  |
| ArchUnit            | 1.5.1   |

## 3. Decisiones

### D-01. Plataforma y versión

**¿Sobre qué framework y versión se construye?**

| Alternativa                | A favor                                                              | En contra                                                           |
|----------------------------|----------------------------------------------------------------------|---------------------------------------------------------------------|
| **Spring Boot 4.1** (elegida) | Es la línea vigente; es el estándar en backend Java y lo que un evaluador espera leer; trae seguridad, acceso a datos y cliente HTTP integrados | Las librerías de terceros para la línea 4 son recientes: hay que comprobar que funcionen juntas |
| Spring Boot 4.0            | Misma línea, con un año más de uso                                   | Va una versión por detrás sin una ventaja concreta                   |
| Quarkus o Micronaut        | Arranque más rápido y menor consumo                                  | Menos conocidos por quien corrige; la ventaja no pesa en este proyecto |

**Decisión:** Spring Boot 4.1 sobre Java 21. Si al armar el esqueleto alguna librería no es compatible, se baja a Spring Boot 4.0 y se anota en el ADR.

**Java 21 con un JDK 25 instalado:** el proyecto se compila con nivel de lenguaje 21 (`maven.compiler.release=21`) y la imagen de Docker usa un JDK 21, así que lo que se ejecuta y se entrega es Java 21. Requisitos: restricción "Java 21".

### D-02. Herramienta de construcción

| Alternativa            | A favor                                                         | En contra                                  |
|------------------------|-----------------------------------------------------------------|--------------------------------------------|
| **Maven con *wrapper*** (elegida) | Es la más extendida en proyectos Spring; el *wrapper* (`mvnw`) evita tener que instalar Maven, tanto acá como para quien corrige | Archivos de configuración más largos |
| Gradle                 | Más rápido y más expresivo                                      | Menos uniforme entre proyectos; no aporta en un proyecto chico |

**Decisión:** Maven con *wrapper*. Requisitos: REQ-13, REQ-19.

### D-03. Organización del código

**¿Cómo se dividen las carpetas y qué puede depender de qué?**

| Alternativa                     | A favor                                                             | En contra                                                                 |
|---------------------------------|---------------------------------------------------------------------|---------------------------------------------------------------------------|
| Capas técnicas (`controller`, `service`, `repository`) | La más conocida                              | Mezcla funcionalidades sin relación en la misma carpeta; un cambio toca todas las capas |
| **Por funcionalidad, con una frontera hacia SWAPI** (elegida) | Cada carpeta es una funcionalidad completa y se entiende sola; el formato de SWAPI queda encerrado en un solo lugar | Hay que cuidar que las funcionalidades no se mezclen entre sí |
| Hexagonal completa              | Aísla el negocio de toda la infraestructura                         | Mucha estructura (puertos, adaptadores y traducciones) para una aplicación que casi no tiene lógica de negocio |
| DDD                             | Ordena dominios complejos                                           | Acá no hay dominio que modelar: los datos son de solo lectura y vienen de afuera |

**Decisión:** carpetas por funcionalidad. Se toma de la arquitectura hexagonal una sola idea, la que sí aporta: SWAPI se usa a través de una interfaz propia, y solo la carpeta `swapi` conoce su formato.

```
io.github.erickvaernet.starwars
├── auth/          login, emisión del token, usuarios y carga inicial
├── catalog/       núcleo común: páginas, filtros y la interfaz hacia la fuente de datos
│   ├── people/    controlador y datos propios de People
│   ├── films/
│   ├── starships/
│   └── vehicles/
├── swapi/         cliente de SWAPI y traducción de sus respuestas
└── shared/        errores, configuración y documentación de la API
```

Reglas de dependencia:

- `catalog` define la interfaz de la fuente de datos; `swapi` la implementa. `catalog` no conoce a `swapi`.
- Los datos con el formato de SWAPI no salen de `swapi`.
- `auth` y `catalog` no dependen entre sí.
- Nada depende de un controlador.

Estas reglas pasan a `docs/ARCHITECTURE.md` y se comprueban con tests (ver D-11). Requisitos: REQ-14, REQ-17.

### D-04. Cuatro entidades sin repetir cuatro veces

**People, Films, Starships y Vehicles se listan y se filtran igual. ¿Cómo se evita copiar la misma lógica?**

| Alternativa                       | A favor                                              | En contra                                                    |
|-----------------------------------|------------------------------------------------------|--------------------------------------------------------------|
| Cuatro implementaciones separadas | Cada una se lee sola                                 | Cuatro copias de la paginación y los filtros: un arreglo hay que hacerlo cuatro veces |
| Un único controlador genérico con la entidad como parámetro | Mínimo código                  | La documentación de la API pierde los datos propios de cada entidad; el detalle de cada una es distinto |
| **Núcleo común y una parte fina por entidad** (elegida) | La paginación y los filtros existen una sola vez; cada entidad conserva su controlador, su documentación y sus datos de detalle | Requiere una abstracción común bien definida |

**Decisión:** núcleo común en `catalog` y, por entidad, solo lo que la distingue: su ruta, su nombre en SWAPI, el campo por el que se busca (Films usa el título) y sus datos de detalle. La forma de paginar no depende de la entidad (ver D-06). Requisitos: REQ-01 a REQ-08.

### D-05. Cliente de SWAPI

| Alternativa                 | A favor                                                        | En contra                                                  |
|-----------------------------|----------------------------------------------------------------|------------------------------------------------------------|
| **`RestClient`** (elegida) | Viene con Spring, es síncrono y simple de leer y de probar    | —                                                          |
| `WebClient`                 | Permite pedidos no bloqueantes                                 | Obliga a sumar el modelo reactivo solo para un cliente      |
| OpenFeign                   | Cliente declarativo, con menos código                          | Una dependencia más para algo que Spring ya resuelve        |

**Decisión:** `RestClient` detrás de la interfaz de `catalog`, con tiempos máximos de conexión y de lectura configurables. Se activan los hilos virtuales de Java 21, que permiten atender muchos pedidos bloqueantes sin el modelo reactivo.

En el MVP, si SWAPI falla o tarda de más, la aplicación responde con un error claro y no se cuelga. Los reintentos y el corte automático quedan para el ciclo 2 (REQ-22). Requisitos: REQ-01 a REQ-04, REQ-16.

### D-06. Paginación y filtros

**SWAPI pagina algunos pedidos y otros no. ¿Cómo se ofrece una paginación igual para todo?**

| Alternativa                          | A favor                                                                 | En contra                                                            |
|--------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------|
| **Decidir según la respuesta** (elegida) | No hay casos especiales por entidad; si SWAPI empieza a paginar Films o las búsquedas, funciona sin tocar el código | Hay que reconocer las dos formas de respuesta de SWAPI |
| Regla fija por entidad               | Más explícita: el código dice de antemano qué se pagina en memoria      | Si SWAPI cambia su comportamiento, se rompe                           |
| Traer siempre todo y recortar        | La más simple                                                           | Pide a SWAPI datos que no se muestran: 82 personajes para listar 10   |

**Cómo funciona:** siempre se le envía a SWAPI la página y el tamaño pedidos, para las cuatro entidades y también en las búsquedas. Después se mira cómo respondió:

| SWAPI responde                                   | Qué hace la aplicación                                                    |
|--------------------------------------------------|---------------------------------------------------------------------------|
| Con totales de registros y de páginas            | Usa esa página tal cual (**delegada**)                                    |
| Con la lista completa, sin totales               | Recorta la página pedida y calcula los totales (**en memoria**)           |

Hoy, People, Starships y Vehicles sin filtros caen en el primer caso; Films y las búsquedas por nombre, en el segundo.

Los filtros por ID no pasan por la paginación de SWAPI:

| Pedido a esta aplicación        | Cómo se resuelve                                                                  |
|---------------------------------|-----------------------------------------------------------------------------------|
| Filtro por ID                   | Se pide ese elemento a SWAPI y se devuelve una página con uno o ningún elemento    |
| Filtro por ID y nombre          | Se pide el elemento por ID y se comprueba que su nombre contenga el texto          |
| Detalle por ID (`/people/1`)    | Se pide ese elemento; si no existe, se responde 404                                |

Límite conocido: si SWAPI empezara a respetar el tamaño de página sin informar los totales, la aplicación tomaría una lista parcial como completa. No hay forma de distinguirlo desde afuera; un cambio así se detectaría al comparar con respuestas reales.

**¿La primera página es la 0 o la 1?**

| Alternativa          | A favor                                                           | En contra                                         |
|----------------------|-------------------------------------------------------------------|---------------------------------------------------|
| **Desde 1** (elegida) | Es como pagina SWAPI y como cuenta una persona                 | Difiere de lo habitual en Spring Data             |
| Desde 0              | Es lo habitual en Spring Data                                     | Esta aplicación no usa Spring Data para los listados; obligaría a sumar y restar uno contra SWAPI |

**Decisión:**

- Páginas desde 1. Tamaño por defecto 10 y máximo 100.
- Todas las respuestas de listado tienen la misma forma: los elementos, el número de página, el tamaño, el total de elementos y el total de páginas.
- Pedir una página posterior a la última devuelve una lista vacía con los totales correctos, no un error.
- Las rutas llevan el prefijo `/api/v1`, para poder cambiar el contrato más adelante sin romper a quien lo usa.

El contrato exacto se detalla en la especificación técnica. Requisitos: REQ-01 a REQ-08.

### D-07. Autenticación

**¿Cómo se inicia sesión y cómo se protege cada pedido?**

| Alternativa                               | A favor                                                         | En contra                                                             |
|-------------------------------------------|-----------------------------------------------------------------|-----------------------------------------------------------------------|
| **JWT con el soporte de Spring Security** (elegida) | Sin estado en el servidor; usa componentes ya probados de Spring en vez de código de seguridad propio | Un token no se puede anular antes de que venza |
| JWT con una librería aparte y un filtro propio | Muy difundido en tutoriales                                | Más código de seguridad escrito a mano, que es donde más errores aparecen |
| Sesión en el servidor con *cookies*       | Se puede cerrar la sesión en el momento                         | Guarda estado en el servidor; es incómodo para una API que se prueba desde Swagger |

**Decisión:**

- `POST /api/v1/auth/login` recibe usuario y contraseña y devuelve un JWT firmado, con vencimiento configurable (60 minutos por defecto).
- Los demás pedidos llevan el token en la cabecera `Authorization: Bearer`.
- La clave de firma viene de una variable de entorno, nunca del repositorio.
- Las contraseñas se guardan con BCrypt.
- Todo está cerrado por defecto. Solo son públicos el login, la documentación de la API y el control de estado.
- El mensaje de error del login es el mismo si falla el usuario o la contraseña, para no revelar qué usuarios existen.

Consecuencia: "cerrar sesión" (REQ-33, ciclo 3) va a necesitar una lista de tokens anulados. Requisitos: REQ-09, REQ-10.

### D-08. Usuarios: base de datos y carga inicial

| Decisión                | Alternativas                                              | Elegida y motivo                                                           |
|-------------------------|-----------------------------------------------------------|----------------------------------------------------------------------------|
| Motor                   | PostgreSQL, MySQL, H2 en memoria                          | **PostgreSQL:** es una base real, como pidió el equipo técnico, y la más usada con Spring. H2 no cumple "una DB si se dockeriza todo" |
| Acceso a datos          | Spring Data JPA, Spring Data JDBC                         | **Spring Data JPA:** es lo que se espera en un backend Java. Para una sola tabla es más de lo necesario, y eso se deja anotado |
| Cambios de esquema      | Flyway, Liquibase, generación automática                  | **Flyway:** archivos SQL versionados, simples de leer. La generación automática no deja registro de los cambios |
| Carga inicial (*seed*)  | Archivo SQL de Flyway junto al esquema; archivo SQL de Flyway en una carpeta aparte; componente que crea los usuarios al arrancar | **Flyway, en una carpeta aparte:** un archivo SQL con los `INSERT` y la contraseña de prueba ya cifrada. Es lo que pidió el equipo técnico, se ve de un vistazo qué se carga y no agrega código. La carpeta se activa por configuración, así los datos de prueba no quedan atados al esquema: en un sistema real se apagaría |

Sobre el componente al arrancar: su ventaja era no dejar contraseñas en el repositorio, pero la contraseña de prueba se publica igual en el README para quien corrige, así que no protegía nada y sumaba código.

Requisitos: REQ-11, REQ-19.

### D-09. Formato de los errores

| Alternativa                      | A favor                                              | En contra                              |
|----------------------------------|------------------------------------------------------|----------------------------------------|
| **Problem Details (RFC 9457)** (elegida) | Es un estándar y Spring lo trae incorporado | —                                      |
| Formato propio                   | Libertad total                                       | Hay que inventarlo, documentarlo y mantenerlo |

**Decisión:** Problem Details desde el MVP para los errores básicos: dato inválido (400), sin sesión (401), elemento inexistente (404) y falla de SWAPI (502 o 504). El pulido completo es el REQ-21 del ciclo 2.

### D-10. Documentación de la API

| Alternativa                                   | A favor                                                    | En contra                                           |
|-----------------------------------------------|------------------------------------------------------------|-----------------------------------------------------|
| **Generada desde el código con springdoc-openapi** (elegida) | Nunca queda desactualizada; incluye Swagger UI con el botón para cargar el token | Hay que anotar el código para que quede completa |
| Escrita a mano en un archivo OpenAPI          | Control total del contrato                                 | Puede desviarse del código sin que nadie lo note     |

**Decisión:** springdoc-openapi. La especificación técnica define el contrato y, una vez implementado, la documentación generada es la referencia. Requisitos: REQ-12.

### D-11. Estrategia de pruebas

| Nivel                 | Qué prueba                                                         | Con qué                                                           |
|-----------------------|--------------------------------------------------------------------|-------------------------------------------------------------------|
| **Unitarias**         | Cada parte por separado: paginación en memoria, filtros, traducción de SWAPI | JUnit 5, Mockito y AssertJ. Sin red ni Docker           |
| **De aceptación**     | Cada historia de punta a punta, por HTTP, con la aplicación levantada | SWAPI simulada con WireMock, usando respuestas reales grabadas, y PostgreSQL real con Testcontainers |
| **De arquitectura**   | Las reglas de dependencia de D-03                                  | ArchUnit                                                          |

Alternativas descartadas:

- **H2 en lugar de PostgreSQL en los tests:** es más rápido, pero prueba contra una base distinta de la real.
- **Simular el cliente de SWAPI con un objeto falso:** no prueba la traducción de las respuestas, que es donde SWAPI es irregular.
- **Llamar a la SWAPI real:** los tests fallarían cuando SWAPI esté caída.

La cobertura se mide con JaCoCo; el objetivo del PRD es 80 % de la lógica propia. Los tests de aceptación necesitan Docker, que ya es un requisito para levantar el proyecto. Requisitos: REQ-15, REQ-16.

### D-12. Entorno local

**Decisión:**

- `docker compose up` levanta la aplicación y PostgreSQL, aplica los cambios de esquema y carga los usuarios de prueba.
- En la máquina de desarrollo se usa el JDK 25 ya instalado, compilando con nivel de lenguaje 21: el compilador rechaza cualquier característica posterior. Dentro de Docker todo corre sobre Java 21, que es lo que se entrega.
- La imagen se construye en dos etapas: una compila con JDK 21 y la otra ejecuta con un JRE 21 liviano.
- La configuración sensible viene de variables de entorno. El repositorio incluye un `.env.example` con valores de desarrollo.
- Se expone un control de estado (`/actuator/health`) que Docker usa para saber cuándo la aplicación está lista. El resto de Actuator queda cerrado.

No se evaluaron alternativas a Docker: el equipo técnico lo mencionó de forma explícita. Requisitos: REQ-11, REQ-13, REQ-19.

### D-13. Documentación del código

**Decisión:** Javadoc en las clases e interfaces públicas y en los métodos cuyo comportamiento no es obvio, explicando el porqué y no repitiendo lo que el código ya dice. Requisitos: REQ-14.

### D-14. Instrucciones para herramientas de IA

**¿Se publica un archivo con las reglas del proyecto para agentes de IA?**

| Alternativa                 | A favor                                                                 | En contra                                           |
|-----------------------------|-------------------------------------------------------------------------|-----------------------------------------------------|
| Publicar `AGENTS.md`        | Es una convención neutral, que no nombra ninguna herramienta; muestra cómo se guía a la IA, que es parte de lo que pidió el equipo técnico | Un archivo más para mantener |
| No publicarlo               | Menos archivos                                                          | Las reglas para la IA quedan fuera del repositorio  |

**Decisión:** publicarlo, corto, apuntando a `CONVENTIONS.md`, `ARCHITECTURE.md` y al flujo de trabajo, sin repetir su contenido. Requisitos: REQ-18.

### D-15. Caché de las respuestas de SWAPI

**SWAPI demora cada vez más a quien le hace muchos pedidos y corta a los 100 por ventana. ¿Cómo se evita que eso llegue a quien usa esta aplicación?**

Esta decisión no estaba en el borrador: surgió al medir los límites de SWAPI. En el PRD era un extra del ciclo 3 (REQ-32) y pasa al MVP.

| Alternativa                              | A favor                                                             | En contra                                                        |
|------------------------------------------|---------------------------------------------------------------------|------------------------------------------------------------------|
| Sin caché                                | Nada que mantener                                                   | Quien pruebe un rato desde Swagger ve respuestas cada vez más lentas y puede llegar al corte |
| **Caché en memoria con vencimiento** (elegida) | Los datos de Star Wars casi no cambian; Spring lo resuelve con poco código | Cada instancia tiene su propia copia                       |
| Caché compartida (Redis)                 | Sirve con varias instancias                                         | Un servicio más para levantar; no hace falta con una sola instancia |

**Decisión:** caché en memoria de las respuestas de SWAPI, con vencimiento y tamaño máximo configurables (una hora por defecto). Se aplica en el único punto de entrada a SWAPI, así que cubre los listados, las búsquedas y el detalle.

- La paginación en memoria se apoya en ella: recorrer las páginas de Films o de una búsqueda genera un solo pedido a SWAPI.
- Con más de una instancia haría falta una caché compartida, como Redis. Queda anotado en el ADR.

Requisitos: REQ-32, REQ-16.

## 4. Decisiones que se postergan

Se toman al empezar su ciclo, cada una con su ADR.

| Tema                                   | Requisito       | Ciclo | Qué deja preparado el MVP                                        |
|----------------------------------------|-----------------|-------|------------------------------------------------------------------|
| Proveedor del despliegue gratuito      | REQ-20          | 2     | Imagen de Docker y configuración por variables de entorno        |
| Reintentos y corte automático ante fallas de SWAPI (candidato: Resilience4j) | REQ-22 | 2 | Tiempos máximos de espera y errores claros |
| Verificación automática en cada cambio | REQ-23          | 2     | Todos los tests corren con un solo comando                       |
| Datos completos a pedido               | REQ-24          | 2     | El cliente ya distingue resumen y detalle                        |
| Rol de administrador y alta de usuarios | REQ-25, REQ-26 | 2     | La tabla de usuarios ya guarda el rol                            |
| Límites de pedidos                     | REQ-29, REQ-31  | 3     | Se guardarían en memoria; con varias instancias haría falta un almacén compartido, como Redis |
| GraphQL                                | REQ-30          | 3     | El núcleo de `catalog` no depende de cómo se expone              |

## 5. Riesgos y comprobaciones pendientes

| Riesgo                                                                      | Cómo se trata                                                                 |
|-----------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| Alguna librería todavía no es compatible con Spring Boot 4.1                | Se comprueba al armar el esqueleto. Si falla, se baja a Spring Boot 4.0       |
| Se desarrolla con JDK 25 y se entrega Java 21                               | Compilación con nivel 21 y ejecución en Docker con JDK 21. Si apareciera una diferencia, se instala un JDK 21 |
| SWAPI cambia su formato o deja de responder                                 | Su formato está encerrado en `swapi` y los tests usan respuestas grabadas      |
| SWAPI demora o corta a quien le hace muchos pedidos                         | Caché en memoria (D-15) y tiempos máximos de espera (D-05). Los tests no llaman a SWAPI |
| La paginación en memoria no escala si una entidad crece mucho               | Hoy son decenas de elementos; queda anotado en el ADR                          |
| El plazo: el MVP completo en un día                                         | Primero una historia completa de punta a punta y recién después el resto       |

## 6. Decisiones tomadas

El autor revisó cada decisión por separado, con sus alternativas, el 14/11/2026.

| Decisión | Tema                         | Opción elegida                                                        | Respecto del borrador |
|----------|------------------------------|-----------------------------------------------------------------------|-----------------------|
| D-01     | Plataforma                   | Spring Boot 4.1 sobre Java 21                                         | Sin cambios           |
| D-02     | Construcción                 | Maven con *wrapper*                                                   | Sin cambios           |
| D-03     | Organización del código      | Por funcionalidad, con una frontera hacia SWAPI                       | Sin cambios           |
| D-04     | Cuatro entidades             | Núcleo común y una parte fina por entidad                             | Sin cambios           |
| D-05     | Cliente de SWAPI             | `RestClient` con hilos virtuales                                      | Sin cambios           |
| D-06     | Paginación                   | Siempre se pide la página a SWAPI y se decide según cómo responde; páginas desde 1 | **Cambió:** el borrador fijaba la estrategia por entidad |
| D-07     | Autenticación                | JWT con el soporte de Spring Security                                 | Sin cambios           |
| D-08     | Usuarios                     | PostgreSQL, Spring Data JPA y Flyway; usuarios de prueba con Flyway en una carpeta aparte | **Cambió:** el borrador proponía un componente al arrancar |
| D-09     | Errores                      | Problem Details                                                       | Sin cambios           |
| D-10     | Documentación de la API      | Generada desde el código con springdoc-openapi                        | Sin cambios           |
| D-11     | Pruebas                      | Base real con Testcontainers, SWAPI simulada con WireMock y reglas de arquitectura con ArchUnit desde el MVP | Sin cambios |
| D-12     | Entorno local                | Docker Compose; JDK 25 compilando a nivel 21                          | Sin cambios           |
| D-13     | Documentación del código     | Javadoc en lo público y en lo no obvio                                | Sin cambios           |
| D-14     | Instrucciones para IA        | Se publica `AGENTS.md`                                                | Sin cambios           |
| D-15     | Caché de SWAPI               | En memoria, con vencimiento, desde el MVP                             | **Nueva:** surgió al medir los límites de SWAPI |

## 7. ADRs que salen de este RFC

| ADR      | Decisión                                     | Sale de        |
|----------|----------------------------------------------|----------------|
| ADR-0001 | Plataforma y herramienta de construcción     | D-01, D-02     |
| ADR-0002 | Organización del código                      | D-03, D-04     |
| ADR-0003 | Cliente de SWAPI                             | D-05           |
| ADR-0004 | Paginación y filtros                         | D-06           |
| ADR-0005 | Autenticación                                | D-07           |
| ADR-0006 | Usuarios: base de datos y carga inicial      | D-08           |
| ADR-0007 | Formato de los errores                       | D-09           |
| ADR-0008 | Documentación de la API                      | D-10           |
| ADR-0009 | Estrategia de pruebas                        | D-11           |
| ADR-0010 | Entorno local                                | D-12           |
| ADR-0011 | Caché de las respuestas de SWAPI             | D-15           |

D-13 y D-14 no llevan ADR: son reglas de trabajo. D-13 está en [`ARCHITECTURE.md`](../ARCHITECTURE.md#documentación-del-código) y D-14 se cumple con el archivo [`AGENTS.md`](../../AGENTS.md).

Los once ADRs están escritos y aceptados: ver el [índice](../adr/README.md).
