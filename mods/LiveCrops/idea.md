# LiveCrops — diseño funcional y técnico

> Los cultivos de Logitanos progresan mientras el servidor avanza, aunque su zona no tenga jugadores. El mod se adapta a chunks cargados y descargados sin mantenerlos vivos, sin jugadores ficticios y sin simular el resto de la granja.
>
> **Requisito principal:** una zona descargada conserva sus datos en disco y no genera trabajo periódico ni retiene su registro de cultivos en la RAM del mod. El trabajo activo depende de los chunks con cultivos registrados que ya estén cargados.

Estado: implementación preparada para Minecraft 26.3; compilación y pruebas de código disponibles. Pruebas dentro del juego pendientes. Revisión: 5 de noviembre de 2026. Las cifras de rendimiento son objetivos de aceptación, no mediciones realizadas.

## 1. Objetivo y experiencia de juego

El jugador planta, se va a explorar y vuelve a encontrar progreso. No necesita activar parcelas ni dejar un personaje conectado. El crecimiento atendido mantiene el comportamiento de Minecraft; el desatendido usa un 40 % con jugadores conectados lejos y un 5 % con el servidor vacío, ambos configurables.

**Regla principal de activación:** LiveCrops compensa únicamente los periodos en los que ningún jugador mantiene activa esa zona para el crecimiento natural. Si hay un jugador suficientemente cerca para que vanilla haga crecer los cultivos, LiveCrops deja ese crecimiento en manos de vanilla. Si el último jugador se aleja o sale del servidor, comienza el periodo compensable.

La ausencia se evalúa por zona: un jugador conectado en otra parte del mundo no mantiene atendida esta parcela. Un chunk que permanece en memoria por visión, spawn o un ticket externo tampoco se considera atendido solo por estar cargado; se comprueba si ya recibe crecimiento natural para evitar duplicarlo.

LiveCrops conserva una deuda de crecimiento y la materializa cuando corresponde observar o modificar las plantas. No mantiene una granja simulada en segundo plano. Al volver un jugador puede aplicarse todavía la deuda de su ausencia, pero nunca se añade compensación por el tiempo nuevo que pasa junto a la granja.

Reglas visibles:

- Plantar un cultivo compatible lo registra automáticamente. Replantar empieza una nueva vida del cultivo.
- Acercarse, alejarse, cambiar de dimensión o desconectarse no exige ninguna acción adicional.
- Un mismo tramo de tiempo nunca obtiene crecimiento simultáneo de vanilla y del mod.
- Una planta madura deja de requerir seguimiento, salvo productores de fruta de la ampliación posterior.
- El mod no cosecha, replanta, genera objetos ni activa máquinas de una granja descargada.
- El tiempo con el servidor apagado, pausado o con ticks congelados no cuenta. Dormir o cambiar la hora no equivale a horas de crecimiento.
- Los porcentajes describen tasas esperadas. Una planta concreta puede avanzar más o menos por azar.

La instalación prevista es **solo en el servidor**, con bloques y paquetes vanilla. Se debe verificar con un cliente sin LiveCrops. La documentación del repositorio deberá distinguirlo de TheBall, que sí añade contenido cliente.

## 2. Alcance y entregas

| Entrega | Incluye | Condición de salida |
|---|---|---|
| Prototipo técnico | Elegibilidad real para random ticks, transiciones, persistencia por chunk, perfil de memoria | Adaptación demostrada con chunks cargados y descargados |
| Primera versión | Trigo, zanahoria, patata, remolacha, verruga del Nether, cacao y bayas; límites, comandos y adopción automática de huertos existentes | Matriz funcional y pruebas de carga superadas |
| Ampliación | Tallos y producción acotada de melón/calabaza | Resolver fruta, tallos maduros y bordes entre chunks |
| Opcional | Adaptadores para otros cultivos | Respetar los mismos presupuestos de recursos |

**La adaptación a chunks cargados sin random ticks pertenece a la primera versión.** No se pierde ese crecimiento por estar fuera de la distancia de simulación.

Fuera del alcance inicial: árboles, bambú, caña, cactus, algas, entidades, inventarios, producción acumulativa e historial ambiental. Los cultivos de otros mods necesitan adaptadores explícitos; no basta con encontrar una propiedad `age`.

## 3. Tres estados, un único responsable

La presencia de un jugador y el estado genérico del chunk no bastan para conocer su actividad. La implementación debe consultar o reutilizar **la condición real usada por Minecraft 26.3 para ejecutar random ticks de bloques**.

| Estado | Situación | Responsable | Trabajo del mod |
|---|---|---|---|
| Atendido | Chunk elegible para random ticks | Vanilla | Detectar cambios de régimen; sin simular plantas |
| Desatendido cargado | Chunk residente sin elegibilidad para random ticks | LiveCrops | Acumular intervalos y materializar bajo presupuesto |
| Desatendido descargado | Chunk fuera de memoria | LiveCrops al regresar | Ninguna visita, tarea, lectura o carga periódica de esa zona |

`randomTickSpeed = 0` impide progreso aleatorio en ambos regímenes. No transforma un chunk atendido en una fuente de compensación.

