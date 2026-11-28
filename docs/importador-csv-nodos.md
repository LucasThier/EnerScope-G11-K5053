# Guía del importador CSV de nodos

Esta guía describe el archivo que recibe el importador de nodos de una versión:
`POST /api/v1/version/{versionId}/nodes/import`. El endpoint requiere
autenticación y permiso para editar la versión. El archivo se envía como
`multipart/form-data` en el campo `file`.

## Formato general

- El archivo debe ser CSV en UTF-8, separado por comas. Se aceptan finales de
  línea LF o CRLF y se omiten las líneas completamente vacías.
- La primera línea contiene encabezados únicos, no vacíos y escritos con los
  nombres de propiedades indicados en esta guía. Los encabezados distinguen
  mayúsculas y minúsculas. Se admite BOM UTF-8 al comienzo del primer encabezado.
- Cada fila debe tener la misma cantidad de campos que el encabezado. Los
  espacios al principio y al final de cada campo se eliminan.
- Un campo que contenga coma, comillas o saltos de línea debe ir entre comillas
  dobles. Dentro de un campo entrecomillado, cada comilla literal se escribe
  duplicada (`""`). Esto también aplica a las celdas que contienen JSON.
- Las comillas mal formadas, los encabezados desconocidos o duplicados y los
  campos no admitidos para el tipo de nodo de una fila hacen fallar el archivo.
- El archivo puede pesar hasta 10 MB y contener hasta 10.000 filas de datos
  (incluidas las filas `CONNECTION` y una eventual fila `SCENARIO`). Debe haber
  encabezado y al menos una fila `NODE`.
- El número de registro informado en un error cuenta el encabezado como
  registro 1; la primera fila de datos es el registro 2.

## Encabezados y campos

`nodeKind` es obligatorio y selecciona el tipo de nodo de cada fila. Los demás
encabezados son nombres de propiedades del DTO. Se pueden usar encabezados de
varios tipos de nodo en un mismo archivo, pero cada fila debe dejar vacías las
columnas que no correspondan a su `nodeKind`.

Estos campos son obligatorios en todas las filas `NODE`:

| Encabezado | Contenido |
| --- | --- |
| `nodeKind` | Tipo de nodo, de la lista siguiente. |
| `name` | Nombre no vacío. |
| `state` | Estado: `RUNNING`, `PROPOSED`, `PENDING` o `REMOVED`. |
| `lifespanInMonths` | Número entero. |
| `upkeepCosts` | Número finito. |
| `maintenanceIntervalInDays` | Número entero. |
| `operatingCosts` | Número finito. |
| `wastePercentage` | Número finito. |
| `type` | Objeto JSON con `vertical`, `role` y `nodeType`; ver compatibilidades abajo. |
| `investmentCost` | Objeto JSON que incluya `components` como arreglo (puede estar vacío). |

Campos comunes opcionales: `id` (referencia externa para conexiones y referencias
económicas),
`startupDate` (fecha/hora ISO-8601), `maintenanceDuration` (entero),
`graphData` (objeto JSON) e `identity` (UUID). No hace falta incluirlos si no se
utilizan. `id` solo sirve para identificar filas del archivo: el backend genera
un ID nuevo al guardar cada nodo y nunca persiste el valor de esta columna.
Puede ser cualquier texto no vacío, pero debe ser único entre los nodos que lo
incluyan.

### `identity` repetida o ya existente

El importador solo **agrega** nodos nuevos; no actualiza los existentes. Por eso
una fila `NODE` con `identity` se rechaza, antes de guardar nada, en dos casos:

- la `identity` ya existe en la versión destino. Guardarla reemplazaría el nodo
  actual por uno nuevo con otro ID y dejaría colgando las referencias
  (perfiles, activos, conversiones y contratos) de las configuraciones
  económicas de los escenarios;
- la misma `identity` aparece en dos filas del archivo.

El error es un `400` con el número de registro (el encabezado es el 1) y estos
mensajes exactos:

```text
Invalid CSV record 3 (NODE): identity 6f1c2a9e-0b7d-4c1e-9a52-3d8f7e4b1a10 already exists in this version
Invalid CSV record 3 (NODE): identity 6f1c2a9e-0b7d-4c1e-9a52-3d8f7e4b1a10 is repeated in the file
```

