# Registro de uso de herramientas de IA

Qué se delegó en herramientas de IA en este proyecto, cómo se revisó y qué se corrigió (REQ-18). El equipo técnico de Conexa permitió usarlas y pidió documentar el flujo.

[`WORKFLOW.md`](WORKFLOW.md) describe cómo se definió el trabajo. Este documento cuenta lo que pasó, también donde no coincide.

| Campo | Valor |
|---|---|
| Estado | Al 15/11/2026, con los tickets #5 a #16 fusionados en `main`, la revisión final del MVP hecha y sus correcciones fusionadas |
| Cómo se escribió | Lo redactó una herramienta de IA en el ticket #16, a partir de la bitácora que se llevó durante el trabajo (no está publicada), de los pull requests y de las notas fechadas de los ADRs. Después de la revisión final lo actualizó la misma herramienta, a partir del informe de esa revisión y del pull request #30 |

## En resumen

- **Las herramientas de IA redactaron los documentos y escribieron el código y los tests.**
- **Qué construir y cómo lo decidió el autor.** Los requisitos, las decisiones técnicas del RFC y las decisiones del contrato de la API se le presentaron una por una, con alternativas y una recomendación, y eligió él. En varias corrigió la recomendación.
- **Hasta el ticket #5, el autor aprobó cada paso.** Desde el ticket #6 delegó también la revisión: los tests y los pull requests los revisó otra sesión de la herramienta de IA, y el autor no los revisó uno por uno.
- **Con todo fusionado, otra sesión de la herramienta hizo una revisión final del conjunto,** con la consigna de encontrar y demostrar fallas. Encontró cuatro importantes, que se corrigieron.
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

Lista de control de cada pull request, que armó la propia sesión supervisora:

1. Correr ella misma la verificación completa (`./mvnw -B -ntp clean verify`), sin confiar en el resultado que informa la sesión del ticket.
2. Comprobar que todas las tareas del ticket están hechas.
3. Comprobar que los tests de aceptación no cambiaron después de aprobados, comparando su commit con el final de la rama.
4. Leer el cambio: que respete [`ARCHITECTURE.md`](../ARCHITECTURE.md), que sea chico y que se entienda.
5. Comprobar que la descripción del pull request solo afirma cosas ciertas.
6. Levantar la aplicación con `docker compose` y probar lo nuevo contra la SWAPI real.

Esas revisiones ocurrieron en mensajes entre las sesiones. No quedaron como revisiones ni como comentarios en GitHub: los pull requests muestran el resultado, no el intercambio.

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

## Límites

**Qué cubre una revisión hecha por otra sesión de la herramienta de IA.** Lo que se puede comprobar: que la verificación completa pasa cuando la corre otro, que los tests aprobados no se tocaron, que cada criterio tiene su test, que la aplicación levanta y responde contra la SWAPI real, y que la descripción del pull request coincide con el cambio.

**Qué no cubre.**

- **No es independiente.** La sesión supervisora y las sesiones de ticket son la misma herramienta: tienen los mismos conocimientos y pueden compartir los mismos errores. Vale también para la revisión final: se hizo sin conocer las revisiones anteriores, pero es la misma herramienta. El flujo de trabajo le pide esa revisión a una persona justamente por lo que un test no mide: si el código es claro, si está bien diseñado y si es seguro.
- **Las decisiones de la sección anterior no pasaron por el autor** antes de fusionarse. Están escritas para que se puedan revisar después, una por una.
- **Los tests y el código de cada ticket los escribió la misma sesión.** Si entendió mal un criterio, el test repite el error. Lo que lo acota es que los criterios ya estaban aprobados por el autor y que los tests se revisaron antes del código, pero esa revisión también fue de la herramienta.

**Qué mostró la revisión final sobre los tests.** Que su cantidad no era protección en todos lados: nueve roturas hechas a propósito en el código, varias de seguridad, no hacían fallar ninguno. Ocho ya tienen un test que las detecta. La novena, quitar el tiempo máximo de conexión con SWAPI, sigue sin ninguno.

**Qué no se verificó, entre otras cosas.**

- El detalle de cada entidad se comparó campo por campo con una sola respuesta real de SWAPI: el personaje 1, la película 1, la nave 9 y el vehículo 4. A mano se probaron unos pocos más. SWAPI tiene 163 elementos.
- El escape de los caracteres especiales de una búsqueda se probó por la negativa (`name=(` ya no falla y `name=l.ke` ya no encuentra a Luke): ningún nombre real tiene un punto o un paréntesis.
- En el ticket #11, los tests unitarios del núcleo, del cliente de SWAPI y de la caché no se vieron fallar por separado antes de su código. El pull request lo dice, y lo cubren trece roturas hechas a propósito.
- El tiempo máximo de conexión con SWAPI no tiene un test propio. El vencimiento de la caché se prueba adelantando un reloj de prueba, no esperando una hora.
- El *wrapper* de Maven para Windows (`mvnw.cmd`) no se probó.