La clasificación debe funcionar con varios jugadores, espectadores según la versión, visión mayor que simulación, tickets externos y chunks forzados. No deducir el comportamiento de `/forceload` o de spawn a partir de versiones anteriores.

### 3.1 Detección eficiente

1. Mantener un conjunto de claves de **chunks ya cargados con seguimiento no vacío**. No indexar permanentemente todas las granjas descubiertas.
2. Usar carga, descarga y cambios de estado para altas, bajas e invalidaciones.
3. Preferir observar el resultado de la comprobación que vanilla ya realiza. El hook no recorre plantas ni asigna objetos.
4. Si ese punto no cubre todos los cambios, comprobar una vez por tick la condición real solo para los chunks registrados y cargados: coste O(L), donde L es su número, nunca O(cultivos) ni O(chunks históricos).
5. Elegir una estrategia completa tras el prototipo; evitar sondeos redundantes si el hook ya cubre el caso.

Fabric documenta `FULL_CHUNK_STATUS_CHANGE`, además de carga y descarga. Es un punto de integración útil, pero debe verificarse si cubre la condición específica de random ticks.

Se sustituye la promesa anterior de «ni una instrucción por tick» por una garantía comprobable: **ningún recorrido periódico de plantas o zonas descargadas, ninguna carga de chunks y ninguna simulación de jugador**. Una comprobación pequeña por chunk registrado cargado es aceptable si resulta necesaria para la corrección.

## 4. Reloj y cambios de ritmo

Usar ticks efectivos mediante `getGameTime()` o el reloj equivalente validado en 26.3. No usar hora del equipo, `dayTime` ni tiempo de ejecución de una tarea.

Para respetar cambios de configuración sin un historial ilimitado, mantener un acumulador persistente por dimensión:

```text
G = intensidad acumulada de crecimiento desatendido
por cada tick efectivo:
    effectiveRate = connectedPlayers > 0 ? onlineRate : emptyServerRate
    G += enabled ? randomTickSpeed * effectiveRate / 4096 : 0
```

Un intervalo desatendido guarda `startG`; al cerrarse tiene intensidad `max(0, endG - startG)`. Los tramos atendidos no se cobran, aunque G avance globalmente. Solo hay un acumulador por dimensión, no un temporizador por parcela.

- Cambios de tasa, gamerule y activación afectan a ticks posteriores.
- Desactivar y reactivar no concede crecimiento por el periodo desactivado.
- No se visitan chunks descargados para actualizar relojes.
- Si el servidor vacío sigue tickeando, G avanza. Si Minecraft pausa el servidor vacío, no avanza; el README debe explicar cómo comprobarlo.
- Usar valores finitos y probar precisión en mundos longevos. Introducir representación entera/fraccionaria solo si la medición lo requiere.

La implementación cuenta eventos efectivos de tick por dimensión con `tickRateManager().runsNormally()`. No compara únicamente el delta local de `getGameTime()`: Nether y End comparten el reloj del overworld y su delta dentro del tick del nivel puede ser cero. Actualizar G en una frontera definida del tick. Las transiciones usan esa misma frontera, sin cobrar un tick dos veces. Un reloj restaurado hacia atrás o una marca futura invalida conservadoramente el intervalo afectado y genera un diagnóstico, nunca una ausencia enorme.

## 5. Persistencia local y memoria

### 5.1 Almacenamiento

Usar **un attachment persistente del chunk**, serializado con sus datos. Fabric ofrece attachments persistentes para chunks; las firmas y garantías se verificarán en las dependencias exactas de 26.3.

No guardar todos los cultivos en un `SavedData` global residente: mantendría en RAM granjas descargadas desde hace semanas. Reservar `SavedData`, si hace falta, para el pequeño reloj y metadatos por dimensión. La ruta física la gestiona Minecraft; no presumir un único archivo para todas las dimensiones.

```text
GrowthClock por dimensión:
  schemaVersion
  accumulatedGrowth
  lastGameTime

CropChunkData persistente, solo con seguimiento:
  schemaVersion
  dormantStartG           // inicio del tramo abierto en el reloj global
  cumulativeDormantC      // intensidad local acumulada, sin tiempo atendido
  // La frontera del lote es transitoria; cada planta persiste consumedC.
  // Al recargar se reinicia el cursor y se omiten deudas ya consumidas.
  cropEntries[]:
    packedPosition       // primitivas, posición local si simplifica el codec
    cropKind             // identificador estable
    // Sustituir o replantar retira la entrada y crea una frontera nueva.
    consumedC            // intensidad local liquidada para esta vida
    admissionRevision    // revisión de tipos habilitados al admitirla
    // No hay historial de intervalos ni metadatos por intento.

LoadedTracker en memoria:
  clave de dimensión + chunk
  régimen observado
  generación de carga y estado de cola
```

Para acumular entradas y salidas sin historial de intervalos, usar un acumulador local C por chunk. Al cerrar un tramo desatendido, sumar `G - dormantStartG` a C; mientras siga abierto, su valor efectivo es `C + G - dormantStartG`. C no avanza por tiempo atendido.