La comprobación se hace con el resto de las validaciones de cada fila (después de
las demás reglas de esa fila) y antes de crear nodos, conexiones o economía, así
que el lote sigue siendo atómico: si falla, la versión queda exactamente igual.
El orden de errores no cambia (401, `400` por archivo vacío o de más de 10 MB,
403/404 por permisos, y recién después los errores de contenido). Solo se
compara con la versión destino: la misma `identity` en otra versión (por ejemplo
la versión padre) es válida. Un archivo sin columna `identity`, o con la celda
vacía, se importa como siempre y recibe identidades nuevas.

### `identity` de conexión repetida o ya existente

Las filas `CONNECTION` siguen la misma regla, con su propio espacio de nombres
(se comparan solo con otras conexiones): se rechaza una `identity` que ya tenga
una conexión de la versión destino (incluidas las heredadas por una rama) o que
aparezca en dos filas `CONNECTION`, sin importar qué nodos unan.

```text
Invalid CSV record 6 (CONNECTION): identity 6f1c2a9e-0b7d-4c1e-9a52-3d8f7e4b1a10 is repeated in the file
Invalid CSV record 4 (CONNECTION): identity 6f1c2a9e-0b7d-4c1e-9a52-3d8f7e4b1a10 already exists in this version
```

Es un `400` y no se guarda nada. La pantalla lo traduce a «Línea N: …». Que
una identidad esté repetida entre conexiones todavía no se detecta en el
navegador: lo informa el servidor al importar.

Además, cada `nodeKind` requiere los siguientes encabezados y valores no vacíos:

| `nodeKind` | `type.vertical` | `type.nodeType` | Campos específicos obligatorios |
| --- | --- | --- | --- |
| `WELL` | `EXTRACTION` | `WELL` | `maxCollectionCapacity`, `declineCurve`, `gasRichness`, `dtmTime`, `dtmCost`, `surface` |
| `GATHERING_NETWORK` | `EXTRACTION` | `GATHERING_NETWORK` | `maxTransportCapacity`, `length`, `lossPerMeter`, `connectedWells` |
| `TREATMENT_PLANT` | `EXTRACTION` | `TREATMENT_PLANT` | `maxTreatmentCapacity`, `contaminantWaste`, `intermediateStorage`, `treatmentCost` |
| `PIPELINE` | `TRANSPORTATION` | `PIPELINE` | `maxFlowCapacity`, `length`, `lossPerKm` |
| `PIPELINE_CONNECTION` | `TRANSPORTATION` | `PIPELINE_CONECTION` | `transferCapacity`, `outputPriority` |
| `COMPRESSING_PLANT` | `TRANSPORTATION` | `COMPRESSING_PLANT` | `maxCompressionCapacity`, `processWaste`, `gasConsumption` |
| `GROUND_BASED_LIQUEFACTION_PLANT` | `LIQUEFACTION` | `GROUND_LIQUEFACTION_PLANT` | `maxProcessingCapacity`, `mtpaRatio`, `intermediateStorage`, `gasConsumption` |
| `FLNG_UNIT` | `LIQUEFACTION` | `FLNG_UNIT` | `maxProcessingCapacity`, `mtpaRatio`, `intermediateStorage`, `vesselDepth`, `hiringCost`, `shipCapacity`, `gasConsumption` |
| `LNG_CARRIER` | `EXPORT` | `LNG_CAMER` | `exportFrequency`, `shipCapacity`, `fullLoadTime`, `hiringCost`, `timeToDestination` |
| `SEAPORT_TERMINAL` | `EXPORT` | `SEAPORT_TERMINAL` | `intermediateStorage`, `portDepth`, `shipCapacity` |
| `INTERNAL_CONSUMPTION` | `INTERNAL_CONSUMPTION` | `INTERNAL_CONSUMPTION` | `consumptionSummer`, `consumptionAutumn`, `consumptionWinter`, `consumptionSpring` |
| `INDUSTRIAL_CONSUMPTION` | `INTERNAL_CONSUMPTION` | `INDUSTRIAL_CONSUMPTION` | `consumption` |

Los valores de `type.vertical` y `type.nodeType` deben coincidir con la fila.
Los nombres `PIPELINE_CONECTION`, `GROUND_LIQUEFACTION_PLANT` y `LNG_CAMER`
son los valores exactos que usa actualmente el enum del backend, aunque no
coincidan con la ortografía de `nodeKind`. `type.role` debe ser `GENERATOR`,
`INTERMEDIATE` o `SINK`.

### El rol y la vertical se deducen del tipo de nodo

El rol estructural y la vertical no se eligen: los fija el tipo de nodo
(`NodeTypeEnum` del backend), y el archivo tiene que repetirlos tal cual en la
columna `type`. Un valor distinto se rechaza.

