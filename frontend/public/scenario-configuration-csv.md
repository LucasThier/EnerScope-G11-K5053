# Configuración CSV de escenarios — formato 1

## Flujo

Descargá la plantilla o exportá el formulario actual, editá el CSV, importalo, vinculá los nodos y pulsá Validar CSV. Revisá la vista previa y elegí Cargar en el formulario. **Solo Guardar publica los cambios**. Cancelar no modifica el formulario; no se inicia una simulación.

La importación reemplaza Economía y Operación completas. Las inversiones y reglas que no estén en el archivo se quitan de la configuración actual. Las evaluaciones anteriores se conservan. No crea/elimina nodos ni modifica conexiones. Las reglas avanzadas requieren crear primero una copia simple con la opción existente.

## Archivo

Máximo 2 MB. CSV UTF-8, separador punto y coma, decimales con coma sin miles (`1234,56`). En Excel, importá columnas como texto para conservar fechas y claves; guardá como CSV UTF-8 con punto y coma. Encabezados exactos:

```csv
Sección;Elemento;Nodo;Campo;Valor
```

Cada fila corresponde a un parámetro. Los nombres técnicos de Campo deben conservarse. Secciones: Metadatos, Colecciones, Nodos, General, Impuestos, Inversion, Ingresos, Costos y Operacion (sin tildes salvo el encabezado Sección).

- Elemento: clave única de cada inversión o regla dentro de su sección (ejemplo activo-1); repetida en sus filas. Vacía en las otras secciones.
- Nodo: clave del nodo de origen incluida por la plantilla. Vacío en General, Impuestos, Metadatos, Colecciones y reglas fijas; opcional en inversiones.
- Las celdas con separadores, comillas o saltos de línea van entre comillas dobles; duplicá una comilla dentro del texto. El exportador lo hace automáticamente.
- El exportador protege textos que comienzan con =, +, - o @ agregando un apóstrofo. El importador revierte esa protección; los apóstrofos iniciales originales se duplican al exportar.

## Metadatos y colecciones

Metadatos contiene una fila con Campo `version` y Valor `1`.
Colecciones contiene cuatro filas: campos `Inversion`, `Ingresos`, `Costos` y `Nodos`; sus valores son las cantidades de elementos de cada sección. Una colección vacía se declara con `0`.
Nodos contiene tres filas por clave en la columna Nodo: `identity`, `type` y `name`. Conservar las de la plantilla. El nombre operativo editable está en Operacion / name.

## Campos económicos

| Sección | Campos obligatorios | Significado |
| --- | --- | --- |
| General | startYear, years, currency, wacc | Año de inversión, años operativos, moneda ISO y WACC anual (%) |
| Impuestos | entityName, taxRate | Nombre de entidad fiscal y tasa (%) |
| Inversion | concept, cost, residual, purchase, service, life | Concepto, costo, residual, fecha compra, fecha puesta en servicio, vida útil en meses |
| Ingresos / Costos | concept, driver, value, unit, factor, from, to | Concepto, base de cálculo, importe/tarifa, unidad comercial, factor, primer y último año de aplicación |

Todos los campos requieren una fila, aunque su valor sea opcional. Las fechas económicas usan AAAA-MM-DD; wacc=10 significa 10 %. Los años de reglas deben pertenecer al horizonte operativo, posterior al año de inversión. Conceptos únicos.

Bases (`driver`): FIXED (importe fijo anual; Nodo, unit y factor vacíos), OUTPUT_VOLUME (requiere nodo, unidad comercial y factor positivo), EXPORTED_VOLUME (solo ingresos y buques LNG_CAMER, con nodo, unidad y factor).

Para agregar un activo o regla, copiá todas las filas de un elemento, elegí otra clave y aumentá el contador correspondiente. Para eliminarlo, quitá todas sus filas y reducí el contador. Ejemplo parcial para integrar a un archivo completo:

```csv
Ingresos;venta-fija;;concept;Venta anual
Ingresos;venta-fija;;driver;FIXED
Ingresos;venta-fija;;value;125000,50
Ingresos;venta-fija;;unit;
Ingresos;venta-fija;;factor;
Ingresos;venta-fija;;from;2031
Ingresos;venta-fija;;to;2035
```

## Operación

La plantilla incluye todos los campos del tipo de nodo: no se omiten filas. Las unidades y restricciones son las del formulario operativo; no se convierten magnitudes físicas.

- name: nombre no vacío; state: PROPOSED, PENDING o RUNNING. No se elimina un nodo por CSV.
- startupDate: ISO UTC, por ejemplo 2031-01-01T00:00:00Z; admite vacío.
- lifespanInMonths: meses; maintenanceIntervalInDays: días; maintenanceDuration: horas; wastePercentage: porcentaje.
- Internal Consumption: consumptionSummer, consumptionAutumn, consumptionWinter, consumptionSpring.
- Industrial Consumption: consumption constante.
- Distribuciones: sufijos `.type`, `.a`, `.b`. CONSTANT requiere a y no lleva fila b; NORMAL requiere media a y desviación b positiva; UNIFORM requiere mínimo a y máximo b estrictamente mayor.
- Ejemplo: declineCurve.type=NORMAL; declineCurve.a=1,45; declineCurve.b=0,1 (cada parámetro en su propia fila).

## Vinculación, errores y conservación

La asociación automática usa identidad y tipo. Entre proyectos, elegí manualmente las equivalencias. Cada nodo del archivo requiere un nodo del mismo tipo y todos los nodos vigentes del destino deben estar cubiertos una sola vez. El nombre sirve de ayuda, no identifica por sí solo. No se transfieren geometría, conexiones ni identificadores persistidos.

Los errores indican fila/campo o las filas de la sección afectada. Corregí el CSV y volvé a importarlo: no se aplican archivos parciales o inválidos. Si cambió el escenario en otra pantalla, recargá y validá nuevamente. Guardar vuelve a controlar permisos y concurrencia.

Exportar refleja el formulario, incluso cambios sin guardar o valores pendientes. La plantilla contiene valores operativos actuales y campos económicos a completar. Ambos pueden requerir edición antes de pasar la validación de importación.