Cada planta guarda `consumedC`, inicializado al C efectivo de su registro. Un lote captura `pendingTargetC`; la intensidad de una entrada es `max(0, pendingTargetC - consumedC)`. Tras liquidarla, avanzar su marca hasta esa frontera, sin retrocederla si nació después. Los tramos nuevos se acumulan en C para otro lote, sin listas de intervalos ni memoria creciente por transiciones.

Así los cambios de régimen son O(1) por chunk y las operaciones por planta quedan en los lotes presupuestados. El lote identifica generaciones; las plantas nuevas no heredan ausencias anteriores. Las marcas C de plantas retiradas desaparecen con ellas.

Evitar `BlockPos` permanentes por planta, mapas por entrada, UUID de propietario sin función asociada y referencias a jugadores. Preferir arrays o colecciones primitivas disponibles en las dependencias.

### 5.2 Liberación

- Al descargar, retirar clave y trabajos de las estructuras activas. No retener chunks desde callbacks, futuros o referencias del attachment.
- Los datos quedan en el archivo de región. Minecraft puede retenerlos durante su descarga y guardado normales; LiveCrops no añade una caché histórica.
- Eliminar el attachment al desaparecer la última planta y terminar el trabajo pendiente.
- No crear attachments vacíos en cada chunk atravesado.
- No construir un índice mundial para mostrar contadores globales exactos. `info` diferencia datos cargados, acumulados de sesión y totales desconocidos.

Ocho bytes por posición solo describen esa primitiva. La RAM real incluye tipo, generación, arrays, capacidad sobrante, colecciones, colas y serialización. Debe medirse con perfilador frente al servidor base.

## 6. Transiciones y materialización

| Transición | Acción |
|---|---|
| Nueva planta | Registrar tras confirmar colocación y estado; nueva generación, sin deuda anterior |
| Atendido → desatendido | Abrir intervalo en G actual; no compensar tiempo atendido |
| Desatendido cargado → descargado | Continuar intervalo, persistir y liberar seguimiento residente |
| Descargado → cargado sin random ticks | Recuperar intervalo y encolar compensación |
| Desatendido → atendido | Cerrar intervalo antes del primer tramo atendido; vanilla se ocupa del posterior |
| Sigue desatendido y cargado | Materializar con baja frecuencia y presupuesto, para actualizar zonas visibles |
| Guardado | Persistir deuda y fronteras sin borrar ausencias pendientes |
| Descarga con lote pendiente | Persistir fronteras consumidas por planta; reanudar al cargar, sin tarea viva |

Un autoguardado no cambia el régimen. **No ejecutar `last = now` sobre todos los registros:** borraría deuda de chunks descargados.

### 6.1 Cola y consistencia

La cola contiene claves y generaciones, no referencias fuertes a chunks. Antes de procesar, verificar que la misma generación de carga sigue residente. Una descarga invalida tareas antiguas.

Cada lote fija `pendingTargetC`, derivado de fronteras G cerradas. Repartirlo entre varios ticks no amplía su intervalo. Si el chunk ya está atendido, los random ticks posteriores pertenecen a vanilla y el lote representa solo deuda anterior.

Por entrada:

1. Confirmar chunk cargado, generación y tipo real de bloque.
2. Leer edad y condiciones sin cargar vecinos.
3. Calcular un número acotado de etapas pendientes.
4. Aplicarlo a la edad actual, limitada al máximo del cultivo.
5. Consumir esa deuda y marcar datos modificados para persistencia.
6. Retirar entradas maduras o inválidas.

Bloque y deuda cambian en el hilo del servidor y deben entrar en una instantánea de guardado coherente. No escribir el mundo desde un hilo auxiliar.

Cosechar antes de terminar un lote descarta la deuda de esa generación; replantar no la hereda. El abono sobre la misma planta puede sumar edad sin superar el máximo. Ningún cambio de bloque debe resucitar el cultivo anterior.

Observar sustituciones por agua, explosiones, pistones, aldeanos y otros mods mediante un hook de mutación confirmado, con salida inmediata en chunks sin attachment. Invalidar la generación anterior aunque el bloque nuevo tenga el mismo identificador. Registrar automáticamente solo colocaciones de jugadores; las escrituras del simulador conservan generación.

### 6.2 Condiciones ambientales y bordes

La primera versión usa las condiciones al materializar: soporte, humedad, vecinos e iluminación. Es una **aproximación del periodo ausente**, no su reconstrucción histórica. Cambiar luces o suelo antes de liquidar deuda puede influir en la estimación; esta limitación debe ser visible en el README.

Una reproducción histórica exigiría capturar y fechar cambios ambientales. No se simulan agua, iluminación o farmland en zonas descargadas para resolverlo.

Si faltan vecinos cargados para evaluar una parcela de borde, aplazar la entrada dentro de límites. Agotado el plazo, descartar ese intervalo para la entrada y contar el motivo; no forzar carga ni inventar condiciones favorables. El cultivo sigue registrado para intervalos futuros.

Tomar la condición lumínica de las fuentes de 26.3. No presuponer que luz bruta y luz visible son equivalentes ni que todos los cultivos comparten umbral. Día y noche deben probarse por adaptador.

## 7. Modelo probabilístico y balance

### 7.1 Aproximación explícita

