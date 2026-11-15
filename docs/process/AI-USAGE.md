# Registro de uso de herramientas de IA

Qué se delegó en herramientas de IA en este proyecto, cómo se revisó y qué se corrigió (REQ-18). El equipo técnico de Conexa permitió usarlas y pidió documentar el flujo.

[`WORKFLOW.md`](WORKFLOW.md) describe cómo se definió el trabajo. Este documento cuenta lo que pasó, también donde no coincide.

| Campo | Valor |
|---|---|
| Estado | Al 15/11/2026, con el ciclo 1 cerrado (tickets #5 a #16, revisión final y sus correcciones) y el ciclo 2 implementado: los tickets #33, #35, #36, #37 y #38 están fusionados en `main`; del #34 está fusionado el archivo que describe el despliegue, y el servicio público todavía no existe. La revisión final del ciclo 2 está hecha, y este documento incluye sus correcciones |
| Cómo se escribió | Lo redactó una herramienta de IA en el ticket #16, a partir de la bitácora que se llevó durante el trabajo (no está publicada), de los pull requests y de las notas fechadas de los ADRs. Después de la revisión final lo actualizó la misma herramienta, a partir del informe de esa revisión y del pull request #30. La sección del ciclo 2 la redactó la sesión que preparó ese ciclo; lo que pasó al implementar se sumó en los pull requests de los tickets, y la sesión de la revisión final del ciclo 2 la completó después de encontrar que había quedado a medio actualizar |

## En resumen

- **Las herramientas de IA redactaron los documentos y escribieron el código y los tests.**
- **En el ciclo 1, qué construir y cómo lo decidió el autor.** Los requisitos, las decisiones técnicas del RFC y las decisiones del contrato de la API se le presentaron una por una, con alternativas y una recomendación, y eligió él. En varias corrigió la recomendación.
- **En el ciclo 2 delegó también las decisiones,** incluidas las de contrato. Está contado [más abajo](#ciclo-2).
- **Hasta el ticket #5, el autor aprobó cada paso.** Desde el ticket #6 delegó también la revisión: los tests y los pull requests los revisó otra sesión de la herramienta de IA, y el autor no los revisó uno por uno.
- **Con todo fusionado, otra sesión de la herramienta hizo una revisión final del conjunto,** con la consigna de encontrar y demostrar fallas. Encontró cuatro importantes, que se corrigieron. Al terminar el ciclo 2 se hizo otra igual, que encontró dos, las dos en la documentación.
- **Esas revisiones delegadas tienen límites,** y están [al final](#límites).

Una **sesión** es una conversación separada con la herramienta. No comparte memoria con las otras: lo que comparten es el repositorio, sus documentos y unas instrucciones escritas.

## Qué se delegó

| Etapa | Qué hizo la herramienta de IA | Qué hizo el autor |
|---|---|---|
| PRD (14/11) | Redactó el documento y planteó las preguntas abiertas | Respondió las cinco preguntas de producto, cada una con su motivo, fijó las prioridades y lo aprobó |
| RFC y ADRs (14/11) | Relevó alternativas y redactó el RFC y los ADRs | Revisó las quince decisiones una por una y las aceptó. Dos cambiaron respecto del borrador (D-06 y D-08) |
| Especificación técnica (14/11) | Redactó el contrato y los criterios de aceptación, a partir de respuestas reales de SWAPI | Revisó una por una las siete decisiones de contrato que el borrador dejaba abiertas y la aprobó |
| Tickets (14/11) | Redactó los doce tickets y los creó en GitHub | Decidió el reparto y pidió crearlos |
| Ticket #5: esqueleto del proyecto (14/11) | Escribió los tests y, después de su aprobación, el código | Aprobó los tests antes del código, y pidió por separado el pull request, su revisión y la fusión |
| Tickets #6 a #15: toda la funcionalidad (15/11) | Una sesión por ticket escribió los tests de aceptación y, después de su revisión, el código, la documentación y el pull request. Otra sesión, supervisora, revisó los tests, resolvió las dudas de diseño, revisó cada pull request y lo fusionó | Hizo el encargo: terminar el MVP con ese esquema, bajo las reglas del repositorio |
| Ticket #16: README final y este registro (15/11) | Igual que los anteriores: una sesión lo escribió, y la sesión supervisora revisó el pull request y lo fusionó | — |
| Revisión final del MVP (15/11) | Una sesión aparte revisó el conjunto ya fusionado y escribió un informe con lo que encontró | La pidió |
| Correcciones de la revisión final (15/11) | La sesión de la revisión escribió los tests y, después de que la sesión supervisora los revisó, el código y la documentación. La sesión supervisora abrió el pull request y lo fusionó | Pidió corregir los cuatro hallazgos importantes |
| Preparación del ciclo 2 (15/11) | Una sesión aparte relevó lo que hacía falta decidir y redactó los ADRs y la extensión de la especificación técnica. La sesión supervisora tomó las decisiones | Pidió el ciclo, fijó su alcance y delegó las decisiones. Ver [Ciclo 2](#ciclo-2) |
| Tickets #33 a #38: el ciclo 2 (15/11) | Igual que en el ciclo 1: una sesión por ticket escribió los tests de aceptación y, después de su revisión, el código, la documentación y el pull request. La sesión supervisora revisó los tests, eligió entre las alternativas de diseño que cada sesión le presentó, revisó cada pull request y lo fusionó | Aprobó los tests de aceptación del ticket #35. En lo demás no intervino |
| Revisión final del ciclo 2 y sus correcciones (15/11) | Una sesión aparte revisó el ciclo ya fusionado y escribió un informe. La sesión supervisora eligió qué corregir, y la sesión de la revisión lo corrigió, con los tests antes que el código | La pidió antes de cerrar el ciclo |

En GitHub, los commits, los pull requests y las fusiones figuran a nombre del autor: las herramientas trabajaron con su cuenta y no firman.

## Cómo se revisó

| Qué | Quién lo revisó | Cómo |
|---|---|---|
| PRD, RFC, ADRs, especificación técnica y tickets | El autor | Decisión por decisión, en conversación con la herramienta, antes de aprobar cada documento |
| Tests del ticket #5 | El autor | Antes de que existiera el código |
| Pull request del ticket #5 ([#18](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/18)) | La misma herramienta de IA que lo había escrito, a pedido del autor | Rompió a propósito cada regla de arquitectura para comprobar que su test la detecta, y encontró una diferencia con un ADR (JUnit 6 en vez de JUnit 5). No es una revisión independiente. La fusión la ordenó el autor |
| Tests de aceptación de los tickets #6 a #15 | La sesión supervisora | Antes del código: que cada criterio del ticket tenga su test, que el test compruebe lo que dice la especificación, que falle por el motivo correcto y que no se haya modificado ningún test anterior |
| Pull requests de los tickets #6 a #16 ([#19](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/19) a [#29](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/29)) | La sesión supervisora | Con la lista de control de abajo, antes de fusionar. **El autor no revisó esos pull requests uno por uno** |
| El MVP completo, con los doce tickets fusionados | Una sesión aparte de la herramienta de IA, sin conocer las conclusiones de las revisiones anteriores | Con la consigna de encontrar y demostrar fallas, no de confirmar que estaba bien. Trabajó sobre un clon limpio de GitHub: siguió el README, comparó cada criterio de aceptación con su test y rompió el código a propósito para ver si los tests lo detectaban, envió entradas mal formadas a la aplicación levantada, y revisó la seguridad, el comportamiento ante fallas de SWAPI y la caché, el código, la documentación contra lo construido y la higiene del repositorio y de su historia. Encontró 0 fallas críticas, 4 importantes y 10 menores, y dejó 9 observaciones |
| Pull request de las correcciones de la revisión final ([#30](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/30)) | La sesión supervisora | Revisó los tests antes del código. Después revisó el cambio completo: leyó el diff, corrió la verificación completa y probó cada caso contra la aplicación levantada con `docker compose`. Lo fusionó |
| ADRs y extensión de la especificación técnica del ciclo 2 ([#39](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/39)) | La sesión supervisora, que además había tomado esas decisiones | Antes de abrir el pull request: en GitHub se abrió y se fusionó con 9 segundos de diferencia. **El autor no los revisó** |
| Tests de aceptación de los tickets #33 a #38 | La sesión supervisora, salvo los del ticket #35, que aprobó el autor | Antes del código, con el mismo criterio que en el ciclo 1. En el ticket #33 no hay tests nuevos: lo que se revisó fue la prueba de romper un test a propósito, ver la ejecución en rojo y revertirlo |
| Pull requests de los tickets #33 a #38 ([#40](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/40) a [#45](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/45)) | La sesión supervisora | Con la misma lista de control, y desde el ticket #33 con la verificación automática de GitHub a la vista. **El autor no revisó ninguno** |
| El ciclo 2 completo, con cinco de sus seis tickets fusionados | Una sesión aparte de la herramienta de IA, sin conocer las conclusiones de las revisiones anteriores. Es la misma herramienta: no es una mirada independiente | Con la misma consigna que la del ciclo 1, sobre un clon limpio. Además de lo que hizo aquella, atacó las reglas por rol y el alta de usuarios, y midió los reintentos contra una SWAPI simulada propia. Encontró 0 fallas críticas, 2 importantes y 6 menores, y dejó 6 observaciones. De 17 roturas hechas a propósito, los tests detectaron 16 |

Lista de control de cada pull request, que armó la propia sesión supervisora:

1. Correr ella misma la verificación completa (`./mvnw -B -ntp clean verify`), sin confiar en el resultado que informa la sesión del ticket.
2. Comprobar que todas las tareas del ticket están hechas.
3. Comprobar que los tests de aceptación no cambiaron después de aprobados, comparando su commit con el final de la rama.
4. Leer el cambio: que respete [`ARCHITECTURE.md`](../ARCHITECTURE.md), que sea chico y que se entienda.
5. Comprobar que la descripción del pull request solo afirma cosas ciertas.
6. Levantar la aplicación con `docker compose` y probar lo nuevo contra la SWAPI real.

Esas revisiones ocurrieron en mensajes entre las sesiones. No quedaron como revisiones ni como comentarios en GitHub: los pull requests muestran el resultado, no el intercambio. Vale también para el ciclo 2: ninguno de sus pull requests tiene una revisión ni un comentario en GitHub, y por eso hay algunos fusionados a segundos o a pocos minutos de abrirse. Desde afuera no se puede comprobar que esas revisiones existieron; lo único que se ve es la verificación automática de cada uno.

## Qué se corrigió

### Correcciones del autor a la herramienta

Llegaron como objeciones y decisiones durante la conversación; el cambio lo escribió la herramienta.

| Qué proponía o afirmaba la herramienta | Qué objetó o decidió el autor | Qué quedó | Dónde |
|---|---|---|---|
| Devolver el identificador como número. Lo sostuvo en la primera revisión: ahorraba pedidos a SWAPI | Pidió justificar por qué no texto: si SWAPI entrega un identificador que no es un número, el contrato cambia | Texto. El argumento mezclaba el tipo de la respuesta con la validación de la entrada, y la herramienta lo retiró | [ADR-0012](../adr/0012-response-data-shape.md) |
| Guardar en la caché, durante una hora, los "no existe" de SWAPI | Preguntó quién vuelve a pedir un identificador que no existe: la caché se llenaría de entradas inútiles | No se guardan. La herramienta retiró la propuesta | [ADR-0011](../adr/0011-swapi-response-cache.md), nota del 14/11 |
| Un tamaño de página de 1 a 100, que venía del RFC sin motivo escrito | Preguntó de dónde salía el rango y si los extremos estaban incluidos | El rango quedó, con sus motivos en un ADR nuevo y un criterio que prueba los dos extremos (CA-05.5) | [ADR-0013](../adr/0013-parameter-limits-and-invalid-values.md) |
| Afirmó que, al llegar a 500 respuestas, la caché descarta una y conserva las demás | Preguntó quién lo decía. Ningún documento nombraba una librería de caché | Quedó anotado como un hueco y se resolvió en el ticket #9, comparando librerías | [ADR-0011](../adr/0011-swapi-response-cache.md), nota del 15/11 |
| Unir la protección del acceso (REQ-10) al ticket del login | La separó, para revisar por separado la emisión del token y su validación | Un ticket propio, el [#7](https://github.com/erickvaernet/conexa-star-wars-challenge/issues/7) | Bitácora del [README](../../README.md#bitácora), 14/11 |
| En el ticket #5 pasó una tarea al ticket #8 (la SWAPI simulada), sin consultar | Un ticket aprobado se cumple completo; si una tarea parece no corresponder, se pregunta antes | Se hizo en el ticket #5, con el test antes que el código | Commits del [pull request #18](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/18) |

En las etapas anteriores, la revisión cambió dos de las quince decisiones del RFC (la tabla "Respecto del borrador" de su [sección 6](../rfc/RFC-001-architecture.md#6-decisiones-tomadas)) y una de las siete decisiones de contrato de la especificación.

### Correcciones de la sesión supervisora a las sesiones de ticket

| Ticket | Qué había hecho o propuesto la sesión del ticket | Qué pidió la sesión supervisora | Cuándo | Dónde |
|---|---|---|---|---|
| #7 | El `401` no llevaba la cabecera `WWW-Authenticate`; lo había anotado como pendiente para otro ticket | Hacerlo en ese ticket: el estándar de HTTP la exige en todo `401`. Se sumó con sus tests antes | Con el pull request abierto | [ADR-0005](../adr/0005-authentication.md); [#20](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/20) |
| #8 | El login estaba repetido en las cuatro clases nuevas de tests | Una pieza de apoyo compartida para el login. Además, que `page` y `size` figuren como enteros en la documentación de la API, aunque se reciban como texto | Al revisar los tests y el diseño, antes del código | [#21](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/21) |
| #10 | Responder `502` si SWAPI no enviaba cualquiera de los campos del detalle | Solo si faltan el identificador o el nombre. Cualquier otro texto ausente llega como `null` y una relación ausente como lista vacía: se había visto la respuesta real de un solo personaje de 82 | Al revisar los tests y el diseño, antes del código | [ADR-0012](../adr/0012-response-data-shape.md), nota del 15/11 |
| #11 | — | Un test más: que un listado completo y una búsqueda no compartan la respuesta guardada. Y que el pull request dijera que un criterio (CA-08.1) ya pasaba antes de escribir el código | Al revisar los tests y el diseño, antes del código | [#24](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/24) |

De los diez pull requests, uno recibió una corrección después de abierto (el #20). Los otros nueve se fusionaron como llegaron: los cambios que pidió la sesión supervisora fueron antes, al revisar los tests y el diseño. Los dos posteriores, el #29 y el #30, también se fusionaron como llegaron.

### Correcciones de la revisión final

La revisión final probó la aplicación ya terminada. Lo que se corrigió a partir de ella está en el [pull request #30](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/30):

| Qué pasaba | Qué quedó | Dónde |
|---|---|---|
| Un parámetro mal codificado (`name=100%`) respondía `500`, también en el control de estado, sin sesión | Responde `400` | [ADR-0007](../adr/0007-error-format.md) |
| El login respondía `500` a un usuario con el carácter nulo, y no limitaba el largo de sus campos | Un usuario de más de 50 caracteres o con caracteres de control, y una contraseña de más de 72, responden `400` | [ADR-0005](../adr/0005-authentication.md) |
| Una dirección o un método que el servidor rechaza respondían `401`, aun con un token válido | Responden `400` o `405`, con la dirección que se pidió | [ADR-0007](../adr/0007-error-format.md) y [ADR-0005](../adr/0005-authentication.md) |
| Nueve roturas hechas a propósito en el código no hacían fallar ninguno de los 715 tests | Ocho hacen fallar un test nuevo. La novena sigue sin test: ver [Límites](#límites) | Pull request #30 |
| Si SWAPI empezaba a responder y se cortaba a mitad, el error era `502` | Es `504` | [ADR-0003](../adr/0003-swapi-client.md) |
| `page` y `size` aceptaban un signo y dígitos de otros alfabetos | Solo los dígitos del 0 al 9 | [ADR-0013](../adr/0013-parameter-limits-and-invalid-values.md) |
| La documentación de la API numeraba los pedidos (`list_2`) | Cada pedido tiene su nombre (`listPeople`) | [ADR-0008](../adr/0008-api-documentation.md) |
| Un comentario del código decía algo que no era cierto, y había un manejo de error que no se podía alcanzar | Comentario corregido y manejo borrado | Pull request #30 |

Quién decidió qué:

- **El autor** pidió corregir los cuatro hallazgos importantes, que son las cuatro primeras filas.
- **La sesión supervisora** decidió que entraran también cuatro de los menores, que son las otras cuatro filas, y tomó las decisiones de detalle. Por ejemplo, que un método que no es de HTTP responda `400` y no `501`, y que dos pedidos sin sesión pasen de responder `401` a responder `400` y `405` ([ADR-0007](../adr/0007-error-format.md) y [ADR-0005](../adr/0005-authentication.md), notas del 15/11/2026).
- **La sesión de la revisión** propuso cómo corregir cada hallazgo y lo escribió, con los tests antes que el código.

Parte de los hallazgos de documentación de la misma revisión se corrigió aparte, en un pull request solo de documentos.

### Decisiones que tomó la sesión supervisora

Al implementar aparecieron decisiones que ni la especificación ni los ADRs cubrían. Las resolvió la sesión supervisora sin consultar al autor: el encargo era detenerse solo si algo contradecía la especificación o un ADR aceptado. Cada una quedó escrita con su porqué y sus alternativas en las notas fechadas del 15/11/2026 de los [ADRs](../adr/README.md), y en la sección "Decisiones tomadas en este ticket" de cada pull request. Las de más peso:

- Los textos de error de la API están en inglés ([ADR-0007](../adr/0007-error-format.md)).
- Un token vencido se rechaza sin tolerancia, y todo `401` es igual ([ADR-0005](../adr/0005-authentication.md)).
- La caché usa Caffeine de forma directa, sin las anotaciones de Spring ([ADR-0011](../adr/0011-swapi-response-cache.md)).
- Un valor que SWAPI no envía llega como `null` ([ADR-0012](../adr/0012-response-data-shape.md)).
- En Swagger UI, el login trae de ejemplo al usuario de prueba, con su contraseña ([ADR-0008](../adr/0008-api-documentation.md)).

La especificación técnica, que el autor aprobó el 14/11/2026, recibió después dos cambios autorizados por la sesión supervisora y no por él: el ejemplo de error pasó a llevar el texto real en inglés, y la tabla de configuración sumó cuatro variables (pull requests [#21](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/21) y [#22](https://github.com/erickvaernet/conexa-star-wars-challenge/pull/22)).

## Ciclo 2

Cómo se decidió el ciclo 2 antes de empezar a programar, lo que pasó al implementar cada ticket y lo que encontró su revisión final.

**Qué pidió el autor.**

- Hacer el ciclo 2 con el mismo esquema delegado con el que se terminó el MVP: una sesión supervisora que reparte el trabajo, lo revisa y lo fusiona.
- El alcance: los siete requisitos "Debería" (REQ-20 a REQ-26). Lo que no entre pasa al ciclo 3.
- **Delegó también las decisiones, incluidas las de contrato.** En el ciclo 1 las había tomado él, una por una. En este no revisó ninguna antes de que quedara escrita.

**Cómo se tomaron.**

| Paso | Quién | Qué hizo |
|---|---|---|
| Relevamiento | Una sesión aparte de la herramienta de IA | Leyó los documentos y el código para separar lo que el MVP ya había dejado hecho de lo que faltaba decidir. Comprobó contra la SWAPI real, con once pedidos, cómo responde cuando se le piden los datos completos. Midió el arranque de la aplicación con la memoria y el procesador de un plan gratuito. Relevó, con sus fuentes, los planes gratuitos vigentes y las opciones de reintentos |
| Propuesta | La misma sesión | Presentó nueve decisiones que cambian lo que ve quien usa la API o lo que tiene que hacer el autor, cada una con alternativas y una recomendación, y aparte una lista de decisiones técnicas. Redactó los borradores de los ADRs, de la extensión de la especificación técnica y de los tickets |
| Decisión | La sesión supervisora | Leyó el informe, las decisiones y la extensión completa, y eligió. Aprobó las decisiones técnicas como estaban |
| Lo que queda para el autor | El autor | Crear las cuentas del despliegue público y cargar ahí los datos de la base. Nadie más puede hacerlo |

**Qué cambió la sesión supervisora respecto de lo propuesto.** De las nueve decisiones, siguió la recomendación en ocho y la cambió en una: la propuesta era mantener despierto el servicio gratuito con un pedido cada diez minutos, y decidió no hacerlo, porque no se pudo comprobar que las condiciones del servicio lo permitan ([ADR-0015](../adr/0015-public-deployment.md)).

**Dónde quedó.** En siete ADRs, del [ADR-0014](../adr/0014-continuous-verification.md) al [ADR-0020](../adr/0020-swapi-failure-tolerance.md); en la [especificación técnica](../spec/SPEC.md), que lleva una nota que lo dice; y en el historial de cambios del [PRD](../requirements/PRD.md#historial-de-cambios), con el orden del ciclo.

**Qué hay que saber al leerlo.**

- **Estas decisiones no pasaron por el autor.** Están escritas con sus alternativas para que se puedan revisar después, una por una. Entre ellas hay reglas que quedan a la vista de quien usa la API: el nombre de un parámetro, la política de contraseñas y el formato del detalle de los errores.
- **Quien propuso y quien decidió son la misma herramienta,** en dos sesiones. Vale el mismo límite que para las revisiones delegadas: no es una mirada independiente.
- **Dos concesiones están dichas en su ADR:** el mínimo de la contraseña queda por debajo de la guía de referencia vigente ([ADR-0018](../adr/0018-user-creation.md)), y una parte de la historia del REQ-21, entender que una sesión venció, queda sin cubrir ([ADR-0016](../adr/0016-validation-error-detail.md)).
- **Lo relevado sobre los planes gratuitos no se pudo leer todo de primera mano:** las páginas de uno de los servicios no abrieron desde el entorno de trabajo, y sus datos se tomaron a través de un lector intermedio y de una búsqueda. El archivo del despliegue que se propone todavía no se probó contra el servicio.

**Lo que pasó al implementarlo.**

- **Ticket #33, la verificación automática.** No tiene tests de aceptación: sus criterios se comprueban por revisión. La prueba fue romper un test a propósito, ver la ejecución en rojo en GitHub y revertirlo; la revisó la sesión supervisora, que también revisó el pull request y lo fusionó. El autor no intervino.
- **Ticket #34, el despliegue público.** El test de aceptación y el pull request con el archivo del despliegue los revisó la sesión supervisora, que lo fusionó sin cerrar el ticket: falta crear el servicio, que es un paso del autor. El archivo sigue sin probarse contra el servicio.
- **Ticket #35, el detalle de los errores de validación.** Los tests de aceptación los aprobó el autor directamente, antes del código, desde la sesión del ticket y sin comentarios. El pull request lo revisó la sesión supervisora.
- **Ticket #36, el rol de administrador y el alta de usuarios.** Los tests de aceptación los revisó la sesión supervisora, antes del código. Al escribirlos, la sesión del ticket encontró una contradicción en la extensión de la especificación (una contraseña de solo espacios se aceptaba en el alta y el login la rechaza) y presentó tres opciones; la sesión supervisora eligió una y autorizó corregir la especificación ([ADR-0018](../adr/0018-user-creation.md), nota del 15/11/2026). El autor no intervino. El pull request lo revisó la sesión supervisora.
- **Ticket #37, los datos completos a pedido en los listados.** Los tests de aceptación y el diseño los aprobó la sesión supervisora, antes del código; el autor no los revisó. La sesión del ticket le presentó tres decisiones, con sus alternativas, y la sesión supervisora eligió: documentar un elemento como "uno de dos" con un tipo por entidad que nada implementa y una conversión que el compilador no puede comprobar; sumar una línea a una grabación de SWAPI que ya existía; y cambiar lo que esperaban cinco tests unitarios de la documentación. Están contadas en el [ADR-0019](../adr/0019-expanded-listings.md), nota del 15/11/2026. El pull request lo revisó y lo fusionó la sesión supervisora.
- **Ticket #38, la tolerancia a fallas de SWAPI.** Los tests de aceptación y el diseño los aprobó la sesión supervisora, antes del código; el autor no los revisó. Antes de diseñar, la sesión del ticket probó con un programa suelto la pieza de Spring Framework que reintenta y midió qué informa el cliente HTTP ante cada falla. Le presentó tres decisiones a la sesión supervisora, con sus alternativas, y esta eligió: que cada intento traduzca su propia falla, en vez de reintentar sobre la falla sin traducir, que era lo que pedía el encargo; reintentar también cuando en lugar de una respuesta HTTP llegan datos que no lo son; y aceptar una espera de cero, sin tope. La tercera precisó la extensión de la especificación, con autorización de la sesión supervisora. Están contadas en el [ADR-0020](../adr/0020-swapi-failure-tolerance.md), nota del 15/11/2026. La primera ejecución de la verificación automática del pull request falló en un test unitario nuevo, el de una conexión que se corta después de la primera línea de la respuesta. La sesión supervisora lo vio y lo atribuyó a una diferencia entre versiones de Java: ese diagnóstico estaba mal. La sesión del ticket midió el caso cuarenta veces con cada versión y encontró que era una carrera entre dos resultados, los dos válidos, que cambia de una corrida a otra con la misma versión. El test pasó a afirmar solo lo que no varía, lo que había quedado escrito se corrigió y el código no cambió. El pull request lo revisó y lo fusionó la sesión supervisora.

**La revisión final del ciclo 2.** La hizo una sesión aparte, con cinco de los seis tickets fusionados y antes de etiquetar la versión.

- **Qué encontró.** Ningún error de comportamiento. Los dos hallazgos importantes eran de este registro y de su resumen en el README:
  - El resumen del README seguía diciendo que el autor había decidido el contrato de la API "una por una", que es cierto para el ciclo 1 y no para el ciclo 2.
  - Esta sección decía "ciclo 2 preparado" con el ciclo ya implementado, no contaba los tickets #33 y #34, y daba por abiertos dos pull requests ya fusionados.
- **Qué se corrigió,** además de esos dos, en un solo pull request:
  - Los ejemplos de la documentación de la API, que mostraban una página que la API nunca responde y un campo que ningún error tiene.
  - El texto del `405`, que repetía el método recibido.
  - Un test de aceptación que comparaba un objeto consigo mismo (el de CA-24.2).
  - Cuatro precisiones de los documentos.
- **Qué quedó como está, declarado:** la conversión que el compilador no puede comprobar en los listados ([ADR-0019](../adr/0019-expanded-listings.md)), el bloque del reintento escrito dos veces en el cliente de SWAPI y la caché sin tipos. Las tres son observaciones de diseño de esa revisión, no errores.
- **Quién decidió qué.** La sesión supervisora eligió qué corregir y qué no; el autor no lo revisó antes. La sesión de la revisión escribió las correcciones, con los tests antes que el código. Cambió dos tests ya aprobados, con autorización de la sesión supervisora: el de CA-24.2, y el test unitario que fijaba el texto anterior del `405`.

## Límites

**Qué cubre una revisión hecha por otra sesión de la herramienta de IA.** Lo que se puede comprobar: que la verificación completa pasa cuando la corre otro, que los tests aprobados no se tocaron, que cada criterio tiene su test, que la aplicación levanta y responde contra la SWAPI real, y que la descripción del pull request coincide con el cambio.

**Qué no cubre.**

- **No es independiente.** La sesión supervisora y las sesiones de ticket son la misma herramienta: tienen los mismos conocimientos y pueden compartir los mismos errores. Vale también para la revisión final: se hizo sin conocer las revisiones anteriores, pero es la misma herramienta. El flujo de trabajo le pide esa revisión a una persona justamente por lo que un test no mide: si el código es claro, si está bien diseñado y si es seguro.
- **Las decisiones de la sección anterior no pasaron por el autor** antes de fusionarse. Están escritas para que se puedan revisar después, una por una.
- **Los tests y el código de cada ticket los escribió la misma sesión.** Si entendió mal un criterio, el test repite el error. Lo que lo acota es que los criterios ya estaban aprobados por el autor y que los tests se revisaron antes del código, pero esa revisión también fue de la herramienta.

**Qué mostró la revisión final sobre los tests.** Que su cantidad no era protección en todos lados: nueve roturas hechas a propósito en el código, varias de seguridad, no hacían fallar ninguno. Ocho ya tienen un test que las detecta. La novena, quitar el tiempo máximo de conexión con SWAPI, sigue sin ninguno. La revisión final del ciclo 2 repitió la prueba sobre el código nuevo, con diecisiete roturas: los tests detectaron dieciséis. La que no, reintentar un `408` de SWAPI, ya tiene su caso.

**Qué no se verificó, entre otras cosas.**

- El detalle de cada entidad se comparó campo por campo con una sola respuesta real de SWAPI: el personaje 1, la película 1, la nave 9 y el vehículo 4. A mano se probaron unos pocos más. SWAPI tiene 163 elementos.
- El escape de los caracteres especiales de una búsqueda se probó por la negativa (`name=(` ya no falla y `name=l.ke` ya no encuentra a Luke): ningún nombre real tiene un punto o un paréntesis.
- En el ticket #11, los tests unitarios del núcleo, del cliente de SWAPI y de la caché no se vieron fallar por separado antes de su código. El pull request lo dice, y lo cubren trece roturas hechas a propósito.
- El tiempo máximo de conexión con SWAPI no tiene un test propio. El vencimiento de la caché se prueba adelantando un reloj de prueba, no esperando una hora.
- El *wrapper* de Maven para Windows (`mvnw.cmd`) no se probó.
- Hasta el ticket #33, los tests de aceptación y `docker compose` no se habían corrido con Docker Engine, ni los tests sobre un JDK 21. Desde ese ticket corren también así, en el ejecutor de GitHub, en cada pull request y en cada cambio de `main` ([ADR-0014](../adr/0014-continuous-verification.md)). En la primera ejecución pasaron sin cambios.
- El archivo del despliegue público (`render.yaml`) no se probó contra el servicio, que todavía no existe. Su formato se revisó contra la referencia que el servicio publica en GitHub, porque su sitio de documentación no abrió desde el entorno de trabajo, y la imagen se probó en desarrollo con la memoria y el procesador del plan gratuito. Lo que queda por comprobar con el servicio creado está listado en las notas del [ADR-0015](../adr/0015-public-deployment.md#notas-posteriores).
