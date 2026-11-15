# ADR-0010: Entorno local

| Campo      | Valor                                                        |
|------------|--------------------------------------------------------------|
| Estado     | Aceptado                                                     |
| Fecha      | 14/11/2026                                                   |
| Requisitos | REQ-11, REQ-13, REQ-19                                       |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), decisión D-12     |

## Contexto

Quien corrige tiene que poder levantar el proyecto con un solo comando y sin configurar nada a mano. El equipo técnico de Conexa describió la combinación de base de datos, proyecto dockerizado y carga inicial como "el combo perfecto".

La consigna exige Java 21, y en la máquina de desarrollo hay un JDK 25.

## Decisión

- **`docker compose up`** levanta la aplicación y PostgreSQL, aplica los cambios de esquema y carga los usuarios de prueba.
- La imagen se construye en **dos etapas**: una compila con JDK 21 y la otra ejecuta con un JRE 21 liviano.
- La **configuración sensible** (clave de firma del token y credenciales de la base) viene de variables de entorno. El repositorio incluye un `.env.example` con valores de desarrollo.
- Se expone un **control de estado** (`/actuator/health`) que Docker usa para saber cuándo la aplicación está lista. El resto de Actuator queda cerrado.
- En la máquina de desarrollo se usa el **JDK 25, compilando con nivel de lenguaje 21**.

## Alternativas consideradas

| Alternativa                          | A favor                                              | En contra                                                    |
|--------------------------------------|------------------------------------------------------|--------------------------------------------------------------|
| **JDK 25 compilando a nivel 21**     | No hay que instalar nada; el compilador rechaza lo posterior a Java 21 | Los tests locales corren sobre el JDK 25   |
| Instalar un JDK 21                   | Desarrollo, tests y entrega sobre la misma versión   | Exige instalar y configurar otro JDK                          |

No se evaluaron alternativas a Docker: el equipo técnico lo mencionó de forma explícita.

## Consecuencias

- Quien corrige no necesita tener Java ni PostgreSQL instalados: alcanza con Docker.
- Lo que se entrega corre siempre sobre Java 21, porque la imagen se compila y se ejecuta con esa versión.
- Si apareciera una diferencia de comportamiento entre el JDK 25 local y el 21 de la imagen, se instala un JDK 21 y este ADR se reemplaza.
- Ningún secreto queda en el repositorio. Los valores de `.env.example` son solo para desarrollo.
- La misma imagen sirve para el despliegue público del ciclo 2 (REQ-20).

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **15/11/2026.** Los tests ya corren sobre un JDK 21. Desde el ticket #33, la verificación automática ([ADR-0014](0014-continuous-verification.md)) ejecuta `./mvnw verify` con un JDK 21 en cada pull request y en cada cambio de `main`.
  - En la primera ejecución pasaron los 793 tests, sin tocar ninguno: no apareció ninguna diferencia de comportamiento entre el JDK de desarrollo y el 21.
  - La consecuencia que este ADR dejaba abierta queda cubierta de otra forma: una diferencia así ya no pasaría inadvertida, porque haría fallar la verificación del pull request. No hizo falta instalar un JDK 21 en la máquina de desarrollo, donde se sigue compilando con nivel de lenguaje 21 sobre un JDK más nuevo.
  - La misma verificación ejecuta además `docker compose up --build --wait`, que hasta este ticket se comprobaba a mano.
- **15/11/2026.** La imagen sirve para el despliegue público tal como está (ticket #34, [ADR-0015](0015-public-deployment.md)), que era la última consecuencia que este ADR dejaba anotada.
  - **No cambiaron el `Dockerfile` ni el código.** Lo que el servicio necesita distinto entra por variables de entorno, escritas en `render.yaml`. Tres de ellas no existen en el entorno local porque ahí no hacen falta: la que indica el puerto al servicio, la que hace leer las cabeceras del intermediario que atiende HTTPS, y los ajustes de memoria y de compilación de Java para una instancia chica.
  - **`docker compose` no cambia.** No suma variables y sigue levantando sin configurar nada.
  - **Se probó la misma imagen con los límites del plan gratuito,** 512 MB y una décima de procesador: arranca en un minuto y medio, contra los 10 segundos del entorno local, y usa unos 260 MB. El detalle está en las notas del ADR-0015.
  - **El control de estado que usa `docker compose` no alcanzaría con esos límites:** espera hasta 80 segundos. No se tocó, porque en el entorno local la aplicación corre sin límites; el servicio público usa su propio control, sobre la misma dirección.