Con `r` selecciones por sección y tick, cada selección elige entre 4096 posiciones. Un bloque puede ser elegido varias veces por tick: `r / 4096` es el número esperado de selecciones, no la probabilidad exacta de recibir al menos una.

Para condiciones constantes y probabilidad `p` de subir una etapa por selección:

```text
intensidad = max(0, pendingTargetC - consumedC)
lambda = intensidad * p
subidas aproximadas ~ Poisson(lambda)
edadFinal = min(edadActual + subidas, edadMaxima)
```

La Poisson es una aproximación útil con probabilidades pequeñas. No equivale a una binomial exacta ni reproduce la secuencia aleatoria de vanilla. Usar una fuente aleatoria propia para no consumir el generador global del mundo y alterar otras mecánicas.

Multiplicar la tasa de intentos o la probabilidad individual produce el mismo parámetro en este modelo. Se usan `onlineRate` y `emptyServerRate` como fracciones de la tasa por claridad, sin atribuirle una diferencia de varianza inexistente en el modelo.

### 7.2 Muestreo acotado por etapas restantes

No usar un bucle por tick ausente ni atribuir a Knuth un máximo de iteraciones que no tiene. Para `k` etapas restantes, calcular las probabilidades de `X = 0 ... k-1` y asignar la cola a `X >= k`:

```text
P(0) = exp(-lambda)
P(i + 1) = P(i) * lambda / (i + 1)
resultado = min(X, k)
```

La inversión de esta distribución necesita como máximo k pasos. Con hasta siete etapas, el coste por planta está acotado aunque hayan pasado diez minutos o un año. Gestionar cero, valores enormes y subdesbordamiento; no introducir un umbral que declare madurez sin cuantificar el error.

### 7.3 Reglas por cultivo

Los adaptadores deben comprobarse contra las clases reales de 26.3. Valores de referencia que debe confirmar el prototipo:

| Cultivo | Edad máxima | Regla de progreso de referencia |
|---|---:|---|
| Trigo, zanahoria, patata | 7 | Humedad, distribución y requisito lumínico del bloque |
| Remolacha | 3 | Regla de cultivo con factor adicional de intentos, referencia `2/3` |
| Verruga del Nether | 3 | Referencia `p = 1/10`, soporte y condiciones propias |
| Cacao | 2 | Referencia `p = 1/5`, orientación y soporte válidos |
| Bayas dulces | 3 | Referencia `p = 1/5` y requisito lumínico propio |
| Tallos, ampliación | 7 | Maduración y producción son procesos distintos |

Para cultivos ordinarios, la fórmula de referencia suma 1 y las contribuciones del farmland 3 × 3: centro húmedo 3, seco 1; vecinos a un cuarto de esos valores. El patrón de cultivos iguales puede dividir los puntos por dos. La probabilidad de referencia es `1 / (floor(25 / puntos) + 1)`.

Comprobar posiciones y tipos exactos de la penalización en la versión objetivo. Una propiedad de edad por sí sola no habilita crecimiento.

### 7.4 Tiempos orientativos

A 20 TPS, `randomTickSpeed = 3` y condiciones estables, la espera media entre selecciones de un bloque es aproximadamente 68,27 segundos. Son medias redondeadas, no una ETA individual.

| Parcela | p ordinaria | Trigo atendido | Trigo lejos, servidor con jugadores (40 %) | Trigo con servidor vacío (5 %) |
|---|---:|---:|---:|---:|
| Farmland húmedo 3 × 3, sin penalización | 1/3 | 24 min | 1 h | 8 h |
| Farmland húmedo 3 × 3, con penalización | 1/6 | 48 min | 2 h | 16 h |
| Farmland húmedo aislado | 1/7 | 56 min | 2 h 19 min | 18 h 35 min |
| Farmland seco 3 × 3, con penalización | 1/13 | 104 min | 4 h 19 min | 34 h 31 min |

En la última fila una etapa tarda unos 14,8 minutos. No publicar percentiles sin calcularlos ni exigir que dos plantas aleatorias tarden exactamente el doble.

| Tasa desatendida | Trigo desatendido en parcela húmeda penalizada | Balance |
|---:|---:|---|
| 1,00 | 48 min | Paridad de tasa esperada |
| 0,75 | 64 min | Penalización suave |
| 0,50 | 96 min | Alternativa de balance |
| **0,40** | **2 h** | **Logitanos con jugadores conectados** |
| **0,05** | **16 h** | **Logitanos con servidor vacío** |
| 0,30 | 159 min | Crecimiento complementario |
| 0,25 | 191 min | Penalización alta |

El mod aporta progreso donde Minecraft normalmente lo detendría. La garantía correcta es «sin duplicar tramos y con tasa desatendida limitada», no «nunca crece más que sin el mod».

## 8. Tallos y fruta: ampliación específica

Un tallo de edad 7 necesita seguimiento para producir fruta. No puede eliminarse como trigo maduro. Un tallo unido a fruta y uno libre también tienen estados distintos.

