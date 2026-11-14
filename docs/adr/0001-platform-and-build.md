# ADR-0001: Plataforma y herramienta de construcción

| Campo      | Valor                                                              |
|------------|--------------------------------------------------------------------|
| Estado     | Aceptado                                                           |
| Fecha      | 14/11/2026                                                         |
| Requisitos | Restricción "Java 21" del PRD, REQ-13, REQ-19                      |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), decisiones D-01 y D-02  |

## Contexto

La consigna exige Java 21. La aplicación necesita servir una API, protegerla con login, guardar usuarios en una base de datos y llamar a un servicio externo. Quien la corrige es un equipo de backend Java.

Al 14/11/2026, la última versión estable de Spring Boot es la 4.1.2. La línea 4 es la vigente desde noviembre de 2025. En la máquina de desarrollo hay un JDK 25 y no hay Maven ni Gradle instalados.

## Decisión

Se usa **Spring Boot 4.1 sobre Java 21**, con **Maven** y su *wrapper* (`mvnw`).

El proyecto se compila con nivel de lenguaje 21 (`maven.compiler.release=21`), de modo que el compilador rechaza cualquier característica posterior aunque el JDK instalado sea más nuevo.

## Alternativas consideradas

| Alternativa            | A favor                                                        | En contra                                                           |
|------------------------|----------------------------------------------------------------|---------------------------------------------------------------------|
| **Spring Boot 4.1**    | Línea vigente; estándar en backend Java; trae integrado todo lo que hace falta | Las librerías de terceros para la línea 4 son recientes |
| Spring Boot 4.0        | Misma línea, con un año más de uso                             | Va una versión por detrás sin una ventaja concreta                   |
| Quarkus o Micronaut    | Arranque más rápido y menor consumo                            | Menos conocidos por quien corrige; la ventaja no pesa acá            |
| **Maven con *wrapper*** | La más extendida en proyectos Spring; no exige instalar nada  | Configuración más larga                                              |
| Gradle                 | Más rápido y más expresivo                                     | Menos uniforme entre proyectos; no aporta en uno chico               |

## Consecuencias

- Cualquier persona con un JDK puede compilar y probar el proyecto con `./mvnw`, sin instalar Maven.
- La compatibilidad de las librerías con Spring Boot 4.1 se comprueba al armar el esqueleto del proyecto. Si alguna falla, se baja a Spring Boot 4.0 y se registra en un ADR nuevo que reemplaza a este.
- En la máquina de desarrollo los tests corren sobre el JDK 25. Lo que se entrega corre sobre Java 21 dentro de Docker (ver [ADR-0010](0010-local-environment.md)).
