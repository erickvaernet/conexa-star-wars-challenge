# ADR-0011: Caché de las respuestas de SWAPI

| Campo      | Valor                                                        |
|------------|--------------------------------------------------------------|
| Estado     | Aceptado                                                     |
| Fecha      | 14/11/2026                                                   |
| Requisitos | REQ-32, REQ-16                                               |
| Origen     | [RFC-001](../rfc/RFC-001-architecture.md), decisión D-15     |

## Contexto

En el PRD original, "Respuestas rápidas aunque SWAPI sea lenta" (REQ-32) era un extra del ciclo 3, marcado como "Podría".

Al preparar el RFC se releyó la documentación de SWAPI y se midieron sus tiempos el 14/11/2026:

- **Demora progresiva.** Dentro de una ventana de 15 minutos, cada pedido tarda 0,1 s más que el anterior a partir del quinto.

  | Pedido  | Tiempo de respuesta |
  |---------|---------------------|
  | 1 a 3   | 0,3 s               |
  | 4       | 0,9 s               |
  | 10      | 1,5 s               |

  A ese ritmo, el pedido número 50 tarda unos 5 segundos.
- **Tope.** 100 pedidos por ventana de 15 minutos, según las cabeceras de la respuesta, y 10.000 por día según la documentación.

Sin guardar las respuestas, quien pruebe la aplicación un rato desde Swagger la vería cada vez más lenta y podría llegar al corte. Eso afecta justo a quien corrige. Por eso **REQ-32 pasó de "Podría" (ciclo 3) a "Debe" (ciclo 1)**; el cambio está registrado en el historial del [PRD](../requirements/PRD.md#historial-de-cambios).

## Decisión

Las respuestas de SWAPI se guardan en una **caché en memoria con vencimiento**, desde el MVP.

- El vencimiento y el tamaño máximo son configurables. El vencimiento por defecto es de una hora.
- Se aplica en el único punto de entrada a SWAPI, así que cubre los listados, las búsquedas y el detalle.

## Alternativas consideradas

| Alternativa                          | A favor                                                         | En contra                                                             |
|--------------------------------------|-----------------------------------------------------------------|-----------------------------------------------------------------------|
| Sin caché                            | Nada que mantener                                               | Respuestas cada vez más lentas y riesgo de llegar al corte de SWAPI    |
| **Caché en memoria con vencimiento** | Los datos de Star Wars casi no cambian; Spring lo resuelve con poco código | Cada instancia tiene su propia copia                        |
| Caché compartida (Redis)             | Sirve con varias instancias                                     | Un servicio más para levantar; no hace falta con una sola instancia    |

## Consecuencias

- Un pedido repetido no llega a SWAPI: responde rápido y no consume el cupo.
- La paginación en memoria se apoya en la caché: recorrer las páginas de Films o de una búsqueda genera un solo pedido a SWAPI (ver [ADR-0004](0004-pagination-and-filters.md)).
- Un dato que cambie en SWAPI puede tardar hasta una hora en verse. Es aceptable porque los datos de Star Wars casi no cambian.
- La caché vive en la memoria de la aplicación: se vacía al reiniciar y cada instancia tiene la suya. Con más de una instancia haría falta una caché compartida, como Redis.
- El primer pedido de cada dato sigue dependiendo de la velocidad de SWAPI.

## Notas posteriores

Aclaraciones agregadas después de aceptar el ADR. No cambian la decisión.

- **14/11/2026.** Al revisar la especificación técnica se precisó qué se guarda: **solo las respuestas correctas de SWAPI**. Un "no existe" (su 404) o un error no se guardan.
  - Se evaluó guardar también el "no existe", para no gastar pedidos a SWAPI en identificadores inexistentes. Se descartó: casi nadie vuelve a pedir el mismo identificador inexistente, así que el ahorro es mínimo, y a cambio la caché se llenaría de entradas que nadie vuelve a leer y que desplazan datos útiles.
  - Un error nunca se guarda: si se guardara, una caída breve de SWAPI se seguiría viendo hasta que venza la entrada.
  - Consecuencia: repetir el pedido de un identificador inexistente vuelve a llamar a SWAPI. La protección contra el abuso es el límite de pedidos por usuario (REQ-31), previsto para el ciclo 3.
  - Una búsqueda sin resultados sí se guarda, porque SWAPI la responde como correcta, con una lista vacía.