- Separar intentos de maduración e intentos posteriores de producción.
- Como máximo, una fruta por tallo durante una compensación. No acumular cosechas históricas ni objetos.
- Respetar espacio, suelo, dirección y estado del tallo. No reemplazar bloques existentes.
- Exigir cargados origen y destino; aplicar la política de vecinos ausentes del apartado 6.2.
- Colocar fruta y actualizar tallo mediante el mecanismo correcto de 26.3; no fijar un flag numérico sin verificar sus efectos.
- Un tallo unido no produce otra fruta hasta que la mecánica vanilla lo libere.
- Probar observadores y pistones. Materializar crecimiento no reproduce eventos de redstone durante la ausencia.

Introducir `stemsProduceFruit` con esta ampliación. No publicar una opción que aparente producir fruta mientras solo se soporta maduración.

## 9. Presupuestos y degradación

### 9.1 Coste esperado

| Recurso | Política |
|---|---|
| Zonas descargadas | Sin iteración ni referencias propias a sus registros tras finalizar la descarga vanilla |
| Plantas atendidas | Sin recorrido periódico; solo registro y cambios de régimen |
| Chunks cargados registrados | Detección O(L), trabajo por plantas solo en lotes |
| Compensación | Tope de entradas y presupuesto temporal compartido por tick |
| Adopción | Trabajo de menor prioridad dentro del mismo presupuesto |
| Memoria | Topes de entradas residentes, claves y cola |
| Disco | Registro compacto por chunk, sin historial ilimitado ni índice mundial duplicado |

Propuesta inicial: 256 entradas por tick y 0,5 ms de trabajo compartido. Ajustar tras medir. Comprobar tiempo entre entradas: no se puede interrumpir una operación individual, por lo que también hay que medir su peor coste.

Vanilla no espera a que termine un lote para volver a dar random ticks. Sus tramos posteriores están separados de la deuda anterior. En chunks cargados desatendidos, una revisión cada 100 ticks encola trabajo si existe deuda, sin recorrer plantas hasta que corresponda procesarlas.

### 9.2 Saturación

- Limitar plantas por chunk y seguimiento residente. No expresar un tope de RAM como un supuesto total mundial sin índice mundial fiable.
- Al alcanzar el tope residente, rechazar nuevas altas. Si la carga de attachments existentes supera el tope, retirar seguimiento cargado de forma conservadora y acotada; persistir o descartar deuda con motivo explícito. Vanilla continúa.
- Una deuda descartada debe avanzar su frontera consumida para no reaparecer al cargar otra vez.
- Cola llena: dejar deuda en el attachment y reintentar desde el conjunto acotado de chunks cargados. No crear una lista auxiliar ilimitada.
- Deduplicar por chunk y tipo de trabajo. No crear temporizadores ni tareas por planta.
- Agrupar advertencias y limitar frecuencia. No generar logs por cultivo o por tick.
- Limpiar entradas obsoletas al visitarlas; no barrer regiones del mundo.

### 9.3 Objetivos de aceptación

Medir en el servidor objetivo indicando hardware, JVM, mods, TPS y muestra:

- Todas las granjas descargadas: ninguna visita, ninguna carga causada por LiveCrops y RAM de sus registros recuperable por GC. Solo permanecen pequeños relojes y estructuras reutilizables vacías.
- Cien chunks registrados cargados: sobrecoste p95 de detección inferior a 0,1 ms/tick fuera de compensaciones, como objetivo a validar.
- La compensación respeta presupuesto sin picos proporcionales a todas las plantas del mundo.
- Explorar miles de granjas y descargarlas no deja crecimiento sostenido de heap atribuible a sus registros frente al servidor base.
- Publicar bytes observados por planta y por chunk; no presentar el tamaño de una posición como RAM total.

## 10. Configuración propuesta

Configuración predeterminada integrada en el mod; este bloque documenta los valores y no se genera como fichero. Los ajustes por comandos se guardan con los datos del mundo mediante ServerSettings:

```json
{
  "enabled": true,
  "onlineRate": 0.4,
  "emptyServerRate": 0.05,
  "maxTrackedCropsPerChunk": 2048,
  "maxResidentTrackedCrops": 65536,
  "maxQueuedChunks": 256,
  "maxCatchUpEntriesPerTick": 256,
  "maxWorkMillisPerTick": 0.5,
  "loadedDormantRefreshTicks": 100,
  "missingNeighborRetryTicks": 100,
  "adoptionMaxRadiusChunks": 6,
  "autoAdoptOnFirstLoad": true,
  "discoverAroundRespawn": true,
  "respawnDiscoveryRadiusBlocks": 128,
  "stemsProduceFruit": true,
  "crops": {
    "minecraft:wheat": true,
    "minecraft:carrots": true,
    "minecraft:potatoes": true,
    "minecraft:beetroots": true,
    "minecraft:nether_wart": true,
    "minecraft:cocoa": true,
    "minecraft:sweet_berry_bush": true,
    "minecraft:pumpkin_stem": true,
    "minecraft:melon_stem": true
  }
}
```

Validar tasa finita entre 0 y 1, límites y periodos positivos con máximos razonables, identificadores conocidos y presupuesto finito positivo. Los valores iniciales aún no proceden de benchmarks.

