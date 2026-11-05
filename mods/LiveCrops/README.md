# LiveCrops

Crecimiento adaptativo de cultivos para Logitanos. Compensa la ausencia de jugadores sin cargar chunks, crear jugadores ficticios ni simular granjas completas.

| Componente | Versión |
|---|---|
| Mod | 1.0.0 |
| Minecraft Java | 26.3 |
| Fabric Loader | ≥ 0.20.1 |
| Fabric API | 0.165.0+26.3 como base de compilación |
| Java | 25 |

## Funcionamiento

| Situación | Ritmo | Trigo óptimo | Parcela húmeda compacta |
|---|---:|---:|---:|
| Zona atendida por los ticks de vanilla | 100 % | 24 min | 48 min |
| Sin actividad natural y con jugadores conectados en cualquier dimensión | 40 % | 1 h | 2 h |
| Sin actividad natural y servidor vacío | 5 % | 8 h | 16 h |
| Servidor apagado, pausado o ticks congelados | 0 % de progreso nuevo | No avanza | No avanza |

Tiempos medios aproximados desde edad cero, a 20 TPS y randomTickSpeed 3. El azar, humedad, luz y distribución influyen. El mod observa la llamada real a `ServerLevel.tickChunk`: si un ticket externo ya proporciona crecimiento vanilla, no añade otro crecimiento al mismo tramo.

Al descargar una zona, su seguimiento queda en un attachment persistido dentro del chunk y se retira de las estructuras activas del mod. Solo avanza un reloj pequeño por dimensión. Al cargarla, la compensación se aplica en lotes, con presupuesto compartido de 0,5 ms y hasta 256 entradas por tick. Las zonas cargadas sin random ticks también acumulan progreso y se revisan cada 100 ticks.

Cada planta mantiene su propia frontera consumida. Replantar o sustituir el bloque descarta deuda de la vida anterior. Cambios de ritmo y de número de jugadores afectan al reloj desde los ticks siguientes, sin recalcular ausencias a la última tasa.

## Cultivos

Trigo, zanahoria, patata, remolacha, verruga del Nether, cacao, bayas dulces y tallos de melón/calabaza. Se usan las reglas de humedad y distribución de `CropBlock` de la versión objetivo. La remolacha tiene máximo 3 y factor de intentos 2/3. Bayas comprueban luz sobre el bloque; cultivos ordinarios y tallos comprueban luz en su posición.

Los tallos pueden madurar y producir como máximo una fruta por compensación, respetando el espacio y los tags de suelo de Minecraft. Un tallo unido no acumula fruta. La recolección de bayas por un jugador permite volver a registrar la planta.

Las plantaciones nuevas y los cultivos existentes se registran automáticamente. No hace falta ejecutar un comando al instalar el mod en un mundo anterior. Los bloques no guardan quién los plantó: la detección retroactiva reconoce todos los cultivos compatibles, incluidos los de aldeas y las bayas naturales. No se añaden bloques, entidades, items ni crafteos.

## Detección de huertos existentes

La primera exploración de cada jugador usa su cama o punto de reaparición registrado, incluido un ancla de reaparición. Si no tiene uno o su dimensión ya no existe, utiliza el respawn del servidor. El radio horizontal predeterminado es de **128 bloques**, trabajando sobre los chunks que intersectan el área y a todas las alturas. Esto incluye el borde completo de esos chunks.

La exploración se realiza por lotes sobre chunks ya cargados y comparte el presupuesto de trabajo del mod. Los chunks descargados se detectan cuando Minecraft los carga de forma natural. La detección al cargar funciona también lejos de la cama, por lo que los huertos apartados no dependen de la distancia al respawn.

Una marca por chunk evita repetir exploraciones completas. Una marca pequeña en los datos del jugador recuerda la cama/respawn y el radio ya revisados; cambiar el punto o ampliar el radio prepara una nueva revisión. No se fuerza la carga del punto de reaparición, no se activa ni consume un ancla y no se conservan referencias a chunks descargados. Si la cola está llena, las claves pendientes de zonas residentes esperan hasta tener sitio; se retiran al descargar y vuelven a detectarse en la próxima carga.

