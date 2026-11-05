# TheSlave — Guía técnica de implementación del lore oscuro

Fecha: 5 de noviembre de 2026.

Documento funcional de referencia: [new_lore.md](new_lore.md).

Esta guía explica cómo llevar el diseño cerrado al mod existente. La sección 1.1 conserva la auditoría del punto de partida; la sección 1.2 identifica la implementación actual. Las propuestas posteriores deben contrastarse con esos archivos reales. No añade ingredientes, pasos de juego ni cambios de equilibrio al lore. Invocación, ambas súplicas y fusión forman parte del mismo lanzamiento; las etapas de desarrollo indicadas al final son internas.

## 1. Plataforma y punto de partida real

La configuración local comprobada es:

| Elemento | Valor del proyecto |
|---|---|
| Minecraft | 26.3 |
| Java | 25 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.165.0+26.3 |
| Plugin declarado | `net.fabricmc.fabric-loom`, `1.19-SNAPSHOT` |
| Entornos | `src/main` para lógica común/servidor y `src/client` para presentación |
| Identificador | `theslave` |

Fuentes: [gradle.properties](../gradle.properties), [build.gradle](../build.gradle) y [fabric.mod.json](../src/main/resources/fabric.mod.json).

La documentación web consultada de Fabric muestra ejemplos para 26.2. Sirve para los conceptos; antes de escribir código hay que contrastar firmas con los JAR y fuentes de **26.3** resueltos por este proyecto. No introducir automáticamente Yarn, el antiguo remapeado ni ejemplos de render de 1.20. Se pueden generar fuentes con `gradlew genSources` e inspeccionar las clases locales con `javap`.

### 1.1. Auditoría inicial anterior a esta ampliación

| Código existente | Situación observada | Trabajo necesario |
|---|---|---|
| [Registro.java](../src/main/java/com/theslave/Registro.java) | Una ficha por dueño con un único UUID de esclavo | Admitir hasta tres vínculos, reservas, difuntos, fusionados y operaciones persistentes |
| [ContratoItem.java](../src/main/java/com/theslave/item/ContratoItem.java) | Crea una entidad sin dueño al usarlo sobre un bloque | Sustituir esa vía por contrato personal más ofrenda y altar; cerrar la invocación gratuita antigua |
| [EsclavoEntity.java](../src/main/java/com/theslave/entity/EsclavoEntity.java) | Gestos, trabajos, mochila, hambre, estados y adopción con látigo | Conservar el trabajo e incorporar seguimiento, aptitudes, identidad de fusión y bloqueo ritual |
| [Ritual.java](../src/main/java/com/theslave/ritual/Ritual.java) | Súplica antigua con contrato, manzana, antorchas y soledad; sesiones en memoria | Reemplazar requisitos antiguos por los nuevos y controlar cuatro secuencias con recuperación persistente |
| [TumbaBlockEntity.java](../src/main/java/com/theslave/block/TumbaBlockEntity.java) | Nombre, variante y custodia limitada a contrato/manzana | Guardar identidad y aptitudes completas; asociar el tributo correcto y migrar custodia antigua |
| [LaMuerteEntity.java](../src/main/java/com/theslave/entity/LaMuerteEntity.java) | Entidad inmóvil, sin IA y no guardada en su registro | Darle movimiento dirigido de entrada, actuación y salida por portal; recrearla o limpiarla desde la sesión |
| [Payloads.java](../src/main/java/com/theslave/net/Payloads.java) | Paquetes para pantallas y escena | Añadir identidad de sesión, tipo, tiempos y estados para observadores |
| [RitualOverlay.java](../src/client/java/com/theslave/client/RitualOverlay.java) | HUD, destello y modificación aleatoria de la rotación del jugador | Conservar subtítulos/viñeta y retirar giros forzados; seguir la dirección de terror del lore |
| [Config.java](../src/main/java/com/theslave/Config.java) | `maxPorJugador = 1` | Aplicar máximo de tres en todas las vías y migrar configuración existente |
| [ModBlocks.java](../src/main/java/com/theslave/registry/ModBlocks.java), [ModItems.java](../src/main/java/com/theslave/registry/ModItems.java), [ModEntities.java](../src/main/java/com/theslave/registry/ModEntities.java) | Registros centrales existentes | Ampliarlos sin cambiar los identificadores antiguos de forma incompatible |

No asumir que añadir una condición al contrato arregla el límite: la adopción, la resurrección, los mundos descargados y las reservas también deben pasar por el mismo registro.

### 1.2. Implementación actual y revisión del lecho personal

La ampliación utiliza los eventos y guardados de Minecraft/Fabric dentro del mod existente. En esta máquina la validación se limita a compilación y pruebas de código: **no ejecutar cliente, servidor, mundos locales ni aceptar la EULA**. El audio definitivo queda aplazado por indicación del usuario.

- `Registro` conserva hasta tres vínculos y recibos de operaciones. `persistencia/ArchivoDurable`, `DiarioOperaciones`, `TransaccionJugador` y `CustodiaJugador` coordinan compras, devoluciones y playerdata con un marcador serializado junto al inventario. `CerrojosJugador` se reconstruye para todos los PREP desde `SERVER_STARTING`.
- `ritual/RitualDurable` prepara el UUID y snapshot del resultado antes de mutar cuentas o inventarios. Confirma playerdata y el almacenamiento de entidades; conserva recibos, filtra los originales retirados y protege reconexiones mediante sesión, intento e identidad del `ServerPlayer`.
- `ritual/EntityDurability` espera la carga real de entidades y confirma escritura más `synchronize(true)` del escritor vanilla. Los mixins interceptan las sobrecargas comprobadas del JAR local. Un simple `saveAll()` no es el ACK.
- `persistencia/TransferenciasDurables` coordina muestras, provisiones, equipo y cobros con copias de ambos inventarios, UUID estable del cuerpo, PREP, ACK de jugador y entidad y COMMIT. Se registra después de los filtros de ritual y muerte. Conserva el último recibo del actor para reparar una copia antigua del chunk; un marcador actual no restaura carga antigua. Solo poda el recibo anterior cuando nuevo COMMIT e índice están persistidos. El excedente de cobro permanece con el esclavo.
- `ritual/MuerteDurable` conserva identidad y botín con UUID fijos; el equipo cae donde murió el cuerpo. `ritual/Hogares` gobierna un lecho canónico por vínculo, su colocación/recogida, renuncia y extracción única del alma. Incluye entrega de lechos a individuos de partidas anteriores.
- `block/LechoBlock`, `LechoBlockEntity` y `item/LechoItem` representan dos partes con `facing`, `part`, `grave` y `fusionado`. La muerte cambia ese hogar a tumba incluso cuando esté descargado. Un lecho portátil colocado después de morir aparece como tumba.
- `ModComponents.ALMA_VINCULADA` conserva dueño, vínculo, generación de muerte, token y condición de fusionado. El alma y el tributo se presentan en el altar; **se elimina la cercanía de la tumba como requisito**. El alma fija la identidad y el tributo sigue exigiendo su precio acordado. Los tributos antiguos asociados a un difunto concreto siguen limitados a esa identidad.
- `entity/Mantenimiento` dirige hambre, esfuerzo, descanso activo y recuperación pasiva. `EsclavoEntity` conserva provisiones y encargo al pausar; replanifica desde inventario tras una carga. El regreso al hogar intenta navegación y solo permite rescate por teletransporte a un destino cargado y seguro.
- El cliente recorta geometría para el portal y las mitades de la fusión. `AparicionRitual` y `GestosMuerte` dirigen los cuerpos temporales y los gestos. Son presentación: no crean una entidad real ni entregan inventario antes de la confirmación.