| Rol | Tipos de nodo (`nodeKind`) |
| --- | --- |
| `GENERATOR` | `WELL` |
| `INTERMEDIATE` | `GATHERING_NETWORK`, `TREATMENT_PLANT`, `PIPELINE`, `PIPELINE_CONNECTION`, `COMPRESSING_PLANT`, `GROUND_BASED_LIQUEFACTION_PLANT`, `FLNG_UNIT` |
| `SINK` | `SEAPORT_TERMINAL`, `LNG_CARRIER`, `INDUSTRIAL_CONSUMPTION`, `INTERNAL_CONSUMPTION` |

Por ejemplo, un `SEAPORT_TERMINAL` es siempre `SINK` de la vertical `EXPORT`:
`{"vertical":"EXPORT","role":"SINK","nodeType":"SEAPORT_TERMINAL"}`. Las
plantillas descargables traen el valor correcto para los tipos que muestran.

La pantalla lo comprueba en el navegador antes de enviar y lista **todos** los
problemas del archivo juntos (los primeros 50 y cuántos faltan), con su línea y
el valor esperado, por ejemplo: «Línea 9: SEAPORT_TERMINAL debe tener el rol
SINK (el archivo dice INTERMEDIATE)». También marca una celda `type` vacía, que
no sea un JSON válido o que no sea un objeto, o a la que le falte `vertical`,
`role` o `nodeType`. Con problemas, el botón *Importar* queda deshabilitado. Si
aun así el backend lo rechaza, se muestra el mismo mensaje traducido.

La vertical exigida para cada `nodeKind` (tabla de arriba) sale del mismo
`NodeTypeEnum` que se aplica al guardar el nodo, así que la verificación previa
del importador y el guardado no pueden contradecirse. `INDUSTRIAL_CONSUMPTION`
va en `INTERNAL_CONSUMPTION` (hasta el 2026-11-22 el importador exigía `EXPORT`
y el guardado `INTERNAL_CONSUMPTION`, por lo que ese tipo no se podía importar).
Para importar ese tipo hace falta permiso de edición en la vertical *Consumo
interno*.

Los campos numéricos deben ser números finitos. Los campos enteros deben
contener un entero dentro del rango de su tipo. Para los nodos, el importador
valida que estén informados y que tengan el formato/tipo correcto; las reglas
de negocio adicionales se aplican al guardar el nodo.

## Celdas JSON

Los encabezados `type`, `investmentCost`, `graphData` y `declineCurve` reciben
un objeto JSON dentro de una celda CSV. No se deben repartir las propiedades
del objeto en columnas separadas.

- `type`: solo admite `vertical`, `role` y `nodeType`.
- `investmentCost`: debe incluir `components`. Cada elemento debe tener
  `name` (texto no vacío), `amount` (número finito) y `costBasis`. Los valores
  admitidos para `costBasis` son `FLAT`, `PER_M`, `PER_KM`, `PER_KM2`,
  `PER_CONECTIONS_TOTAL`, `PER_CONECTIONS_INPUT` y `PER_CONECTIONES_OUTPUT`.
- `declineCurve` (obligatorio para `WELL`): admite `type` igual a `CONSTANT`,
  `UNIFORM` o `NORMAL`, y `val_a` numérico finito. `UNIFORM` requiere también
  `val_b` finito y `val_a < val_b`; `NORMAL` requiere `val_b` finito y mayor
  que cero. `CONSTANT` no requiere `val_b`.
- `graphData` (opcional): puede contener `graphPosition` con números `x` e `y`,
  `geographicalPosition` con `longitude` y `latitude`, o ambos. La longitud
  debe estar entre -180 y 180 y la latitud entre -90 y 90.

No agregues propiedades desconocidas a estos objetos JSON. Para serializar JSON
en CSV, encierra la celda entre comillas y duplica las comillas del JSON. Por
ejemplo, el JSON `{"vertical":"EXTRACTION"}` se escribe como
`"{""vertical"":""EXTRACTION""}"`.

## Ejemplo mínimo: un pozo

Este ejemplo importa un nodo sin configuración económica:

```csv
nodeKind,name,state,lifespanInMonths,upkeepCosts,maintenanceIntervalInDays,operatingCosts,wastePercentage,type,investmentCost,maxCollectionCapacity,declineCurve,gasRichness,dtmTime,dtmCost,surface
WELL,Pozo Norte,RUNNING,240,1000,30,5000,0.02,"{""vertical"":""EXTRACTION"",""role"":""GENERATOR"",""nodeType"":""WELL""}","{""components"":[{""name"":""Perforacion"",""amount"":250000,""costBasis"":""FLAT""}]}",1000000,"{""type"":""CONSTANT"",""val_a"":0.25}",0.85,0,500,1200
```