Cada cambio por comando se valida completo antes de aplicarlo. Si falla, conservar los valores anteriores y explicar el motivo. Los ajustes personalizados se guardan con el mundo, sin JSON externo. Los cambios de tasa afectan al reloj sin visitar chunks descargados.

Desactivar un tipo descarta su seguimiento y deuda al encontrarlo. Para reconocer también desactivaciones y reactivaciones durante ausencias, guardar un marcador de invalidación por tipo en el pequeño metadato global y compararlo al cargar. La readmisión empieza sin antigüedad previa. Cada entrada o registro debe conservar la revisión de tipos contra la que fue admitido.

No imponer por defecto un máximo de ausencia: el muestreo ya es independiente de su duración y la edad está limitada. Añadir topes de balance solo si las pruebas de juego los justifican.

## 11. Comandos y comunicación

Sin GUI propia. La entrega inicial usa mensajes de chat en español; traducciones adicionales quedan como mejora posterior.

| Comando | Acceso | Resultado |
|---|---|---|
| `/livecrops ayuda` | Jugadores | Reglas y tasa actual |
| `/livecrops estado` | Jugadores | Cultivo apuntado a alcance limitado: edad, compatibilidad, régimen y bloqueo |
| `/livecrops info` | Operador nivel 2 | Entradas residentes, chunks, cola, descartes y tiempos |
| `/livecrops ritmo conectados|vacio <0..1>` | Operador nivel 2 | Cambio validado, persistido y efectivo desde la siguiente frontera |
| `/livecrops adoptar [radio]` | Operador nivel 2 | Vista previa en chunks cargados; radio predeterminado 1 |
| `/livecrops adoptar <radio> confirmar` | Operador nivel 2 | Adopción presupuestada con resultado final |
| `/livecrops olvidar chunk` | Operador nivel 2 | Vista previa del seguimiento del chunk actual |
| `/livecrops olvidar chunk confirmar` | Operador nivel 2 | Retirar seguimiento sin romper plantas |
| /livecrops config | Operador nivel 2 | Consulta los valores actuales |
| /livecrops config <opción> <valor> | Operador nivel 2 | Ajuste específico validado |
| /livecrops config cultivo <id> <true/false> | Operador nivel 2 | Activa o desactiva un cultivo |
| /livecrops config restablecer | Operador nivel 2 | Restaura los valores predeterminados |

Las consultas no materializan parcelas, cargan chunks ni provocan guardados. No mostrar como exacto un total mundial desconocido. Los comandos de posición requieren jugador o coordenadas explícitas para consola.

Ejemplos:

- «Trigo · edad 3/7 · seguimiento activo · crecimiento desatendido al 40 % con jugadores conectados y al 5 % con servidor vacío.»
- «Compensación pendiente. La parcela se actualizará por lotes.»
- «Sin progreso: condiciones de luz insuficientes para este cultivo.»
- «Sin seguimiento: límite del chunk alcanzado. El crecimiento normal continúa.»
- «Adopción completada: 384 registrados, 12 maduros omitidos y 2 chunks descargados durante el proceso.»

No enviar avisos por cada carga o maduración. Información habitual bajo demanda, sin ETA exacta ni supuestos propietarios que no se registran.

## 12. Adopción de huertos existentes

Registrar automáticamente colocaciones confirmadas y cultivos existentes encontrados por exploración. La adopción retroactiva incorpora parcelas anteriores sin inferir autoría, tanto alrededor de cama/respawn como al cargar chunks.

La vista previa cuenta chunks cargados candidatos. La ejecución recorre secciones bajo presupuesto; omitir paletas sin cultivos compatibles si la API lo permite. Consultar una paleta evita secciones vacías, pero una sección candidata puede requerir recorrer posiciones.

No usar accesos que carguen chunks. Omitir e informar las zonas descargadas durante el trabajo. Repetir adopción no duplica plantas. Un cultivo adoptado empieza sin antigüedad retroactiva. Bayas maduras omitidas podrán registrarse tras una recolección de jugador mediante su hook específico.

La adopción automática está activada por defecto. La primera búsqueda usa la cama o respawn del jugador, con un radio horizontal de 128 bloques ajustable entre 0 y 512. Una firma persistente recuerda punto y radio; los chunks descargados se revisan al cargarse naturalmente. Si se implementa, requiere marca persistente por chunk y versión de adopción, con presupuesto compartido. Nunca barre el mundo al arrancar.

## 13. Guardado, reinicios y recuperación

Marcar datos modificados al registrar, eliminar, cambiar fronteras o avanzar un lote. Si la API no detecta mutaciones internas de estructuras mutables, reemplazar el valor o marcar explícitamente el chunk. Comprobarlo reiniciando.

Bloque y avance de deuda deben formar una instantánea coherente. Reloj global y chunk pertenecen a unidades de persistencia distintas: **no asumir atomicidad global del guardado**. El prototipo debe comprobar el orden y resolver desajustes conservadoramente.

