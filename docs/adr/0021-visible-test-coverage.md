# ADR-0021: Cobertura de tests visible: un distintivo de valor fijo que la verificación automática comprueba

| Campo      | Valor                                                                       |
|------------|-----------------------------------------------------------------------------|
| Estado     | Aceptado                                                                    |
| Fecha      | 15/11/2026                                                                  |
| Requisitos | REQ-27, REQ-23                                                              |
| Origen     | [ADR-0014](0014-continuous-verification.md), que dejó guardado el informe de cobertura y este requisito para el ciclo 3 |

## Contexto

El PRD pide que quien corrige pueda ver qué porcentaje del código está cubierto por tests "sin tener que correr nada", con "un indicador en el README" (REQ-27).

Qué hay hecho:

- **El informe ya existe.** JaCoCo está en el `pom.xml` desde el MVP ([ADR-0009](0009-testing-strategy.md)), con dos agentes, uno para los tests unitarios y otro para los de aceptación, que escriben en el mismo archivo. El informe se genera al final de `./mvnw verify`, en `target/site/jacoco`.
- **La verificación automática lo guarda** como adjunto de cada ejecución, con el nombre `coverage-report` ([ADR-0014](0014-continuous-verification.md)).
- **Nada muestra el número.** Para verlo hay que correr los tests o bajar el adjunto, que GitHub solo deja bajar a quien inició sesión y conserva 90 días.

Medido el 15/11/2026 sobre `main`, en la versión `v0.2.0`:

| Qué se corre                                    | Líneas cubiertas | Cobertura de líneas | Cobertura de ramas |
|-------------------------------------------------|------------------|---------------------|--------------------|
| Solo los tests unitarios y de arquitectura (`./mvnw test`) | 562 de 586 | 95,90 %             | 92,42 %            |
| Todos los tests (`./mvnw verify`)               | 584 de 586       | 99,66 %             | 96,68 %            |

- **El informe de `./mvnw verify` une de verdad los dos grupos de tests:** lista dos sesiones de medición, una por grupo, y los de aceptación suman 22 líneas a las que cubren los unitarios.
- **El número es el mismo en una máquina de desarrollo y en GitHub:** el adjunto de la ejecución de `main` da las mismas 584 de 586 líneas.
- **El código propio son 586 líneas ejecutables:** un punto porcentual son casi seis líneas, y una línea, 0,17 puntos.

Qué condiciona la decisión:

- Ninguna sesión de trabajo crea cuentas: lo que pida una cuenta nueva lo tiene que hacer el autor en persona.
- La definición de la verificación automática pide solo permiso de lectura sobre el repositorio ([ADR-0014](0014-continuous-verification.md)), y en el repositorio el único autor de los commits es el autor.
- Un número escrito a mano en el README queda viejo con el primer cambio que nadie recuerde actualizar.

## Decisión

El README muestra la cobertura con **un distintivo de valor fijo**, al lado del de la verificación automática, y **la verificación automática falla si ese número no es el que mide**.