Los valores numéricos del ejemplo son ilustrativos: deben respetar las unidades
y magnitudes que use el proyecto. El archivo puede incluir varias filas `NODE`
de distintos tipos; para eso, agrega al encabezado las columnas específicas que
necesites y deja vacías las columnas ajenas a cada fila.

## Conexiones entre nodos

Para importar aristas del diagrama, agrega `recordType`, `id`, `fromNodeId` y
`toNodeId` al encabezado. Cada nodo que participe en una conexión debe tener un
`id` único. Agrega una fila por arista con `recordType=CONNECTION`; sus campos
`fromNodeId` y `toNodeId` deben contener los `id` externos de los nodos, no UUID
de la base de datos. El importador crea primero los nodos, resuelve esas
referencias a los IDs que generó el backend y después guarda las conexiones.
Las filas `CONNECTION` deben dejar vacías las columnas de nodo y economía;
`identity` puede incluirse opcionalmente como UUID de identidad estable de la
conexión. `nodeKind` también queda vacío en estas filas.

Las referencias deben apuntar a nodos incluidos en el mismo archivo. Los IDs
externos duplicados o las referencias inexistentes rechazan el archivo completo
antes de guardar. La respuesta del endpoint sigue contando solo los nodos, no
las conexiones.

Se requiere `recordType` para incluir conexiones. Si se omite, el archivo sigue
interpretándose como una importación de nodos sin conexiones.

## Importación económica opcional

Para incluir economía agrega `recordType`, `economicProfile` y
`economicScenario`; puedes usar esas filas junto con `CONNECTION`:

| Encabezado | Uso |
| --- | --- |
| `recordType` | Tipo de fila: `NODE`, `CONNECTION` o `SCENARIO`. |
| `economicProfile` | Objeto JSON opcional por nodo con `ownership` y `rules`. |
| `economicScenario` | Objeto JSON de escenario, obligatorio en la única fila `SCENARIO`. |

Si existe el encabezado `recordType`, todas las filas deben indicar `NODE`,
`CONNECTION` o `SCENARIO`. Puede haber como máximo una fila `SCENARIO` y debe
haber al menos una fila `NODE`. En una fila `NODE`, `economicScenario`,
`fromNodeId` y `toNodeId` deben quedar vacíos. En una fila `CONNECTION`, solo
se permiten `recordType`, `fromNodeId`, `toNodeId` y, opcionalmente, `identity`;
en la fila `SCENARIO`, todos los campos salvo `recordType` y `economicScenario`
deben quedar vacíos. Si alguna fila incluye `economicProfile`, también se exige
una fila `SCENARIO`. Si se omite `recordType`, todas las filas se interpretan
como `NODE` y no se pueden importar conexiones ni una fila `SCENARIO`. Los
valores de `nodeKind` y `recordType` se convierten a mayúsculas al procesarlos;
se recomienda escribirlos en mayúsculas como en esta guía.

Ejemplo de celda `economicProfile`:

```json
{"ownership":[{"taxEntityId":"entity-1","share":1}],"rules":[]}
```

`ownership` debe tener al menos un titular existente en el escenario; cada
participación debe ser positiva y la suma debe ser exactamente `1`. `rules`
puede ser un arreglo vacío; si incluye reglas, se validan como `EconomicRule`
(identificadores, entidad titular, moneda, fechas, ocurrencias y, para drivers
variables, su conversión correspondiente). El importador asocia el perfil al
ID del nodo que crea: no incluyas `nodeId` en este objeto.

`economicScenario` acepta las propiedades `startYear`, `years`, `currency`,
`wacc`, `taxEntities`, `boundary`, `adjustments`, `contracts`, `assets`,
`taxTreatments` y `conversions`. No incluyas `nodeProfiles`: se generan a partir
de los perfiles de las filas `NODE`. Las colecciones deben estar presentes
(aunque estén vacías); `taxEntities` y `boundary` deben contener al menos una
entidad válida. Entre otras validaciones, el escenario exige horizonte de 1 a
100 años, año inicial entre 1900 y 9000, moneda ISO de tres letras en
mayúsculas y WACC no negativo. Las referencias entre entidades, perfiles,
activos, contratos, reglas y conversiones también deben ser consistentes. Las
referencias a nodos de `conversions[].nodeId`, `contracts[].deliveryNodeId` y
`assets[].nodeId` usan el `id` externo de una fila `NODE`, no un UUID persistido.
Incluye un `id` único en cada nodo referenciado; el importador lo reemplaza por
el UUID que genera al crear el nodo y rechaza referencias ausentes o
desconocidas antes de guardar el lote.