Para ampliar la zona inicial: `/livecrops config respawnDiscoveryRadiusBlocks 256` (entre 0 y 512 bloques). El comando manual de adopción sigue disponible para repetir una revisión bajo demanda. Las plantas se registran con su estado actual y el crecimiento se cuenta desde su detección; no se inventa antigüedad anterior.

## Instalación posterior en un servidor

El archivo de distribución está en `jar/LiveCrops/livecrops-1.0.0.jar`, desde la raíz del repositorio. Esta entrega no lo instala en ninguna carpeta de Minecraft.

Cuando se despliegue, el administrador deberá usar ese jar y Fabric API en un servidor Fabric compatible. Los clientes no necesitan LiveCrops: no añade contenido cliente ni sincroniza attachments. También puede ejecutarse en servidor integrado cuando esté instalado localmente; su código solo opera en el lado servidor.

Si se desea crecimiento con el servidor vacío, este debe seguir procesando ticks. Comprobar `pause-when-empty-seconds` del servidor; la configuración de pausa pertenece a Minecraft y LiveCrops no la cambia. Dormir y `/time set` no conceden tiempo extra.

## Configuración

Los valores predeterminados están integrados en el mod. No se genera un JSON de configuración. Los ajustes específicos se realizan mediante comandos de operador y se guardan con los datos del mundo, conservándose tras reiniciar.

| Campo | Predeterminado | Efecto |
|---|---:|---|
| enabled | true | Permite registrar y materializar crecimiento; desactivarlo pausa trabajo y acumulación nuevos |
| onlineRate | 0.40 | Tasa desatendida con al menos un jugador conectado |
| emptyServerRate | 0.05 | Tasa desatendida sin jugadores; debe ser ≤ onlineRate |
| maxTrackedCropsPerChunk | 2048 | Tope por chunk, máximo configurable 4096 |
| maxResidentTrackedCrops | 65536 | Tope de seguimiento de plantas residentes |
| maxQueuedChunks | 256 | Límite de cola de compensación y de adopción, cada una acotada |
| maxCatchUpEntriesPerTick | 256 | Tope conjunto de operaciones de compensación/adopción |
| maxWorkMillisPerTick | 0.5 | Presupuesto compartido entre 0,01 y 10 ms; se comprueba entre operaciones |
| loadedDormantRefreshTicks | 100 | Frecuencia de encolado para zonas cargadas desatendidas |
| missingNeighborRetryTicks | 100 | Plazo de reintento con vecinos o iluminación pendientes |
| adoptionMaxRadiusChunks | 6 | Máximo radio del comando de adopción |
| autoAdoptOnFirstLoad | true | Detecta huertos existentes al cargar chunks, con marca persistente |
| discoverAroundRespawn | true | Exploración inicial alrededor de cama o respawn del jugador |
| respawnDiscoveryRadiusBlocks | 128 | Radio horizontal de exploración, entre 0 y 512 bloques |
| stemsProduceFruit | true | Permite producción acotada de fruta |
| crops | Todos los tipos soportados | Activación por identificador vanilla |

Las tasas deben ser finitas y estar entre cero y uno. Un cambio inválido conserva todos los valores anteriores. La configuración personalizada se marca para el guardado normal de Minecraft; sin cambios se usan directamente los valores integrados.

`/livecrops config` muestra todos los valores. Cada opción escalar de la tabla admite `/livecrops config <nombre> <valor>`, con autocompletado y validación. Ejemplos: `/livecrops config maxCatchUpEntriesPerTick 128`, `/livecrops config stemsProduceFruit false` y `/livecrops config cultivo minecraft:wheat false`. `/livecrops config restablecer` restaura los valores predeterminados.

## Comandos

Los comandos públicos son `ayuda` y `estado`. Administración exige permiso de gamemaster, equivalente al nivel de operador 2.