- Parada normal: guardar reloj, intervalos y cursor; reanudar sin contar tiempo apagado.
- Caída abrupta: se admite perder progreso desde la última instantánea consistente. No prometer recuperar ticks nunca guardados.
- Marca del chunk posterior al reloj recuperado: descartar o rebasar conservadoramente ese intervalo, sin interpretarlo como una ausencia enorme.
- Datos futuros o corruptos: aislar seguimiento afectado, conservar diagnóstico y mantener vanilla. No sobrescribir silenciosamente datos desconocidos.
- Copias de seguridad: restaurar mundo y metadatos juntos. Una restauración parcial activa la política de desajustes y una advertencia.
- Desinstalación: las plantas conservan su estado vanilla. Verificar el tratamiento de attachments desconocidos; no prometer conservar deuda tras retirar y reinstalar el mod.

Antes de declarar estabilidad, demostrar que bloques y deuda se serializan juntos incluso con descarga y lotes pendientes. La entrega usa fronteras consumidas por planta; el cursor y el objetivo del lote no necesitan persistirse. Tras recargar se reinicia el recorrido y solo se cobra deuda posterior a cada frontera. La atomicidad con el guardado de bloques sigue requiriendo validación en un servidor real.

## 14. Estructura y compatibilidad

Base comprobada en los archivos locales de TheBall: Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3, Java 25 y Loom 1.19-SNAPSHOT. Son las versiones del repositorio, no una garantía para otras versiones.

Reutilizar configuración Gradle y wrapper necesarios, sin copiar entidades, renderizadores ni recursos ajenos al mod.

```text
mods/LiveCrops/
  idea.md
  README.md
  build.gradle, gradle.properties, settings.gradle, gradlew*, gradle/wrapper/
  src/main/java/com/livecrops/
    LiveCropsMod.java            arranque y eventos
    CropKind.java                reglas verificadas por cultivo
    CropChunkData.java           attachment, codec y versionado
    GrowthClock.java             intensidad por dimensión
    DormantLedger.java           acumulación local independiente del juego
    GrowthEngine.java            seguimiento, lotes y aplicación
    GrowthMath.java              muestreo acotado y fases de tallos
    LiveCropsConfig.java          valores predeterminados, validación y codec
    ServerSettings.java           ajustes guardados con el mundo
    command/LiveCropsCommand.java
    mixin/                       solo hooks no cubiertos por APIs
  src/main/resources/
    fabric.mod.json
    livecrops.mixins.json         si hay mixins
    assets/livecrops/lang/es_es.json
  compilar.ps1                   compilación y copia a jar/LiveCrops
  // jar/LiveCrops contiene únicamente el jar compilado.
  src/test/                      pruebas de núcleo, codecs y hooks
```

Mod id `livecrops`, grupo `com.livecrops`; clases en inglés y comentarios en español. Declarar entorno de servidor, evitar referencias cliente y comprobar funcionamiento en servidor integrado antes de documentarlo.

Confirmar eventos, getters de luz, flags de actualización y firmas de mixins contra las dependencias de 26.3. Los esquemas de este documento son pseudocódigo, no Java listo para compilar.

Verificar mods de crecimiento, iluminación, optimización de chunks y protección de parcelas. Si otro mod modifica probabilidades, el adaptador vanilla no garantiza la misma tasa; ofrecer exclusión y documentar combinaciones probadas.

Al publicar: README con versiones, instalación solo servidor, mecánicas, limitaciones, comandos y desarrollo; compilación en local y `.jar` en `jar/LiveCrops/`; fila en README raíz. No necesita crafteos porque no añade items ni bloques. Estas tareas corresponden a la implementación, no a esta revisión del plan.

## 15. Plan de ejecución

1. **Integración crítica:** inspeccionar fuentes de 26.3 y demostrar elegibilidad, fronteras de random ticks, persistencia y descarga sin retención. No continuar si solo funciona al descargar chunks.
2. **Núcleo:** reloj, acumulación local, generaciones, codec, registro y cola. Empezar con trigo en chunks de regímenes distintos.
3. **Reglas funcionales:** completar cultivos, sustituciones, bayas tras recolección, cambios de ritmo y bordes.
4. **Operación:** configuración, comandos, adopción y diagnóstico. Verificar saturación sin estructuras ilimitadas.
5. **Medición:** multijugador, reinicios, guardados durante lotes y perfiles de CPU/RAM tras exploración prolongada.
6. **Entrega:** compilación reproducible, cliente sin mod, README y CI.
7. **Ampliación:** tallos y fruta solo después de estabilizar el núcleo.

No fijar plazos antes de resolver la primera etapa.

## 16. Pruebas y criterios de aceptación

### 16.1 Pruebas automatizadas focalizadas

- Reloj: distintas tasas, cero, desactivación, pausa, precisión y marcas futuras; ausencia de efectos retroactivos.
- Muestreo: resultado entre cero y etapas restantes, cero intensidad, valores enormes y distribución truncada con semilla fija y tolerancias documentadas.
- Registro: duplicados, replantación, sustituciones del mismo tipo y altas durante lotes.
- Persistencia: ida y vuelta del codec, cursor parcial, migración, corrupción y reloj desajustado.
- Cola: deduplicación, presupuestos, saturación y tareas antiguas tras descargar/recargar.

No exigir parcelas idénticas por compartir semilla de mundo si el mod usa otra fuente aleatoria. Comparar distribuciones o medias sobre muestras suficientes.