En el CSV, el objeto `economicScenario` se codifica como una celda JSON entre
comillas, escapando sus comillas dobles igual que en los ejemplos anteriores.
Los campos económicos se validan y guardan junto con los nodos, en una única
transacción. El importador no convierte `upkeepCosts`, `operatingCosts` ni
`investmentCost` en reglas económicas automáticamente. Para las propiedades
internas completas de las reglas y demás componentes económicos, consulta el
[módulo económico](economic-module/economic-module.md).

## Errores y resultado

Si una fila es inválida, el archivo completo se rechaza; no se importa un lote
parcial. También se rechaza el archivo completo si falla la validación del
escenario económico. El endpoint responde con el envoltorio `ApiResponse`; los
errores de formato o datos son `400` y los archivos que superen el límite de
carga del servidor pueden responder `413`. El valor devuelto cuenta únicamente
los nodos importados, no la fila `SCENARIO`.

Este importador crea nodos (incluido `PIPELINE_CONNECTION`) y aristas del
diagrama (`NodeConnection`) a partir de filas `CONNECTION`.

## Cómo se muestran los errores en la pantalla

La pantalla *Versionado → Importar CSV* traduce el mensaje del backend (en
inglés, uno por vez) a «Línea N: …», donde N es el número de registro de esta
guía (el encabezado es 1). Se reconocen por expresión regular, entre otros:

| Mensaje del backend | Se muestra |
| --- | --- |
| `Invalid CSV record N (NODE): identity <uuid> already exists in this version` | Línea N: la identidad `<uuid>` ya existe en esta versión. Este importador solo agrega nodos nuevos: quitá esa fila o dejá la columna identity vacía. |
| `Invalid CSV record N (NODE): identity <uuid> is repeated in the file` | Línea N: la identidad `<uuid>` aparece repetida en el archivo. Cada nodo debe tener una identidad única. |
| `Invalid CSV record N (TIPO): Node role does not match its type: TIPO is SINK, not INTERMEDIATE` | Línea N: TIPO debe tener el rol SINK (el archivo dice INTERMEDIATE) |
| `Invalid CSV record N (TIPO): Node vertical does not match its type: TIPO belongs to EXPORT, not LIQUEFACTION` | Línea N: TIPO debe tener la vertical EXPORT (el archivo dice LIQUEFACTION) |

El resto de los mensajes con número de registro (columnas faltantes, JSON
inválido, referencias desconocidas, tipos incoherentes, etc.) y los de archivo
(UTF-8, tamaño, encabezado, filas de más) tienen su traducción en
`frontend/src/api/errors.ts`. Un mensaje con número de registro que no se
reconoce se muestra como «Línea N: la fila no es válida.» con el texto original
como detalle; uno sin número, como «El importador rechazó el archivo.» con el
original como detalle. Antes de enviar, la pantalla ya detecta en el cliente
una `identity` repetida en el archivo o que no es un UUID; que una `identity`
ya exista en la versión solo lo sabe el backend.

## Plantillas descargables

Se sube **un solo archivo**: nodos, conexiones y economía van juntos. Las tres
plantillas de la pantalla son ejemplos **alternativos**, cada uno completo por
sí mismo:

| Enlace en la pantalla | Archivo | Contenido |
| --- | --- | --- |
| *Descargar plantilla (nodos y conexiones)* | `plantilla-nodos-y-conexiones.csv` | `TREATMENT_PLANT` y `PIPELINE` con su conexión, sin economía. No requiere permiso de economía. |
| *solo nodos* | `plantilla-nodos.csv` | Un `WELL`. |
| *con economía* | `plantilla-nodos-con-economia.csv` | Los mismos dos nodos y la conexión, con un `economicProfile` por nodo y la fila `SCENARIO`. El enlace se deshabilita sin permiso para editar la economía. |

La conexión de los ejemplos es `TREATMENT_PLANT → PIPELINE`, un par que el
editor permite (`ALLOWED_CONNECTIONS` en
`frontend/src/components/editor/nodeCatalog.ts`); un test comprueba que toda
conexión de toda plantilla cumpla `canConnect`. Esa regla vive solo en el
frontend: el importador **no** valida los pares de tipos. El ejemplo es un tramo
de la cadena, sin pozo que lo alimente.

Ninguna plantilla incluye la columna `identity`: es opcional y, si viene, no
puede existir ya en la versión ni repetirse en el archivo.
