# ADR-0008: Documentación de la API

| Campo      | Valor                                                        |
|------------|--------------------------------------------------------------|
| Estado     | Aceptado                                                     |
| Fecha      | 14/11/2026                                                   |
| Requisitos | REQ-12                                                       |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), decisión D-10     |

## Contexto

El equipo técnico de Conexa indicó que no hace falta interfaz gráfica: alcanza con la API documentada con Swagger. Esa documentación es la forma en que quien corrige va a probar la aplicación, incluido el login.

## Decisión

La documentación se **genera desde el código con springdoc-openapi**, que también publica la página de Swagger UI.

- Incluye el esquema de seguridad, para cargar el token con el botón "Authorize".
- Cada pedido documenta sus parámetros, sus respuestas y sus errores.
- La documentación y Swagger UI son públicas: no exigen sesión.

## Alternativas consideradas

| Alternativa                          | A favor                                              | En contra                                                  |
|--------------------------------------|------------------------------------------------------|------------------------------------------------------------|
| **Generada desde el código**         | Nunca queda desactualizada; incluye Swagger UI       | Hay que anotar el código para que quede completa            |
| Escrita a mano en un archivo OpenAPI | Control total del contrato                           | Puede desviarse del código sin que nadie lo note            |

## Consecuencias

- La especificación técnica define el contrato antes de programar. Una vez implementado, la documentación generada es la referencia.
- Mantener cada entidad con su propio controlador (ver [ADR-0002](0002-code-organization.md)) permite que Swagger muestre los datos propios de cada una.
- Las anotaciones de documentación quedan en los controladores, junto al código que describen.

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **15/11/2026.** Precisiones que aparecieron al documentar el primer listado (ticket #8):
  - **`page` y `size` figuran como enteros.** La aplicación los recibe como texto para validarlos en un solo lugar ([ADR-0013](0013-parameter-limits-and-invalid-values.md)), pero la documentación los declara como lo que son: enteros opcionales, con su mínimo, su máximo y su valor por defecto. Un test lo comprueba sobre la documentación generada.
  - **Los parámetros y los errores comunes se documentan una sola vez.** Están en `catalog`, y el controlador de cada entidad los toma de ahí en vez de repetirlos.
  - **El esquema de seguridad** se suma con el ticket de la documentación interactiva (REQ-12).
- **15/11/2026.** Lo que se decidió al completar la documentación interactiva (ticket #15):
  - **El token se exige una sola vez, para todo el documento, y el login se exceptúa.** Es el mismo criterio que las reglas de acceso, que cierran todo salvo lo que una regla abre ([ADR-0005](0005-authentication.md)): un pedido nuevo queda con candado sin declarar nada, y lo que se declara a propósito es un pedido público. La alternativa era declararlo en cada controlador; se descartó porque suma algo que recordar por entidad, y si se olvida, Swagger UI no envía el token y el pedido responde `401`. Un test recorre los pedidos de la documentación y falla si alguno que no es el login queda sin candado.
  - **Por qué el login tiene que quedar exceptuado, y no solo por prolijidad.** Un pedido público que trae un token rechazado responde `401` (nota del ticket #7 en el [ADR-0005](0005-authentication.md)). Si el login llevara candado, Swagger UI le enviaría el token cargado, y con un token vencido no se podría volver a iniciar sesión desde la página sin quitarlo antes a mano. Exceptuado, el login nunca recibe el token.
  - **La configuración general está en `shared` y solo declara.** Título, descripción, versión, esquema de seguridad y exigencia global son anotaciones sobre una clase sin código, la misma forma en que los controladores documentan sus pedidos. La alternativa era armar el documento con código; hace lo mismo con más líneas.
  - **La versión sale del `pom.xml`.** La configuración de la aplicación tiene un marcador que Maven reemplaza al construir, así que la versión está escrita en un solo lugar. Las alternativas eran repetirla en el código, que puede desviarse, o pedírsela a la información de construcción de Spring Boot, que suma una pieza al `pom.xml` para el mismo resultado. Consecuencia: mientras el `pom.xml` diga `0.1.0-SNAPSHOT`, eso es lo que muestra la documentación.
  - **El login es el primer grupo de Swagger UI.** La página ordena los grupos por nombre y el del login se llama "Authentication". Sin eso el orden era el que le tocaba a la lista de grupos, y el login quedaba último, cuando es por donde hay que empezar. La alternativa era un orden escrito a mano (el login y después las entidades en el orden de la consigna); pide código que reordene la lista después de generada, para una diferencia chica.
  - **El cuerpo del login trae de ejemplo al usuario de prueba,** con su contraseña. Así "Try it out" inicia sesión sin escribir nada, que es lo que hace quien corrige. En contra: una contraseña a la vista en el código y en la documentación. Se aceptó porque es una credencial de prueba que el README ya publica y que solo existe donde la carga de usuarios de prueba está encendida; la descripción del pedido y la de cada campo lo dicen. Si la aplicación se publicara con usuarios reales, el ejemplo se quita en una línea.
  - **El login declara que responde JSON,** igual que los listados y los detalles; sin eso la documentación mostraba su respuesta con cualquier tipo de contenido. Es el único cambio de comportamiento del ticket: un login que pide una respuesta que no sea JSON (cabecera `Accept`) responde `406` sin comprobar las credenciales. Antes las comprobaba primero y respondía `406` después, al no poder escribir la respuesta. Ahora hace lo mismo que los listados, con el mismo texto.
  - **Qué se completó al repasar los nueve pedidos.** Los ocho de datos ya documentaban sus parámetros, su respuesta y sus errores. Al login le faltaba casi todo: resumen, descripción, su `400` y su `401`. Se sumaron además las descripciones de los campos de la página y de sus elementos, que los detalles ya tenían. Los errores inesperados (`500`) no se documentan pedido por pedido: lo documentado es lo que un cliente puede provocar.