### 16.2 Matriz de integración

| Caso | Resultado requerido |
|---|---|
| Cultivo atendido | Solo vanilla, sin compensación del mismo tramo |
| Jugador conectado lejos de la parcela | La parcela sigue desatendida; estar en el servidor no basta para activarla |
| El último jugador cercano se aleja o desconecta | Empieza el periodo compensable de esa zona |
| Regresa un jugador a la parcela | Liquidar solo la ausencia anterior; el tiempo nuevo cercano pertenece a vanilla |
| Zona desatendida descargada | Progreso al volver, sin visitas o cargas durante ausencia |
| Zona cargada sin random ticks | Progreso desatendido; no perderlo por seguir cargada |
| Cambio de elegibilidad sin descargar | Detectarlo y asignar cada tramo una vez |
| Varios jugadores entrando y saliendo | Usar elegibilidad real, no pertenencia a un jugador |
| Spawn, forceload o ticket externo | Clasificar según reglas reales de 26.3 |
| Visión mayor que simulación | Compensar franja cargada sin random ticks |
| Todos desconectados, servidor tickeando | Avanza el reloj, sin visitar granjas descargadas |
| Todos desconectados, servidor pausado | Sin crecimiento por tiempo pausado |
| Gamerule 0, 3 y otro valor | Respetar su valor en cada tramo |
| Cambiar ritmo durante ausencia | Cada tramo usa la tasa vigente entonces |
| Dormir, cambiar hora, parar y reiniciar | No conceder ticks inexistentes |
| Luz, soporte, humedad, distribución y noche | Aplicar regla verificada del adaptador |
| Vecino necesario descargado | Sin cargarlo; reintento y descarte acotados |
| Cosechar/replantar con deuda pendiente | Nueva generación sin deuda anterior |
| Bayas cosechadas por jugador | Retomar seguimiento desde su edad nueva |
| Agua, explosión, pistón o aldeano | Invalidar vida anterior sin resucitarla |
| Descarga con lote parcial | Memoria liberable y reanudación sin duplicar |
| Autoguardado durante ausencia larga | Conservar deuda de zonas descargadas |
| Parada con zonas atendidas y deuda pendiente | Instantánea coherente, sin recobrar tiempo atendido |
| Límites residentes, por chunk y de cola | Degradación acotada; vanilla continúa |
| Adopción repetida o interrumpida | Sin duplicados, cargas ni antigüedad inventada |
| Nether, End y overworld | Separación de posiciones, relojes y colas |
| Cliente sin LiveCrops | Conexión y actualizaciones de bloques normales |

### 16.3 Pruebas de carga

Usar parcelas realizables: 256 cultivos por planta horizontal de chunk. Miles de plantas en un chunk requieren varias alturas y respetar el límite configurado o probar expresamente su saturación.

Comparar servidor sin mod, mod sin registros, 100 chunks registrados cargados, muchas parcelas que llegan juntas y miles de zonas históricas ya descargadas. Registrar p50/p95/p99 del coste añadido por tick, duración de colas, RAM retenida tras GC, asignaciones y tamaño de datos en disco.

**Terminado significa:** adaptación a los tres estados demostrada, ausencia de doble cómputo, ningún chunk cargado por LiveCrops, RAM de zonas descargadas liberable y presupuestos verificados. Compilar correctamente por sí solo no demuestra estas propiedades.

## 17. Referencias y puntos pendientes del prototipo

Fuentes primarias de orientación. Las páginas API enlazadas de 26.2 no sustituyen la comprobación local de las dependencias 26.3:

- [Fabric: Data Attachments](https://docs.fabricmc.net/develop/serialization/data-attachments): datos asociados a chunks y persistencia mediante codec; fundamento del almacenamiento local propuesto.
- [Fabric: Saved Data](https://docs.fabricmc.net/develop/serialization/saved-data): persistencia auxiliar del pequeño estado por dimensión.
- [Fabric: ServerChunkEvents](https://maven.fabricmc.net/docs/fabric-api-0.155.2%2B26.2/net/fabricmc/fabric/api/event/lifecycle/v1/ServerChunkEvents.html): carga, descarga y cambio de estado; carga sola no cubre todas las transiciones.
- [Fabric: ServerLifecycleEvents](https://maven.fabricmc.net/docs/fabric-api-0.155.2%2B26.2/net/fabricmc/fabric/api/event/lifecycle/v1/ServerLifecycleEvents.html): ciclo de vida y eventos de guardado para verificar instantáneas.

Resultados que debe producir el prototipo:

1. Condición exacta de random ticks en 26.3 y punto de observación sin comprobaciones redundantes.
2. Cobertura del evento de estado o necesidad del sondeo acotado de chunks cargados.
3. Instantánea coherente de bloques, attachment y cursor durante descarga y guardado asíncrono.
4. Coste real de detección, codecs y consultas ambientales sin cargar vecinos.
5. Invalidación fiable al sustituir un bloque por otro cultivo del mismo tipo.
6. Lista de mods de Logitanos que requieren exclusión o adaptación.

Estas comprobaciones condicionan la implementación; no reducen el requisito de adaptación ni autorizan mantener zonas descargadas en RAM.