| Tema                    | Decisión                                                                                                         |
|-------------------------|------------------------------------------------------------------------------------------------------------------|
| Qué se muestra          | Una imagen de [shields.io](https://shields.io) con el número escrito en su dirección (`…/badge/cobertura-99%25-…`). No hay ningún servicio de cobertura detrás: la imagen dice lo que dice la dirección |
| Qué mide                | La **cobertura de líneas** del informe de JaCoCo de `./mvnw verify`: todo el código de `src/main`, sin exclusiones, con los tests unitarios, los de arquitectura y los de aceptación juntos |
| Redondeo                | **Hacia abajo, a un número entero.** 99,66 % se muestra como 99 %: el distintivo nunca dice más de lo medido |
| Que no quede viejo      | Un paso de la verificación automática compara el número del README con el medido, redondeado igual. Si no coinciden, falla y dice qué número escribir |
| Mínimo                  | La misma comprobación falla si la cobertura de líneas baja de **95 %** |
| Dónde vive              | En un solo guion del repositorio, `scripts/check-coverage.sh`, que lee el informe y el README. El trabajo `verify` lo corre después de los tests, y se corre igual en una máquina de desarrollo, después de `./mvnw verify` |
| A la vista en cada ejecución | El mismo guion escribe el porcentaje, con las líneas cubiertas y las totales, en el resumen de la ejecución de GitHub |
| El informe completo     | Sigue siendo el adjunto `coverage-report` de cada ejecución. El README dice dónde está, en la sección "Tests" |

## Alternativas consideradas

Cómo se muestra el número:

| Alternativa                                         | A favor                                                                 | En contra                                                                                          |
|-----------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **Distintivo de valor fijo, comprobado en cada ejecución** | Sin cuentas ni permisos nuevos; el número vive en el README, a la vista en cada cambio que lo mueve; nada escribe en el repositorio salvo el autor | Hay que editar el README a mano cuando el entero cambia; depende de un servicio de imágenes de terceros |
| Un servicio de cobertura (Codecov, Coveralls)       | El distintivo se actualiza solo; muestra la evolución y el detalle por archivo | Pide una cuenta que tendría que crear el autor, y en general una clave cargada en GitHub; envía el informe a un tercero; es una integración más para un número |
| Que la verificación escriba el distintivo en el repositorio o en una rama | Se actualiza solo, sin servicios externos | Commits que no son del autor en un repositorio donde el único autor es él; la definición tendría que pedir permiso de escritura, que hoy no tiene |
| Publicar el informe en GitHub Pages, con un distintivo que lea el número de ahí | El informe completo se abre en el navegador, sin iniciar sesión ni bajar nada; el distintivo se actualiza solo y no hay nada que comparar | Cambia la configuración del repositorio (activar Pages) y suma un sitio público que mantener; la definición gana un trabajo con permiso para publicar; el distintivo pasa a depender de dos servicios en cada vista. Son más piezas nuevas, a un día de la entrega, para mostrar un número |
| El número como texto en el README, sin imagen       | No depende de ningún servicio                                           | No se ve de un vistazo junto al estado de la verificación, que es donde un evaluador lo busca       |
| No mostrarlo: solo el informe adjunto               | Nada que mantener                                                       | No cumple el requisito: hay que iniciar sesión, bajar un archivo y abrirlo                          |

Dónde vive la comparación:

| Alternativa                                         | A favor                                                                 | En contra                                                                                          |
|-----------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **Un guion que hace las tres cosas** (resumen, comparación y mínimo) | Una sola pieza y un solo paso; su mensaje dice qué corregir; se corre igual en una máquina de desarrollo; en el pull request se ve aparte de los tests | Es un guion de terminal: no corre con `./mvnw verify` a secas |
| La regla `check` de JaCoCo para el mínimo, más un guion para la comparación | El mínimo se comprueba dentro de `./mvnw verify`, con una pieza estándar | Dos piezas y dos lugares donde vive un número; un mínimo incumplido se vería como una falla del paso de los tests |
| Un test de Java                                     | Queda junto a los demás tests                                           | El informe se genera después de que corren los tests: un test no puede leer el de su propia ejecución |

Qué número y cómo se redondea:

| Alternativa                             | A favor                                                        | En contra                                                                                          |
|-----------------------------------------|----------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **Líneas, hacia abajo, a un entero**    | Es el número que la gente entiende por "cobertura"; nunca dice de más; cambia pocas veces | Esconde los decimales: 99,1 % y 99,9 % se ven igual                    |
| Con un decimal                          | Más preciso                                                    | Con 586 líneas, cada línea mueve 0,17 puntos: casi todo cambio obligaría a editar el README, y tres tickets en paralelo chocarían en esa línea |
| Redondeo al más cercano                 | Es el redondeo habitual                                        | 99,66 % se mostraría como 100 %, que es falso                                                       |
| Ramas o instrucciones en vez de líneas  | Las ramas son una medida más exigente                          | Son menos conocidas, y el informe completo ya las muestra                                           |

## Consecuencias

- Quien abre el repositorio ve el porcentaje en la primera pantalla, junto al estado de la verificación automática.
- **El número del README no puede quedar viejo ni decir de más:** un cambio que lo mueva deja la verificación en rojo hasta que el README lo acompañe, y el cambio de número queda a la vista en el pull request.
- **La cobertura no puede bajar en silencio.** Por debajo de 95 % la verificación falla. Bajar ese mínimo es editar el guion, también a la vista. Queda lejos del 80 % que el PRD fija como métrica y cerca del valor actual: son unas 27 líneas de margen.
- **El número se edita a mano, y pocas veces.** Con el valor de hoy, el entero cambia recién cuando las líneas sin cubrir pasan de cinco, o cuando no queda ninguna.
- **Dos cambios que pasan por separado pueden fallar juntos.** Si dos pull requests mueven la cobertura y el entero cambia recién al sumarlos, cada uno pasa su verificación y la de `main` falla después de fusionar el segundo. Se evita actualizando cada rama con `main` antes de fusionarla, que vuelve a correr la verificación con todo junto, y si igual ocurre se corrige con un commit que cambia el número. Es el costo de que el número viva en el repositorio.
- Mientras la verificación de `main` esté en rojo por esto, el despliegue público no toma el cambio: despliega solo lo que pasó la verificación ([ADR-0015](0015-public-deployment.md)).
- La definición de la verificación automática sigue pidiendo solo permiso de lectura, y no se carga ningún secreto en GitHub.
- **El distintivo depende de un servicio de imágenes de terceros.** Si no responde, no se ve la imagen; el número sigue escrito en el README y en el resumen de cada ejecución. Ese servicio no recibe ningún dato del proyecto: solo el pedido de la imagen.
- **El informe completo sigue pidiendo iniciar sesión en GitHub** para bajarlo, y GitHub lo conserva 90 días. El porcentaje no: está en el README y en el resumen de cada ejecución.
- Un porcentaje alto no dice que los tests comprueben lo que importa. Las dos revisiones finales lo midieron de otra forma, rompiendo el código a propósito ([registro de uso de herramientas de IA](../process/AI-USAGE.md#límites)).
- Si el proyecto creciera o sumara colaboradores, un servicio de cobertura o el informe publicado en GitHub Pages pasarían a valer lo que cuestan: se registraría en un ADR nuevo.

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **15/11/2026.** Lo que apareció al implementar (ticket #48, [pull request #52](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/52)):
  - **El número medido no cambió:** 584 de 586 líneas, 99,66 %, en una máquina de desarrollo y en GitHub. El distintivo arranca en 99.
  - **El guion corrió igual en los dos lugares, sin instalar nada.** Usa `bash`, `awk` y `grep`, y hace las cuentas con números enteros, para no depender de cómo escribe los decimales cada sistema. Se revisó una vez con `shellcheck`, que el ejecutor de GitHub ya trae, en un paso temporal que no quedó en la definición: no encontró nada.
  - **GitHub muestra el resumen de una ejecución solo a quien inició sesión.** Corrige una consecuencia de este ADR, que daba por visible sin iniciar sesión el porcentaje del resumen. Se comprobó abriendo la página de una ejecución con un navegador sin sesión: muestra el estado, los trabajos, las anotaciones y la lista de adjuntos, pero no el resumen ni los registros.
    - **Qué se hizo.** El guion deja el porcentaje en dos lugares de la página de la ejecución: en el resumen, como pedía la decisión, y en una anotación, que se ve sin iniciar sesión. Cuando una comprobación falla, su mensaje va a los mismos dos lugares. Se comprobó con el mismo navegador sin sesión sobre una [ejecución](https://github.com/erickvaernet/conexa-star-wars-challenge/actions/runs/43785394734): la anotación dice "Line coverage: 99.66 % (584 of 586 lines)".
    - **Alternativa descartada.** Dejarlo solo en el resumen. El porcentaje de cada ejecución lo habría visto solo quien inició sesión, y el requisito habla de quien corrige, que puede no tener una cuenta.
    - **Qué cuesta.** Cada ejecución lleva una anotación, que también aparece en la pestaña de comprobaciones del pull request.
  - **Sin iniciar sesión, entonces, el porcentaje está en dos lugares:** el distintivo del README, como entero, y la anotación de cada ejecución, con dos decimales y las líneas. El informe completo sigue pidiendo iniciar sesión.
  - **Los mensajes de error son tres, y dos tienen una variante** que la decisión no nombraba. Si el informe existe pero no cuenta ninguna línea, el guion pide volver a correr `./mvnw verify`, igual que cuando falta. Si el README no tiene exactamente un distintivo de cobertura, dice cuántos encontró y con qué dirección tiene que quedar uno solo.
  - **Una corrida informa todo lo que hay que corregir.** Si la cobertura baja del mínimo y además el distintivo quedó viejo, salen los dos mensajes. La alternativa, cortar en el primero, obligaba a correrlo dos veces.
  - **El porcentaje con dos decimales se redondea al más cercano, pero nunca sube al entero siguiente:** 94,996 % se imprime como 94,99 %, no como 95,00 %. Así el número impreso no contradice al entero redondeado hacia abajo, que es el que se compara con el mínimo y con el distintivo.
  - **El guion reconoce el distintivo por su dirección,** `img.shields.io/badge/cobertura-<número>%25`. Cambiar el texto del distintivo o el servicio de imágenes pide cambiar también esa línea del guion.
  - **CA-27.3 se comprobó una vez en GitHub.** Un commit cambió a propósito el número del distintivo de 99 a 98: su [ejecución](https://github.com/erickvaernet/conexa-star-wars-challenge/actions/runs/43784887290) quedó en rojo, con los 1200 tests en verde y el paso nuevo diciendo qué número escribir. El commit siguiente lo revirtió y la [ejecución](https://github.com/erickvaernet/conexa-star-wars-challenge/actions/runs/43785135760) volvió a pasar. En la ejecución en rojo el trabajo `compose` pasó, como con un test roto ([ADR-0014](0014-continuous-verification.md#notas-posteriores)).
  - **Si los tests fallan, el guion no corre:** su paso se saltea, como cualquier paso que sigue a uno que falló, y esa ejecución no muestra ningún porcentaje.