Verificaciones disponibles: `verifyTrabajoSeguro`, `verifyFabricacion`, `verifyKnowledgeBudget`, `verifyLogic`, `verifyRitual`, `verifyRitualDurable`, `verifyEntityDurability`, `verifyDeathDurability`, `verifyHomeDurability`, `verifyLore` y `verifyWhip`, incluidas en `check`. `previewLore` genera una hoja geométrica fuera del juego. `tools/test_lore_assets.py` comprueba modelos, variantes y los nueve recursos protegidos de látigo/contrato. Las pruebas simulan fronteras de caída; no equivalen a ensayos dentro de una partida ni a validación GPU.

### 1.3. Endurecimiento de seguridad y presupuesto de servidor

- Las peticiones de red tienen limites antes de encolarse, validan la instancia conectada, propietario, custodia, distancia, dimension y chunk cargado. Cambiar nombre sin cambios no reescribe el registro.
- Los recibos comerciales confirmados conservan identidad y huella SHA-256 sin retener inventarios completos. Se mantiene el ultimo recibo comercial de cada propietario; los recibos externos de hogar, muerte y ritual no se podan con ese criterio. Los PREP conservan toda la informacion necesaria para recuperar.
- Los indices de hogares, muertes y rituales evitan recorrer todo el historial en cada carga de chunk. Los snapshots confirmados se liberan de RAM; los archivos persistentes siguen permitiendo auditar el historial. Las confirmaciones concurrentes de un chunk solicitan una escritura nueva.
- `ai/BarridoMenas` recorre cascaras cercanas con memoria constante. `PresupuestoEscaneo` reparte 1024 posiciones/tick en total, con 128 como maximo por trabajador por defecto. Hasta 64 candidatos y 512 posiciones vetadas por trabajador. Configuracion acotada y limite fijo de tres vinculos.
- `Conocimiento` prepara recetas y fuentes en el hilo servidor, por lotes con objetivo de 2 ms entre unidades, y se invalida tras recargar datapacks. Una unidad individual depende de la receta o tabla de botin de Minecraft y otros mods; este presupuesto no es una garantia de MSPT.
- Mineria revalida estado, alcance, visibilidad, herramientas y permisos justo antes de romper. No extrae contenedores, fluidos ni bloques irrompibles. Con `respetarProtecciones=true`, requiere propietario conectado en la misma dimension con capacidad de construir y respeta los callbacks `PlayerBlockBreakEvents`. Entrega botin solo despues de confirmar `destroyBlock`.
- `Fabricacion` usa `matches`, `assemble` y recipientes nativos sobre una copia. Una tanda fallida no consume ingredientes. Conserva componentes y el credito de combustible en NBT y procesa hasta 16 tandas por accion. El planificador limita cantidad, profundidad, nodos y pasos.
- La brujula limita su cache a 128 jugadores, reutiliza el destino durante un segundo y sigue verificando el destino fisico. La busqueda de bosque utiliza 64 muestras deterministas por region; no recorre un volumen completo ni carga terreno para comprobar biomas.

## 2. Arquitectura mínima

Mantener el mod y reutilizar sus registros. Separar responsabilidades sin crear un motor genérico de cinemáticas ni un sistema físico de cadenas.

- **Registro de vínculos:** verdad persistente de dueños, plazas, entidades y difuntos.
- **Servicio de comercio:** valida precio, almas, inventario y emisión del conjunto de compra.
- **Datos de objetos:** relicario, contratos, ofrendas, sellos y destino de brújula.
- **Control de rituales:** valida, custodia objetos, bloquea participantes, dirige tiempos y confirma o cancela.
- **IA del esclavo:** seguimiento/espera y trabajo; se suspende mientras participa en un rito.
- **Enclaves:** estructuras, ubicaciones verificadas, Notarios y búsqueda de accesos.
- **Presentación de ritual:** estados de render, portal, velas, cuerpos, cadenas, partículas y sonido.

La lógica que concede, sustituye o revive entidades se ejecuta en el servidor. El cliente representa lo que el servidor autorizó. Ninguna animación, paquete de pantalla ni audio concede una plaza o una recompensa por sí mismo.

## 3. Identificadores y componentes de objetos

Conservar `theslave:contrato` y `theslave:latigo` cuando sea viable; el nombre visible se resuelve mediante traducciones. Para objetos nuevos, propuesta de IDs:

| ID propuesto | Nombre visible |
|---|---|
| `brujula_ceniza` | Brújula de Ceniza |
| `relicario_animas` | Relicario de Ánimas |
| `altar_ceniza` | Altar de Ceniza |
| `ofrenda_muerte` | Ofrenda a la Muerte |
| `tributo_condena` | Tributo de la Condena |
| `sello_almas` | Sello de Almas |
| `tributo_doble_condena` | Tributo de la Doble Condena |