| Comando | Uso |
|---|---|
| `/livecrops ayuda` | Explica las reglas y tasas vigentes |
| `/livecrops estado` | Inspecciona el primer bloque apuntado hasta seis bloques, sin cargar zonas |
| `/livecrops info` | Estado, registros residentes, colas, contadores de sesión y último tiempo de trabajo |
| `/livecrops ritmo conectados 0.4` | Cambia la tasa con jugadores conectados |
| `/livecrops ritmo vacio 0.05` | Cambia la tasa con servidor vacío |
| `/livecrops config` | Consulta la configuración actual |
| `/livecrops config <opción> <valor>` | Cambia una opción booleana, entera o decimal |
| `/livecrops config cultivo <id> <true|false>` | Activa o desactiva un cultivo compatible |
| `/livecrops config restablecer` | Restaura los valores predeterminados |
| `/livecrops adoptar [radio]` | Vista previa del número de chunks ya cargados; radio predeterminado 1 |
| `/livecrops adoptar <radio> confirmar` | Encola los chunks que caben en la cola y muestra cuántos aceptó |
| `/livecrops olvidar chunk` | Vista previa del seguimiento del chunk actual |
| `/livecrops olvidar chunk confirmar` | Retira ese seguimiento sin romper plantas |

Adoptar es idempotente y no concede antigüedad anterior. La adopción avanza por secciones y posiciones bajo el presupuesto compartido; al terminar informa al jugador si sigue conectado y escribe el resultado en el log. Si faltó capacidad de cola, se puede repetir. La consola usa su posición y dimensión de origen para los comandos de área; `estado` requiere jugador.

## Límites conocidos

- Se usa una aproximación Poisson para etapas ordinarias y etapas exponenciales para tallos. No se reproduce la secuencia aleatoria original.
- Las condiciones ambientales se evalúan al materializar. No se reconstruye la historia de luz, humedad o suelo; modificarlos antes puede afectar a la compensación.
- Consultas ambientales solo con los chunks necesarios cargados y su iluminación correcta. Si faltan durante el plazo configurado, se descarta ese tramo; la planta sigue registrada.
- Se prioriza respetar límites. Si un attachment cargado excede los topes, se retira su seguimiento y se avisa. Las plantas siguen creciendo con vanilla.
- RAM descargada queda liberable después del ciclo normal de guardado/descarga y GC de Minecraft. No significa cero RAM del servidor completo.
- Los contadores de `info` describen memoria activa y sesión, no todos los cultivos históricos del mundo.
- Restaurar parcialmente un mundo puede desajustar reloj y chunk. Se rebasa un reloj futuro sin regalar crecimiento; no se promete recuperar trabajo no guardado tras una caída.
- Mods que sustituyan reglas de crecimiento o anulen el método observado necesitan pruebas de compatibilidad. Se pueden desactivar tipos individualmente.

## Desarrollo y compilación

Desde PowerShell, con JDK 25 y las dependencias de desarrollo ya disponibles:

```powershell
.\compilar.ps1 -Offline
```

El script no instala Minecraft ni copia archivos a una instalación del juego. Ajusta solo para su proceso la ruta temporal de sockets del JDK, compila, ejecuta `verifyCore` y copia el jar a `../../jar/LiveCrops`. Restaura la variable de entorno al terminar.

En un entorno Gradle normal:

```sh
bash gradlew build collectJar
```

`verifyCore` ejecuta pruebas Java sin framework adicional, sin cliente y sin crear mundos. El informe está en `build/reports/core-tests.txt`. Las comprobaciones abarcan distribución estadística, transiciones, fronteras por planta, serialización, configuración, geometría de exploración y compatibilidad estática de los hooks con los jars de desarrollo.

La compilación y estas pruebas no sustituyen una prueba en un servidor real: carga/descarga, interacción multijugador, sincronización con cliente y picos de CPU deben validarse allí antes de declarar rendimiento medido.

Clases principales: `GrowthEngine` integra eventos y presupuestos; `CropChunkData` mantiene arrays primitivos y codec; `DormantLedger` separa tramos; `GrowthClock` persiste intensidad y revisiones; `ServerSettings` conserva ajustes personalizados con el mundo; `CropKind` define reglas; `GrowthMath` implementa muestreo; `LiveCropsCommand` contiene comandos.