Usar `DataComponentType` con codec de persistencia y codec de red para datos de `ItemStack`; no utilizar solo el nombre visible o `CUSTOM_NAME` como identidad. La API de componentes permite datos propios asociados a objetos; los codecs deben mantener límites y valores por defecto explícitos. [Fabric: Custom Data Components](https://docs.fabricmc.net/develop/items/custom-data-components).

Datos propuestos:

| Dato | Campos mínimos |
|---|---|
| Relicario | Contadores hambrienta/vigilante/acechante; suma entre 0 y 64 |
| Contrato | `contractId`, `ownerUuid`, grado 1–3, versión de datos |
| Ofrenda de invocación | Mismo `contractId` y dueño; contadores exactos de su grado |
| Tributo de retorno | `ownerUuid`, `deceasedId`, normal/fusionado, identidad de compra |
| Sello de fusión | Dueño e identidad de compra; selección final validada al iniciar |
| Látigo incluido | Identidad de compra para la devolución; no vincular su uso a un esclavo |
| Brújula | Dimensión, posición del acceso, ID de enclave y última búsqueda válida |

Un objeto con UUID válido tampoco autoriza por sí solo una operación: comprobar que el registro reconoce su emisión y no lo marca como consumido o devuelto. Esto evita reutilizar copias del mismo pacto.

## 4. Registro de tres vínculos y migración

Un vínculo necesita un ID estable, dueño, estado, UUID de entidad si existe, dimensión/última ubicación, nombre, apariencia, aptitudes, condición de fusionado y procedencia. Estados sugeridos: `RESERVADO`, `VIVO`, `DIFUNTO`, `EN_RITUAL`, `LIBERADO`, `FUSIONADO_EN_OTRO`.

Contar los estados que ocupan plaza sin buscar únicamente entidades cargadas. Una tumba pendiente y un contrato sin usar cuentan. Los registros de progenitores fusionados se conservan como historial, pero no ocupan plazas activas.

Persistir en datos del mundo, preferiblemente una implementación de `SavedData` almacenada en el Overworld para compartir el registro entre dimensiones. El mecanismo requiere marcar cambios; eso no convierte varias escrituras en una transacción atómica. [Fabric: Saved Data](https://docs.fabricmc.net/develop/serialization/saved-data).

Migración obligatoria:

1. Leer `theslave_registro.json` existente y conservar una copia antes de migrar.
2. Transformar la ficha antigua en un vínculo equivalente, incluyendo muertos.
3. Mantener UUID, dueño y nombre de entidades ya existentes; no recrearlas en masa.
4. Guardar `schemaVersion` y una marca de migración finalizada.
5. Si hay conflictos o corrupción, informar y conservar los datos: no vaciar el registro ni regalar plazas silenciosamente.
6. Los contratos antiguos sin datos nuevos se convierten mediante una política explícita de migración; no siguen invocando entidades sin pasar por el nuevo sistema.

El `Estado` actual del esclavo se guarda como ordinal. No insertar estados en medio del enum sin migrar los ordinales anteriores. Preferir nombres estables para datos nuevos y probar la lectura de partidas antiguas.

Al desaparecer una entidad del seguimiento por descarga no se marca como muerta. La muerte la confirma el servidor mediante su flujo de daño/muerte; una reconciliación distingue descarga, traslado, muerte y sustitución por fusión.

## 5. Almas y receta de la brújula

Conectar la captura a un evento de muerte confirmado en servidor —por ejemplo `ServerLivingEntityEvents.AFTER_DEATH`, comprobando su firma en la API local— y verificar la atribución directa al jugador. Clasificar entidades mediante tags de datos por tipo de alma.

- Zombis: hambrientas; esqueletos: vigilantes; arañas: acechantes.
- Una muerte válida concede un eco, no uno por cada relicario del inventario.
- Solo se captura con un relicario disponible y capacidad; no se generan objetos de alma en el suelo.
- Excluir aldeanos, animales, esclavos y actores de ritual. Definir expresamente variantes admitidas en los tags.
- Validar los tres contadores y el total al cargar componentes y al recibir operaciones.

El relicario se fabrica sin forma con tres bloques de vidrio y un lingote de oro; también lo vende el Notario por un oro. Capacidad: 64 almas totales.

La Brújula de Ceniza necesita una receta personalizada: la casilla de Alma Vigilante acepta un relicario con al menos una vigilante. El resultado consume una sola alma y devuelve el relicario con todas las demás y sus componentes conservados. No basta una receta JSON que ignore sus contadores. Implementar y probar el resto de fabricación usando las interfaces de receta de 26.3. Probar también fabricación repetida con shift, inventario lleno y dos relicarios distintos.

La cuadrícula autorizada es:

```text
Cuarzo       Hierro       Cuarzo
Hierro       Redstone     Hierro
Netherrack   Hierro       Relicario con ≥1 Alma Vigilante
```

## 6. Comercio: una operación, tres objetos

Compra de adquisición = **Contrato de Condena + Ofrenda a la Muerte asociada + látigo nuevo**. El contrato y la ofrenda comparten identidad de pacto; el látigo mantiene su uso normal. No se aumenta el precio por incluirlo.

| Plaza | Minerales | Almas de cada tipo | Total de almas |
|---|---|---:|---:|
| Primera | 16 hierros + 4 oros | 2 | 6 |
| Segunda | 32 hierros + 8 oros + 2 diamantes | 8 | 24 |
| Tercera | 64 hierros + 16 oros + 6 diamantes | 14 | 42 |

Los contratos no exigen esmeraldas ni cobre. Las almas pasan del relicario a la ofrenda: La Muerte las recibe durante el ritual, sin volver a cobrarlas. El grado se fija al comprar y la reserva ocupa plaza inmediatamente. Solo un contrato pendiente por jugador.

Implementar la interfaz del Notario como menú validado por servidor, no como una pantalla que envíe un precio elegido por el cliente. Un comercio vanilla simple no resuelve por sí solo tres salidas, pago desde componentes, identidad de pacto y plazas: reutilizar infraestructura de menús, con una operación propia de compra.

Orden de la operación:

1. Validar interacción real con el Notario, distancia, dimensión y menú abierto.
2. Calcular precio desde el registro del servidor; volver a comprobarlo al confirmar.
3. Preparar una copia del inventario y simular cobro/entrega de los tres objetos.
4. Comprobar espacio para todos; no tirar el excedente al suelo.
5. Persistir identidad de operación y reserva antes de confirmar sus efectos; reconciliarla al recuperar.
6. Aplicar el cambio de inventario una vez y devolver estado confirmado al cliente.

Una petición repetida con la misma identidad no vuelve a cobrar ni entrega otro conjunto. Dos clics rápidos no pueden comprar dos primeras plazas. La devolución exige el contrato y la ofrenda intactos, además del látigo de esa compra sin usar; reembolsa el pago original, no el precio actual, y respeta capacidad del relicario.

Los precios de otros rituales siguen siendo los del lore:

| Compra | Minerales | Almas de cada tipo |
|---|---|---:|
| Tributo de la Condena | 4 oros + 1 esmeralda | 1 |
| Sello de Almas | 16 oros + 8 esmeraldas + 3 diamantes | 3 |
| Tributo de la Doble Condena | 16 oros + 6 esmeraldas + 1 diamante | 2 |

La eliminación de esmeraldas se acordó para los contratos de adquisición; el lore todavía las conserva en estos tres objetos. No extender ese cambio sin actualizar ambos documentos.

## 7. Altar y control de rituales

Crear un bloque orientable `altar_ceniza`, de unos tres cuartos de altura, con colisión apropiada, cuenco integrado y cuatro velas en el modelo. Usar blockstate para orientación y estado básico; una `BlockEntity` para identidad y estado de la sesión. Las velas no se colocan manualmente.

Controlador recomendado: cuatro definiciones de secuencia que comparten validación y recuperación, pero tienen hitos distintos. No desarrollar un lenguaje de scripting completo.

```text
VALIDAR → PREPARAR/CUSTODIAR → ANIMAR → CONFIRMAR → LIMPIAR
                              ↘ CANCELAR/RESTITUIR → LIMPIAR
```

Cada sesión guarda `ritualId`, tipo, dueño, dimensión, altar/orientación, participantes, difunto cuando corresponda, objetos en custodia, tiempo transcurrido, transformaciones iniciales, resultado esperado y estado de confirmación.

Bloquear por altar, dueño y participantes: una entidad no participa simultáneamente en dos altares. Identificar posiciones con dimensión más `BlockPos`, no solo `pos.asLong()`.

### 7.1. Requisitos y reserva de espacio

Validar en servidor antes de custodiar: Overworld, noche, dueño presente, objetos correctos, plazas y participantes. El código actual considera noche el intervalo 13.000–23.000 del reloj de 24.000; reutilizar esa convención comprobando `getOverworldClockTime()` de 26.3. Si amanece después de iniciar, no cancelar por la hora.

El frontal del altar fija su sistema local de coordenadas: cuerpo nuevo delante, portal detrás y participantes a izquierda/derecha. Transformar esas posiciones según orientación. Comprobar colisiones, suelo seguro, altura, acceso al portal y espacio para el alma y el cuerpo recuperado delante del altar. Para fusión, comprobar la caja del cuerpo con una elevación de medio bloque; las dimensiones del modelo visual no sustituyen las cajas reales.

No romper bloques para abrir el portal ni teletransportar esclavos a través de paredes. Si falla una condición, informar y no consumir. Los observadores están permitidos: la antigua condición de soledad no pertenece al nuevo diseño.

### 7.2. Custodia, confirmación y reinicios

Durante la escena, custodiar los objetos originales o mantener un bloqueo equivalente validado en servidor. Si se retiran del inventario, conservar el `ItemStack` completo y sus componentes, no solo el nombre o un bit.

La apariencia de entregar una ofrenda no es el momento de cobrar definitivamente. El consumo, el cambio de plazas y la creación/sustitución se confirman juntos al final. Tras una cancelación previa, devolver los originales una sola vez. Si no hay espacio o el dueño está desconectado, guardar una restitución pendiente; no perderlos ni dejarlos duplicados en el suelo.

Minecraft guarda jugadores, chunks y datos globales por separado. **Estar en el hilo del servidor o llamar a `setDirty()` no garantiza atomicidad frente a una caída.** Registrar una operación persistente con estados `PREPARADA`, `CONFIRMANDO`, `CONFIRMADA`/`CANCELADA`, IDs de resultado estables y evidencia suficiente para reconciliar objetos y entidades.

Recuperación por defecto: cancelar una sesión no confirmada y restituir; para una confirmación iniciada, completar de forma idempotente según su registro. Un resultado ya creado con el UUID previsto no se crea de nuevo. Probar caídas en cada frontera de guardado, no solo un cierre limpio. No declarar protección completa contra duplicación hasta pasar esas pruebas.

Al cancelar, limpiar portal, velas, sonidos, cadenas y bloqueos; restaurar gravedad, navegación y postura. No restaurar una copia de salud que deshaga daño legítimo recibido durante la escena. Si un participante muere realmente, cancelar la fusión y procesar esa muerte una sola vez.

## 8. Tiempos, red y observadores

| Ritual | Duración | Ticks nominales a 20 TPS |
|---|---:|---:|
| Invocación | 12 s | 240 |
| Súplica normal | 20 s | 400 |
| Fusión | 18 s | 360 |
| Súplica por fusionado | 24 s | 480 |

El servidor gobierna los hitos. A menos TPS la simulación tarda más tiempo real: no prometer duración real fija bajo lag ni usar FPS para decidir el resultado. El cliente interpola posiciones y poses con tiempo parcial; no cambia el progreso de autoridad.

Propuesta de mensajes: solicitud de inicio con IDs seleccionados; instantánea de sesión; corrección de fase/tiempo; cancelación y cierre. Una instantánea contiene ID, tipo, fase, tiempo base, orientación, anclas, UUID de participantes y semilla visual. Los receptores verifican límites y ejecutan las mutaciones en el hilo adecuado, siguiendo el patrón local de `ctx.client().execute`/`context.server().execute`.

Enviar a dueño y observadores que siguen la zona, no solo a quien abre una pantalla. Al comenzar a observar una sesión activa se manda su estado actual: no se repite el pago, el portal desde cero ni los sonidos ya pasados. Deduplicar hitos por `(ritualId, cueId)`. El cierre siempre es explícito y un timeout solo limpia presentación, nunca decide el resultado. [Fabric: Networking](https://docs.fabricmc.net/develop/networking).

## 9. Seguirme, esperar y detectar a los participantes

Añadir estados/objetivos de IA que respeten los gestos existentes. Consultar `mobInteract`, `gesto` y la mochila antes de fijar botones; no reasignar shift con mano vacía sin revisar su función actual.

`Seguirme` utiliza navegación hacia el dueño, distancia cómoda y actualización espaciada del camino. Dos seguidores buscan posiciones ligeramente distintas. `Esperar aquí` guarda ubicación. Sin dueño conectado, se detiene con seguridad. Sin atajos mediante lava o caídas. Interrumpir un trabajo requiere el aviso previsto, conservando mochila y datos del encargo.

Al usar el Sello de Almas en el altar, buscar en un radio inicial de seis bloques y filtrar dueño, vivo, normal, estado disponible y ruta. Dos candidatos se muestran automáticamente; con tres, elegir dos de forma explícita. Revalidar UUID y condiciones al confirmar.

Antes de animar, ambos caminan a sus anclas. Un camino bloqueado no se resuelve con teletransporte. Durante la escena, una marca de participación suspende IA de trabajo y seguimiento; al cancelar se restaura el estado previo válido.

## 10. Cadenas al cuello y suspensión

Dos cadenas salen de anclas laterales del altar, cierran collares alrededor del cuello y elevan a los participantes aproximadamente medio bloque. No se compran cadenas y no se usa el sistema de correa como autoridad del ritual.

Implementar una curva corta entre el ancla y el cuello, con eslabones o segmentos repetidos. Al inicio sigue el suelo; después se tensa y levanta el collar. Una curva paramétrica con una flecha controlada basta: no hace falta un solver físico de cadenas.

El cuello se calcula desde la pose interpolada del humanoide y su escala, no desde `blockPosition()`. Dibujar el collar fuera del volumen del cuello, siguiendo la cabeza/torso para evitar que lo atraviese. Validar también la vista lateral y posterior.

Separar dos mecanismos:

- **Servidor:** marca ritual, bloquea desplazamiento voluntario y dirige la elevación segura de la entidad; guarda posición y propiedades previas.
- **Cliente:** interpola eslabones, collar, brazos y respiración usando el progreso autorizado.

No mover un metro a la entidad solo en cliente mientras el servidor la deja caminando. Tampoco activar daño de ahogamiento o caída por esta suspensión. Las cadenas no capturan jugadores ni entidades no seleccionadas. Al finalizar, apoyar al fusionado en suelo comprobado antes de retirar collares; al cancelar, liberar a ambos aunque el dueño se desconecte.

## 11. Portal, La Muerte y render moderno

El portal es geometría temporal detrás del altar: centro negro opaco, contorno violeta mate, filamentos escasos y profundidad sugerida mediante capas. No usar bloques de portal del Nether ni crear una dimensión. No transporta entidades.

Primera implementación recomendada: superficie negra con profundidad correcta, dos o tres capas de borde y distorsión limitada a su material. La Muerte recorre una trayectoria corta de dentro a fuera, con máscara de revelado/clipping donde sea necesario para que no aparezca entera antes de cruzar. El fondo negro debe ocultar el paisaje; un quad transparente violeta no basta. Probar ambas caras, ángulos oblicuos y orden de profundidad.

Reutilizar `LaMuerteEntity` para posición, invulnerabilidad y presencia de mundo; el controlador dirige su movimiento sin IA libre. El registro actual usa `.noSave()`: no confiar en esa entidad para persistir el rito. Después de una recuperación se recrea desde una sesión válida o se elimina como actor huérfano.

Para el altar, extraer datos a un estado de render y enviar geometría/modelos desde el renderer correspondiente. Evitar leer y mutar entidades durante la fase de dibujo. Fabric documenta la separación de preparación y render y la asociación de block entities con renderers; contrastar sus firmas con la versión local. [Fabric: Block Entity Renderers](https://docs.fabricmc.net/develop/blocks/block-entity-renderer).

El bounding box visual abarca portal, cadenas y cuerpos, no solo el bloque del altar: de otro modo desaparecerán por culling al sacar el altar del centro de la pantalla. Reducir detalle a distancia y evitar luces dinámicas obligatorias. Un pack de shaders puede mejorar el acabado, pero no es una dependencia.

## 12. Mitades corporales y aptitudes

Guardar las aptitudes sorteadas una sola vez, con presupuesto equilibrado y versión de fórmula. Representarlas como valores acotados; hambre, velocidad y recuperación nunca aceptan multiplicadores negativos o cero accidental. Aplicar la recuperación al planificador de trabajos, no únicamente a una animación.

La previsualización de fusión y el resultado utilizan **la misma función pura**. Mejor especialidad de cada progenitor y resto equilibrado, con límites explícitos; el algoritmo definitivo debe concretarse y probarse antes de ofrecer compras. Si se necesita desempate, derivarlo de IDs estables para que cerrar y abrir la pantalla no cambie el resultado.

Para el cuerpo, reutilizar el humanoide y dividir realmente sus piezas centrales por el plano vertical: media cabeza y medio torso; brazo/pierna de cada lado proceden del progenitor correspondiente. No basta cortar por la mitad horizontal una PNG de skin: sus caras UV están distribuidas por el atlas.

Con las tres skins existentes se pueden precalcular nueve combinaciones izquierda/derecha, o renderizar submallas con UV correctas de cada skin. Guardar IDs de apariencia parental, no una textura temporal del cliente. La costura es una capa fina, apagada y con relieve mínimo. El mismo resultado debe verse en la previsualización, durante la unión y después de reiniciar.

Durante la ceremonia, ocultar las mitades descartadas solo en presentación. Los originales no mueren mediante daño y no sueltan inventario al ser sustituidos. Definir traslado de equipo y mochila sin perder excedentes: bloquear el inicio hasta poder alojarlos o custodiar el sobrante para su entrega persistente al dueño. No destruirlos ni duplicarlos por el aspecto elegido.

## 13. Tumbas y restauración de identidad

La tumba referencia un vínculo muerto y conserva su instantánea: nombre, aptitudes, apariencia, fusión/progenitores y versión. Registrar la muerte una sola vez. La entidad recuperada puede necesitar un UUID de entidad nuevo, pero conserva el ID lógico del esclavo; no volver a sortearlo.

Los objetos perdidos al morir no regresan automáticamente con la resurrección. La instantánea usada para recuperar el cuerpo debe excluir equipo y carga ya soltados. Diferenciar esa regla del traslado de mochila durante una fusión de dos vivos.

Sustituir custodia de bits contrato/manzana por tributos completos. Retirar la exigencia de antorchas físicas y de ausencia de observadores. La acción de la tumba es «Recoger alma» y entrega un objeto ligado a dueño, vínculo, generación y token, con recibo durable de extracción. La súplica se inicia después en el altar, llevando alma y tributo, de noche en el Overworld. Un tributo normal no sirve para un fusionado. Al confirmar el retorno, el hogar remoto recupera su estado de lecho. Las lápidas antiguas conservan una vía de extracción compatible.

## 14. Enclaves y generación del mundo

Modelar las tiendas como estructuras persistentes con plantillas y una colocación validada. Propuestas de variantes: cueva del Overworld, superficie oculta, Casa bajo las Raíces de bosque oscuro y refugio exterior del Nether.

Los JAR locales contienen `Structure`, `Structure.findGenerationPoint` y `StructureTemplateManager`. Usar esos mecanismos o sus equivalentes de 26.3 para plantillas y posiciones; registrar el tipo/codec propio cuando las comprobaciones de terreno excedan una colocación JSON simple. No copiar una API de jigsaw de otra versión sin compilarla.

Recursos previstos: `data/theslave/structure/`, definiciones `worldgen/structure/`, `worldgen/structure_set/` y tags de biomas. Confirmar nombres y codecs con las estructuras vanilla de 26.3. El espaciado por cuadrícula reduce densidad pero no demuestra por sí solo todas las distancias entre variantes: comprobar separación global de enclaves cuando el requisito sea estricto.

Validaciones de emplazamiento:

- Overworld subterráneo: cámara lateral con entrada conectada a una cueva; no una habitación sellada.
- Superficie: ocultación por vegetación/relieve y edificio bajo, sin balizas visibles.
- Bosque oscuro: buscar `minecraft:dark_forest` fuera de la exclusión. Una etiqueta de bioma hace posible la variante, pero no garantiza que exista cerca; la búsqueda prioritaria necesita un proceso acotado y persistente.
- Nether: plataforma exterior con volumen abierto y acceso seguro, resguardada por relieve. No usar una comprobación de luz solar del Overworld para identificar «exterior» del Nether.
- Distancias iniciales del lore: unos 2.000 bloques entre tiendas del Overworld, 1.000 en Nether y exclusión de 1.500 alrededor del spawn principal del Overworld.

Las exclusiones de spawn no son una función automática de `structure_set`. Resolverlas expresamente y no acceder al mundo vivo desde un worker de generación. Si se utiliza una instantánea de spawn/protecciones, debe ser inmutable y estar disponible de forma segura para el proceso. Los puntos de reaparición posteriores no desplazan tiendas existentes.

Guardar un índice de enclaves realmente verificados con dimensión, entrada, orientación, variante y UUID del Notario. La generación puede evaluarse varias veces: colocar o restaurar exactamente un mercader por enclave. No generar otro en cada carga del chunk.

Para mundos ya creados, generar por defecto en terreno nuevo. Cualquier colocación retroactiva requiere un modo separado que compruebe construcciones/protecciones; no sobrescribir bases para cumplir una cuota.

La búsqueda prioritaria del bosque usa un presupuesto por ejecución y persiste su progreso. Si el bioma no existe dentro del radio actual, ampliarlo en pasos; no bloquear el tick ni afirmar que se ha garantizado una ubicación todavía no encontrada.

## 15. Brújula y búsqueda eficiente

Apuntar al **acceso verificado** del enclave en la dimensión actual. Conservar destino en un componente y sincronizarlo; el cliente solo calcula la aguja y los pulsos a partir de esa información.

`LodestoneTracker` existe en 26.3, pero una brújula personalizada necesita comprobar sus propiedades de modelo. Si se reutiliza seguimiento vanilla, no permitir que la falta de una magnetita real invalide el acceso elegido; si no encaja, usar un componente y una propiedad visual propios. No usar `getNearestPlayer` ni el movimiento del Notario como destino.

Buscar al usarla o en intervalos acotados, con caché. No ejecutar una búsqueda de estructuras ni generar chunks cada frame. El índice de enclaves cargados no descubre por sí solo tiendas en terreno sin explorar: combinarlo con la localización de estructuras de la versión objetivo y un trabajo de verificación limitado. No apuntar a una posición candidata hasta confirmar que existe una entrada válida. Mientras busca, mostrar estado de búsqueda o «Sin rastro».

La indicación arriba/abajo se obtiene comparando la altura del jugador con la entrada; el pulso se basa en distancia. Probar la aguja en ambas manos, inventario y cambio de dimensión. No transformar coordenadas entre dimensiones como sustituto de buscar un enclave de la dimensión actual.

## 16. Bloques infectados y decoración

Registrar bloques propios y sus items solo cuando sean recuperables. Terreno inicial: piedra, pizarra profunda, tierra, césped, grava, arena, netherrack y piedra negra infectadas. Decoración: raíz, raicillas, capa de ceniza, velas, urnas, archivo, mesa, lámpara y placa del Registro.

Usar modelos y blockstates estáticos para la mayoría. Capas para ceniza; orientación para archivos y mesa; variantes ponderadas de texturas para romper repetición. Arena/grava de la estructura necesitan soporte o una implementación estable deliberada. Añadir tags de herramienta y loot tables coherentes.

Las grietas moradas son principalmente píxeles en la textura. Reservar animación para nudos y raíz con `.png.mcmeta` o renderer específico. Una textura clara no crea iluminación dinámica; comprobar el resultado con luz normal y escoger pocos emisores de nivel bajo. No una block entity con ticker por cada bloque infectado.

Aplicar la infección con procesadores o una pasada de estructura limitada a terreno permitido. Excluir minerales, contenedores y construcciones ajenas. La distancia 12–18 del lore es una huella irregular adaptada al terreno, no una esfera de sustitución. Nunca pintar superficie sobre una tienda oculta en cueva.

No hay propagación por random tick, daño al caminar, almas al romper bloques ni requisitos de recolectar decoración para progresar. El inventario de una urna o el archivo es narrativo: no materializar recompensas al romperlos.

## 17. Arte, partículas y cámara

Pixel art 16×16 o 32×32 para items y bloques; modelos de geometría sencilla con buena silueta. Elaborar altar, Notario y La Muerte en Blockbench o herramientas equivalentes y conservar fuentes editables. El proyecto actual genera assets mediante [generate_assets.py](../tools/generate_assets.py): ampliar solo lo necesario y evitar regenerar skins ajenas de forma accidental.

Preservar los colores cerrados en el lore: carbón `#121016`, ceniza `#77717A`, hueso `#B5AB96`, violeta amoratado `#493153`, azul grisáceo `#9AAFB8`. El miedo depende de contraste localizado y ritmo, no de hacer ilegible la escena.

Partículas propias solo donde las de vanilla no sirvan: ceniza que se hunde o asciende, llama del umbral y un hilo de alma breve. Hacer la aleatoriedad visual determinista por sesión. Limitar emisores y evitar un envío de partículas por eslabón y tick; el cliente puede generarlas desde las anclas sincronizadas.

Retirar la modificación aleatoria directa de yaw/pitch de `RitualOverlay`. No forzar la mirada hacia el portal. Subtítulos, viñeta y mezcla deben ser por jugador, restaurarse al cancelar/desconectar y respetar reducción de movimiento y volumen. Los espectadores tienen una escena espacial, no una pantalla que los inmovilice.

## 18. Audio: recursos locales y sincronización

El juego utiliza archivos aprobados dentro del mod o su pack de recursos. No consulta ElevenLabs durante un ritual. Esto evita latencia de generación, gastos por cada uso y dependencia de Internet al jugar.

Registrar eventos en el código y archivos en `assets/theslave/sounds/`, con `sounds.json` y traducciones de subtítulos. Minecraft utiliza OGG Vorbis para estos recursos. Preparar efectos posicionados en mono; reservar estéreo para capas ambientales no localizadas y probar su reproducción. [Fabric: Creating Custom Sounds](https://docs.fabricmc.net/develop/sounds/custom).

Catálogo propuesto de cues, con variantes breves donde convenga:

| Cue | Fuente espacial | Contenido |
|---|---|---|
| `ritual.candle_ignite` | Vela | Mecha seca, fuego estrecho |
| `ritual.ash_pull` | Cuenco | Ceniza aspirada y roce granular |
| `ritual.portal_open` | Portal | Tensión, desgarro lento y golpe grave |
| `ritual.death_step` | La Muerte | Paso pesado y borde de túnica |
| `ritual.soul_payment` | Ofrenda/mano | Aire aspirado y murmullos interrumpidos |
| `ritual.contract_seal` | Contrato | Golpe de firma |
| `ritual.grave_knock` | Tumba | Golpe amortiguado desde debajo |
| `ritual.chain_close` | Cada cuello | Cierre metálico seco |
| `ritual.chain_tension` | Cadenas | Eslabones tensándose, sin bucle molesto |
| `ritual.suture` | Aguja | Hilo, tela y puntada breve |
| `ritual.first_breath` | Cuerpo | Primera inspiración |
| `ritual.portal_close` | Portal | Contracción o costura, según ceremonia |
| `death.voice.*` | La Muerte | Frases cerradas del lore |

Voces iniciales: «Queda escrito», «La deuda permanece», «Dos nombres. Un cuerpo» y «Una vida recuerda su camino. Esta necesita recordar dos». El nombre elegido por el jugador se muestra por subtítulos/UI; no exige generar TTS nuevo para cada nombre.

Elegir una sola autoridad de reproducción por cue: servidor o controlador cliente de sesión. No enviar un `playSound` de servidor y reproducir el mismo cue otra vez desde el HUD. Guardar qué hitos ya se oyeron; quien llega tarde no recibe de golpe todos los anteriores. Parar sonidos en bucle por instancia al cancelar.

La música, si se usa, es una capa ambiental discreta e instrumental. No debe ocupar toda la duración con un tema épico. La reducción del ambiente es un crossfade local reversible que mantiene avisos y amenazas audibles; no modifica permanentemente los ajustes del usuario.

## 19. Producción con ElevenLabs

El usuario ha ofrecido habilitar una API de ElevenLabs para voces, efectos y música. Es una posibilidad de producción: actualmente no se ha proporcionado ni usado una clave y esta guía no ejecuta solicitudes ni genera gastos.

Preparar, cuando esté disponible, una herramienta de desarrollo separada, por ejemplo `tools/audio/generate_audio.py`, con un manifiesto de trabajos. Esa ruta es propuesta, no un script existente. La clave se obtiene de una variable local como `ELEVENLABS_API_KEY`; nunca se incluye en Java, el JAR, un manifiesto versionado o un log. El servidor y los jugadores no necesitan la clave.

### 19.1. Capacidades verificadas y elección de servicio

| Trabajo | API oficial | Uso propuesto |
|---|---|---|
| Efectos | `POST /v1/sound-generation` | Mechas, cadenas, tumba, portal, pasos y sutura |
| Voz | `POST /v1/text-to-speech/{voice_id}` | Frases de La Muerte en español con una voz consistente |
| Música | `POST /v1/music` | Fondo instrumental mínimo, si mejora la escena |

La API de efectos acepta descripción, duración y opción de bucle. Generar piezas aisladas y montarlas en el juego, en lugar de pedir una banda sonora completa que obligue a reproducirla siempre igual. [ElevenLabs: Create sound effect](https://elevenlabs.io/docs/api-reference/text-to-sound-effects/convert).

Para voz, fijar `voice_id` y el modelo disponible que se pruebe con español; conservar una toma seca antes del tratamiento. La API recibe texto y devuelve audio de la voz elegida. No prometer una voz o un modelo concreto sin comprobar su disponibilidad en la cuenta. [ElevenLabs: Create speech](https://elevenlabs.io/docs/api-reference/text-to-speech/convert).

La API de música admite composición mediante prompt o plan y una opción instrumental. Su disponibilidad, formatos y costes se comprueban con la cuenta habilitada al producir, sin fijar precios de API en este diseño. [ElevenLabs: Compose music](https://elevenlabs.io/docs/api-reference/music/compose).

### 19.2. Manifiesto de producción y flujo

Cada entrada debe registrar cue, texto o prompt, duración objetivo, variantes, idioma, voz/modelo, formato solicitado, salida, parámetros, estado aprobado y procedencia. Mantener originales y masters fuera de los recursos finales; empaquetar solo tomas aprobadas.

1. Producir primero un lote de prueba pequeño: portal, cierre de cadena, puntada y una frase.
2. Escuchar los resultados y escoger el carácter de La Muerte antes de producir todas las voces.
3. Generar variantes cortas; conservar silencios y colas controladas.
4. Editar: recortar silencio excesivo, retirar clics, ajustar niveles y separar toma seca de capa susurrada/reverberación.
5. Convertir a OGG Vorbis con una herramienta disponible —FFmpeg o equivalente— sin cambiar simplemente la extensión de un MP3. Verificar canales, duración y ausencia de clipping.
6. Asociar archivos a cues y subtítulos, probar dentro del juego y registrar la toma elegida.

Ejemplo de prompt de efecto original:

> Heavy blackened iron chain dragged slowly across rough stone, two distinct links locking shut, close perspective, dry detailed metal, restrained low resonance, no voices, no music, no explosive impact.

Dirección de voz original:

> Voz grave y serena en español, ritmo lento pero inteligible, autoridad antigua, sin gritar; frase corta con una pausa natural antes de la última palabra.

Los prompts son instrucciones de producción, no garantías sobre el resultado. Añadir una capa susurrada y reverberación discreta en edición solo si la frase sigue siendo comprensible. No estirar una frase breve hasta que deje de caber en su hito.

Usar límites de lote, reintentos acotados, caché por contenido y un modo de simulación que liste trabajos sin llamar a la API. Guardar el ID de petición para no repetir generaciones después de un timeout sin comprobar el resultado. Comprobar permiso de distribución de las tomas y registrar su procedencia antes de incluirlas en una release.

## 20. Ruta de implementación

Las etapas permiten comprobar dependencias, pero el lanzamiento acordado contiene todas las mecánicas.

1. **Migración y registro:** tres vínculos, reservas y operaciones idempotentes; pruebas con mundos existentes.
2. **Objetos y comercio:** componentes, relicario, receta de brújula, contrato/ofrenda/látigo y menús validados.
3. **Altar y seguimiento:** orientación, anclas, detección de dos participantes y cancelación segura.
4. **Cuatro resultados funcionales:** invocar, suplicar normal, fusionar y suplicar fusionado con presentación mínima verificable.
5. **Enclaves:** estructuras, ocultación, mercader, índice persistente y búsqueda útil de brújula.
6. **Producción visual:** bloques infectados, decoración, cuerpos por mitades, cadenas, portal, velas y entrada física de La Muerte.
7. **Audio:** tomas propias, posible producción ElevenLabs, mezcla espacial y subtítulos.
8. **Validación completa:** servidor dedicado, varios clientes, reinicios y rendimiento; empaquetado final con assets aprobados.

No dar por terminada una etapa visual si solo existe una descripción o una captura externa. No dar por implementado el lore porque el mod compile.

## 21. Pruebas y criterios de aceptación

| Área | Comprobación necesaria |
|---|---|
| Compra | Cada grado cobra 2/8/14 almas de cada tipo y sus minerales; entrega contrato, ofrenda y látigo completo |
| Límite | Vivo en chunk descargado, muerto y reservado cuentan; cuarta plaza rechazada |
| Duplicaciones | Doble clic, paquete repetido, dos tiendas y reinicio no duplican compra, devolución o ritual |
| Receta | Se consume una vigilante y vuelve el mismo relicario con las demás almas; probar shift e inventario lleno |
| Identidad | Ofrenda de otro contrato/dueño y tributo de otro difunto se rechazan |
| Noche | Los cuatro rituales rechazan inicio diurno; terminar tras amanecer sigue siendo válido |
| Espacio | Portal detrás en cuatro orientaciones, sin atravesar paredes; techo bajo impide suspensión antes de cobrar |
| Seguimiento | Dos y tres seguidores, caminos bloqueados, lava, desconexión y conservación de mochila |
| Fusión | Dos candidatos automáticos, tres requieren elección; no afecta al tercero ni a ajenos |
| Cadenas | Collar sigue cuello en todas las vistas; elevación de medio bloque; cero daño ritual y liberación tras cancelar |
| Herencia | Previsualización igual al resultado guardado; límites de aptitudes y ausencia de reroll |
| Muerte | Tumba conserva identidad; resucitar no duplica equipo soltado ni sortea nuevas aptitudes |
| Persistencia | Caída antes/durante/después de confirmar; reconciliación de UUID y objetos sin pérdida ni duplicado |
| Multijugador | Dueño y observadores ven el mismo rito; entrada tardía y latencia no repiten audio o efectos |
| Mundo | Semillas múltiples; tiendas escasas y escondidas; Nether exterior; Casa bajo las Raíces en bioma correcto |
| Brújula | Acceso real, dimensión correcta, arriba/abajo, búsqueda acotada y ausencia de destino ficticio |
| Recursos | JSON/codecs válidos, texturas pixeladas, loot/tags correctos, OGG reproducibles y subtítulos |
| Rendimiento | Comparar reposo y varias ceremonias; vigilar MSPT, FPS, asignaciones y partículas antes de fijar límites |
| Compatibilidad | Cliente y servidor dedicado arrancan sin cargar clases de render en entorno servidor |

Reutilizar `gradlew build` y las pruebas actuales `verifyWhip`; añadir pruebas puras para precios, contadores, herencia y estados, e integración/GameTests de la versión objetivo para inventarios, altar y persistencia. Las pruebas del látigo no validan los rituales nuevos.

Para comprobaciones permitidas en este equipo:

```powershell
.\gradlew.bat --offline --no-daemon build previewLore
python -I -m unittest discover -s tools -p 'test_*assets.py' -v
```

No ejecutar `runClient`, `runServer` ni aceptar una EULA en esta maquina. Las pruebas dentro de Minecraft y el arranque real del servidor dedicado necesitan otro entorno autorizado; no se consideran superadas por compilar. No usar una partida de produccion para ensayos de migracion o fallos.

En esta máquina se observó un fallo de sockets Unix de Java al arrancar Gradle. Se resolvió en la sesión utilizando un directorio temporal corto mediante `-Djdk.net.unixdomain.tmpdir=...` en `JAVA_TOOL_OPTIONS`. Es una solución local de entorno, no una propiedad que deba imponerse a todos los usuarios ni incluirse en el JAR.

## 22. Referencias y mantenimiento

- [Lore funcional cerrado](new_lore.md): autoridad sobre nombres, precios, rituales y tono.
- [Fabric: Networking](https://docs.fabricmc.net/develop/networking).
- [Fabric: Custom Data Components](https://docs.fabricmc.net/develop/items/custom-data-components).
- [Fabric: Saved Data](https://docs.fabricmc.net/develop/serialization/saved-data).
- [Fabric: Block Entity Renderers](https://docs.fabricmc.net/develop/blocks/block-entity-renderer).
- [Fabric: Creating Custom Sounds](https://docs.fabricmc.net/develop/sounds/custom).
- [ElevenLabs: Sound Effects](https://elevenlabs.io/docs/api-reference/text-to-sound-effects/convert).
- [ElevenLabs: Text to Speech](https://elevenlabs.io/docs/api-reference/text-to-speech/convert).
- [ElevenLabs: Music](https://elevenlabs.io/docs/api-reference/music/compose).

Las páginas web se consultaron el 5 de noviembre de 2026. Las firmas concretas se verifican contra las dependencias locales de 26.3 al implementar. Si una limitación técnica obliga a cambiar una regla de juego, actualizar primero el lore y esta guía de forma coherente; no introducir una mecánica distinta silenciosamente.
