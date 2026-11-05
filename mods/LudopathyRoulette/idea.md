# LudopathyRoulette — diseño funcional y técnico

> Dos máquinas de azar para Logitanos: una rueda vertical con aguja y una ruleta horizontal con bola. El jugador conoce el precio, los premios y los castigos antes de girar. La caja tiene una pantalla frontal para jugar y una puerta lateral con candado para administrarla.

**Estado:** diseño de referencia con implementación 1.0.0 y pruebas automatizadas; validación dentro del juego pendiente. **Revisión:** 5 de noviembre de 2026.

La entrega compilada se prepara en `jar/LudopathyRoulette/`. [README.md](README.md) describe el comportamiento implementado y [VALIDACION.md](VALIDACION.md) distingue comprobaciones ejecutadas, diferencias respecto al diseño y pruebas pendientes. No se ha instalado ni ejecutado Minecraft para preparar esta entrega.

Base local comprobada en `mods/TheBall/gradle.properties`: Minecraft 26.3, Fabric Loader 0.20.1 y Fabric API 0.165.0+26.3. Java 25 es la base prevista. TheBall separa fuentes comunes y cliente y sus renderizadores utilizan `SubmitNodeCollector`. Esto orienta la integración; no demuestra que todas las APIs propuestas sean compatibles. M0 verificará las firmas reales. Los esquemas siguientes son contratos de diseño, no Java listo para compilar.

## 1. Experiencia y alcance

La máquina debe sentirse como un objeto del mundo: pantalla encendida, rueda con volumen, sonido mecánico y resultado visible para espectadores. El gesto da personalidad al giro; las probabilidades dependen exclusivamente de la configuración publicada.

Recorrido principal:

1. Acercarse: pantalla con nombre, precio base y disponibilidad.
2. Abrir: consultar precio personal, premios, probabilidades y consecuencias.
3. Girar: arrastrar o pulsar un botón equivalente; una única petición de cobro.
4. Observar: UI y caja muestran el mismo progreso.
5. Recibir: resultado confirmado, con entrega o estado pendiente explícitos.
6. Volver a jugar: siempre mediante otra acción deliberada, sin giro automático.

| Entrega | Incluye |
|---|---|
| Prototipo | Bloque, permisos, configuración, cobro y recuperación demostrados |
| Primera versión | Ambas ruletas, premios por plantilla de staff, castigos acotados, consola, sonidos, espectadores y comandos |
| Ampliaciones | Stock finito, economía externa, historial persistente consultable y estilos visuales |

El stock finito queda fuera de v1: exigiría reservas y devolución de existencias. No mezclarlo con las plantillas descritas en 4. La primera versión funcional necesita las dos máquinas, aunque se implementen por separado.

## 2. Reglas que gobiernan el mod

- **El servidor decide resultado, precio, permisos y consecuencias.** El cliente solicita y representa.
- Al aceptar se congelan configuración, precio y resultado. Editar después no altera esa tirada.
- Repetir una petición no vuelve a cobrar. Cerrar, cambiar de dimensión o desconectarse no vuelve a sortear.
- El resultado se determina al aceptar; la entrega se intenta al terminar el tiempo servidor de la animación.
- Un rechazo no consume objetos, XP ni usos de recargo.
- Precio efectivo y probabilidades se muestran antes de confirmar. Si cambian, se exige actualizar y volver a pulsar.
- No hay compensación oculta de pérdidas ni trayectorias que busquen un «casi premio».
- Inventario, mundo y datos auxiliares no forman por sí solos una transacción de disco. No prometer entrega exactamente una vez ante cualquier caída sin demostrarla en M0/M3.

## 3. Las dos máquinas y la caja

| Propiedad | Ruleta de la Fortuna | Ruleta de Casino |
|---|---|---|
| Bloque | `ludopathyroulette:fortuna` | `ludopathyroulette:bola` |
| Geometría | Rueda vertical, aguja fija arriba | Cuenco horizontal, rotor y bola |
| Acción | Arrastrar en círculo y soltar; botón alternativo | Botón «Lanzar bola» |
| Duración normal | 4,5–9 s | 7–10 s |
| Carácter | Feria, clics de clavijas | Rodadura, caída y rebotes |

Ambos bloques comparten configuración, cobro y resolución. En v1 el tipo se elige al colocar: convertirlo desde la consola se pospone para evitar sustituciones de BlockEntity con sesiones abiertas.

### 3.1 Interacciones físicas

Cuatro orientaciones horizontales. Pantalla frontal orientada hacia quien coloca. Pantalla, rueda y cuerpo abren juego; la puerta lateral abre administración con permiso. Transformar el punto de impacto a coordenadas locales para acertar en las cuatro orientaciones. La puerta forma parte del bloque, sin entidad ni inventario separados.

Sin permiso, sonido de cerradura y «No tienes permiso para administrar esta máquina». No abrir menú ni enviar configuración privada. `requireSneak` puede exigir agacharse, sin cambiar permisos.

### 3.2 Estados visibles

| Estado | Pantalla del bloque | UI de juego |
|---|---|---|
| Sin configurar | «SIN CONFIGURAR» | Explicación, giro deshabilitado |
| Disponible | Nombre, precio base y rueda lenta | Precio personal y botón |
| Ocupada | Animación y progreso | «Está girando · espera a que termine» |
| Pausada por staff | «EN MANTENIMIENTO» | Motivo, sin cobro |
| Error de datos | «FUERA DE SERVICIO» | Motivo breve y referencia para staff |

La puerta permanece abierta visualmente mientras exista una sesión administrativa válida y se cierra al acabar la última. Ese estado no se guarda en disco. El último resultado público no expone inventario ni consecuencias privadas.

## 4. Premios, precio y probabilidades

### 4.1 Economía de v1: plantillas de staff

Las casillas contienen **plantillas**, no existencias. Cada premio genera una copia del stack configurado. La consola muestra «Premios ilimitados generados por el servidor» antes de publicar.

Los campos de objetos son slots de muestra: el administrador selecciona un stack de su inventario y el servidor copia su contenido. No se mueve ni consume el original. El paquete identifica slot de inventario y fila de destino; el servidor comprueba ambos y no acepta componentes arbitrarios del cliente. Se pueden reutilizar las plantillas guardadas sin exigir otra copia del objeto.

Cerrar, borrar una fila o romper la máquina no devuelve muestras: nunca se depositaron objetos reales. Los slots de muestra evitan depósitos temporales y devoluciones durante edición. Los permisos de administración autorizan generar estas plantillas.

El cobro se consume como sumidero: desaparecen objetos y niveles. No hay recaudación para un propietario en v1. Stock y caja de recaudación requerirán inventarios reales separados.

### 4.2 Casillas y reparto

Entre **2 y 12 casillas**, con `segmentId` estable, orden explícito y peso entero 1–1 000 000.

| Categoría | Id | Resultado |
|---|---|---|
| Gran premio | `GRAND` | Entrega un objeto y celebración configurable |
| Normal | `NORMAL` | Entrega un objeto |
| Castigo | `PUNISH` | Ejecuta un plan acotado; admite «nada» |

`COST` es el precio y no ocupa sector. Una devolución del precio puede configurarse como premio normal. No se exige una categoría concreta: un reparto extraño genera aviso de staff, sin reglas ocultas que lo transformen.

```text
RouletteSegment
  segmentId       UUID estable
  category        GRAND | NORMAL | PUNISH
  weight          entero positivo
  rewardTemplate  stack, solo para entrega de objetos
  punishment      lista validada de acciones, solo PUNISH
```

`P(i) = weight_i / sum(weight)`. Muestrear un entero uniforme en `[0, sum(weight))` y recorrer acumulados en `long`. El orden guardado es el dibujado; reordenar requiere acción explícita.

### 4.3 Edición y lectura de porcentajes

- Editor principal por pesos; porcentajes y ángulos proceden de los mismos enteros.
- Opción de reparto en porcentaje: introducir todas las filas, validar suma 100 y convertir a base 1 000 000 por restos mayores, con desempate por orden. Cada fila debe recibir peso mínimo 1; rechazar repartos que no lo permitan y explicar la precisión disponible.
- Previsualizar y confirmar la conversión; no reajustar silenciosamente otras filas mientras se escribe.
- Mostrar dos decimales y «<0,01 %» para probabilidades positivas menores. Los redondeos pueden no sumar visualmente 100,00 %; explicar que el total matemático sí es 100 %.
- Ángulos proporcionales al peso. Casillas diminutas usan indicador exterior numerado y leyenda; no ensancharlas para aparentar más probabilidad.
- Categorías con texto e icono además de color. Mostrar cantidad y características relevantes del premio sin publicar NBT privado arbitrario.

### 4.4 Precio efectivo

Un tipo de objeto, niveles de XP o ambos. `free=true` exige objeto vacío y cero niveles. Solo operador puede habilitar gratuidad; comprobarlo al guardar y al girar.

Comparar tipo y componentes exactos. Un objeto renombrado no paga un precio de objeto normal si sus componentes difieren. Contar inventario principal y barra rápida; excluir mano secundaria, armadura, cursor y contenedores anidados. Explicarlo en la ayuda del precio.

XP significa **niveles**, no puntos. M0 verificará el descuento y el comportamiento de la fracción de progreso antes de documentarlo como definitivo.

Recargos con aritmética racional o de punto fijo. Redondear hacia arriba cada componente positivo después del multiplicador total: 1 ficha y 3 niveles con ×1,5 cuestan 2 fichas y 5 niveles. Una máquina gratuita no admite `SURCHARGE`.

### 4.5 Límites iniciales

| Dato | Límite propuesto |
|---|---|
| Casillas / peso por casilla | 2–12 / 1–1 000 000 |
| Stack de premio | 1–máximo del objeto, hasta 64 |
| Plantilla serializada | 8 KiB por stack |
| Configuración completa | 128 KiB con todas las plantillas |
| Resumen público | 16 KiB; rechazar guardado si no cabe |
| Nombre / etiqueta corta | 32 / 24 caracteres |
| Acciones por castigo | 3, sin repetir tipo |
| Criaturas por resultado | 3 |
| Efectos | 8; hasta 120 s y amplificador 4, nivel V |
| Recargo | ×1,5–×10 durante 1–5 tiradas aceptadas |

Validar antes de asignar colecciones grandes, también al leer disco. No truncar objetos ni efectos silenciosamente. Medir estos límites con objetos de los mods instalados.

## 5. Castigos comprensibles y ejecutables

La muestra sugiere una acción, pero el administrador confirma su interpretación. El jugador ve el plan completo antes de pagar.

| Acción | Fuente | Consecuencia |
|---|---|---|
| `GIVE_ITEM` | Stack | Entrega el objeto |
| `EFFECT` | Poción o flecha compatible | Aplica efectos directamente, sin botella, proyectil ni nube |
| `SPAWN` | Huevo compatible | Intenta invocar criaturas cerca del jugador |
| `SURCHARGE` | Parámetros explícitos | Aumenta precios futuros en esa máquina |
| `KILL` | Elección explícita | Intento de daño letal mediante fuente propia |
| `NOTHING` | Sin muestra | Sin consecuencia |

No interpretar cualquier objeto con `entity_data` como huevo. Los objetos personalizados necesitan resolución explícita de entidad y parámetros. Un efecto de otro mod necesita aprobación en la política del servidor; reconocer su componente no demuestra compatibilidad.

### 5.1 Combinaciones y fuentes

`KILL` y `NOTHING` son exclusivos. El resto admite hasta tres acciones distintas, en orden `SPAWN`, `GIVE_ITEM`, `EFFECT`, `SURCHARGE`. Cada acción tiene fuente y parámetros propios: un huevo no sirve también como poción. Rechazar listas incompatibles.

Ejemplo: «Aparece 1 zombi y recibes lentitud I durante 15 s». La descripción se genera desde el plan validado, no desde un nombre decorativo del objeto.

### 5.2 Efectos y daño letal

- Resolver componentes al guardar y congelar el plan al aceptar; verificar registros al ejecutar.
- Los efectos instantáneos necesitan límite propio y lista permitida: limitar duración no limita daño instantáneo.
- Pociones arrojadizas y persistentes aplican efectos solo al perdedor, sin área.
- `KILL` solicita daño letal y **no garantiza muerte**: reglas o mods pueden cancelarlo. No usar eliminación que evite tótems.
- Fuente propuesta `ludopathyroulette:ruleta`; verificar etiquetas de daño, tótem, armadura y mensaje real.
- Guardar daño letal requiere confirmación adicional. Jugar esa revisión exige aceptar su aviso antes del cobro, una vez por revisión.

### 5.3 Criaturas y límites del mundo

Buscar posiciones en radio de 4 bloques alrededor del jugador, junto a la máquina y en su dimensión, con máximo 32 candidatos por resultado. Sin cargar chunks, colisiones ni bypass de protecciones. Si el jugador cambia de dimensión o se aleja más de 8 bloques de la máquina, diferir la acción hasta regresar a una ubicación válida junto a ella.

No copiar NBT completo sin control: UUID, pasajeros, dueño, equipo y datos ajenos pueden duplicar objetos o causar daños. Lista permitida de tipos y componentes; sin jefes ni entidades explosivas por defecto. No asignar dueño ni forzar objetivos por reflexión.

La IA vanilla puede atacar a espectadores o romper bloques. Por eso `SPAWN` está **desactivado por defecto** hasta verificar protecciones y adaptadores. No prometer aislamiento de terceros con IA vanilla. Al habilitarlo, mostrar aviso, etiquetar entidades con `spinId`, desactivar drops/XP y limitar permanencia a 120 s de ticks activos mediante mecanismos verificados. No mantener chunks vivos: comprobar caducidad al recargarlos.

Si no hay hueco o una protección rechaza la invocación, registrar omisión con motivo. No volver a sortear ni cobrar ni sustituir por otro castigo. Continuar las demás acciones.

### 5.4 Recargos persistentes

Guardar por `(playerUUID, machineId)` multiplicador y usos. Desconectar no elimina el recargo. Cada tirada aceptada consume un uso previo; rechazo y demostración no consumen. Un recargo ganado en esa tirada entra después y afecta a la siguiente.

Acumulación: multiplicar factores hasta ×10; usos iguales al máximo entre restantes y nuevos, hasta 5. Ejemplo: ×2 con 2 usos más ×3 con 3 usos produce ×6 durante 3 usos. Mostrar precio final y usos. Traslado autorizado conserva identidad; copia creativa recibe otra y no hereda recargos.

### 5.5 Ausencia y muerte del jugador

Premios van al buzón. `EFFECT`, `SURCHARGE` y `KILL` pendientes se presentan al volver o reaparecer, con aviso de 5 s de ticks jugables antes de aplicarse. Durante ese aviso no se inicia otra tirada; salir conserva obligación y tiempo pendiente. No ejecutar sobre la pantalla de muerte.

Un `SPAWN` diferido exige volver junto a la máquina original; la obligación bloquea nuevas tiradas del jugador. Si desaparece la máquina o se deshabilita la capacidad, registrar omisión y liberar. El resto de acciones resuelve independientemente. Reconectar no repite acciones confirmadas. Una acción ambigua tras caída se aísla según 11; no repetir daño automáticamente.

## 6. Administración y permisos

### 6.1 Acceso

Operador nivel 2 o etiqueta de scoreboard exacta `ludopathyroulette:admin`. `allowCreative=false` por defecto; al activarlo, creativo también permite administrar. Solo operador modifica política global y habilita gratuidad o acciones peligrosas.

Comprobar permiso al abrir, copiar muestras, probar, guardar, pausar y retirar. Revocarlo con menú abierto cierra sesión y descarta borrador. La etiqueta concede administración global, no propiedad de una máquina; explicarlo al staff.

Sin permiso, rechazar rotura, traslado y vaciado. Bloquear pistones y definir resistencia a explosiones para impedir bypass; demostrarlo en integración. Las plantillas no se conectan a tolvas.

### 6.2 Consola y borradores

Tres zonas: casillas, detalle seleccionado y precio/estado. Cabecera con nombre, tipo y revisión. Pie con «Probar borrador», «Descartar cambios» y «Guardar y publicar». Nueva máquina sin configurar hasta primer guardado válido.

- Fila con categoría, icono, cantidad, peso y porcentaje.
- Castigos con acciones, fuentes independientes, consecuencias y avisos específicos.
- Sección económica declara premios ilimitados y consumo del cobro.
- Errores junto al campo y resumen que enfoca el primero. Admitir coma o punto decimal en porcentajes españoles, sin separadores ambiguos.
- Cerrar con cambios pide confirmar descarte; ningún objeto queda retenido.
- Demostración local al editor, sin cobro, premios, castigos, contadores ni ocupación pública. Lleva «DEMOSTRACIÓN» visible permanentemente.

### 6.3 Concurrencia

Borrador con `editSessionId` y `expectedConfigRevision`. Validar y publicar configuración inmutable en hilo servidor. Ante conflicto, conservar borrador y ofrecer recargar o comparar; no sobrescribir automáticamente.

Publicar incrementa revisión y actualiza clientes. Un giro activo conserva instantánea visual y funcional anterior. Pausar impide nuevas tiradas y deja terminar las aceptadas.

## 7. UI de juego y accesibilidad

Rueda central y lista consultable al lado o en panel plegable. Botón «Girar · 2 fichas + 5 niveles», con recargo incluido; gratuito dice «Girar gratis». Gesto y botón usan idéntica validación.

- Estados: disponible, esperando aceptación, girando, resultado y bloqueo con motivo. Deshabilitar al enviar; reintentos reutilizan identificador.
- Arrastrar no cobra: solo soltar un gesto válido envía petición. Cancelar un arrastre no envía nada.
- Tab, flechas y Enter/Espacio para completar el flujo. Esc cierra sin cancelar tiradas aceptadas.
- Sonido normal, reducido o silenciado, guardado en opciones cliente. Subtítulos de inicio, resultado y errores relevantes.
- Movimiento reducido: sin zoom, bamboleo ni atracción; progreso discreto en vez de giro intenso, con mismo tiempo y resultado servidor.
- Ventanas pequeñas: priorizar rueda, precio, consecuencias y botón. Validar 854×480 y 1920×1080 con escalas GUI admitidas, sin ocultar avisos letales.
- Historial local: últimas 20 tiradas por máquina con coste, resultado y estado. No sustituye auditoría ni recuperación.
- Contadores públicos: tiradas resueltas y resultados por categoría. Un castigo no cuenta como «premio entregado».

Mensajes: «Premio: 3 × Pan · entregado» o «Premio: 3 × Pan · pendiente de recoger». Para castigos, describir acciones ejecutadas y omitidas. Terminar la animación no demuestra que la entrega haya sucedido.

## 8. Ciclo de una tirada

```text
READY
  validar sesión, identidad, alcance, revisión, cuota y capacidad de recuperación
  comprobar todos los componentes del precio efectivo
  congelar configuración; sortear; preparar identificador y trayectoria
  registrar operación y cobrar según protocolo de recuperación
ACCEPTED / SPINNING
  publicar SpinStart; resolver al tick servidor previsto
RESOLVING
  procesar acciones y entrega sin depender de UI ni chunk
SETTLED o PENDING_PLAYER
  contabilizar una vez y liberar rueda
REVIEW_REQUIRED
  aislar ambigüedad e impedir nuevos cobros al jugador afectado
```

Una tirada activa por máquina y una sin resolver por jugador. Castigos pendientes bloquean nuevas tiradas del jugador. Sin cola automática ni reservas cobradas. `BUSY` muestra cuánto queda y no añade una espera fija.

Límites iniciales: 3 s entre aceptaciones por jugador, 20 por minuto y ocupación de máquina igual a duración real, al menos 4,5 s. Límite separado de paquetes fallidos por conexión antes del trabajo caro. Rechazar si se supera el máximo global de operaciones, propuesto 256.

Si falta una parte del precio, no consumir la otra. Creativo no recibe exención implícita del coste; el modo demostración sirve para pruebas. Espectadores no pueden jugar.

| Caso | Conducta requerida |
|---|---|
| Doble clic o paquete repetido | Misma respuesta; un solo cobro |
| Cerrar y reabrir | Recuperar progreso sin cobrar |
| Precio cambia antes de aceptar | `STALE_QUOTE`, actualizar y exigir otra acción |
| Inventario lleno | Buzón persistente, sin drop automático |
| Jugador muerto o desconectado | Preservar obligaciones por UUID |
| Chunk descargado | Resolver sin cargarlo ni mantener tickets |
| Reinicio normal | Reanudar obligaciones guardadas, sin contar tiempo apagado |
| Rotura durante giro | Rechazar hasta terminar, sin adelantar resultado |
| Eliminación externa del bloque | Preservar operación; omitir spawn sin origen |
| Objeto o efecto deja de existir | Aislar incompatibilidad, sin sustitución silenciosa |
| TPS bajos o ticks congelados | Resolver por ticks servidor; UI espera confirmación |

## 9. Azar y animaciones

### 9.1 Fuentes aleatorias separadas

Fuente servidor dedicada al muestreo; su estado nunca se publica. Semilla visual independiente por tirada. Conocer la semilla de animación no debe permitir predecir sorteos futuros. Logs permiten investigar, pero no prueban por sí solos justicia criptográfica. Compromiso y revelación públicos quedan fuera de v1.

### 9.2 Fortuna

Medir velocidad angular de los últimos 120 ms respecto al centro, ignorando zona central y resolviendo salto de ±π. Acotar impulso; rechazar `NaN` e infinitos. Botón usa impulso medio. Antes de aceptar solo hay previsualización del gesto, sin resultado.

Elegir duración `T` de 90–180 ticks según impulso. Con ángulo inicial `theta0` y objetivo interior del sector:

```text
delta = mod(thetaAguja - thetaObjetivo - theta0, 2*pi)
R = 2*pi*N + delta
u = clamp(elapsedTicks / T, 0, 1)
theta(u) = theta0 + R * (1 - (1-u)^5)
```

Elegir `N` entre 2 y 4 vueltas según duración e impulso. Velocidad inicial efectiva `5*R/T`, con unidades coherentes. Es una curva artística: no se conserva exactamente el impulso al imponer vueltas, tiempo y destino. No mezclarla con fórmulas incompatibles de desaceleración constante.

Objetivo en centro más jitter de hasta ±0,30 de amplitud. Bamboleo limitado por margen interior del sector, especialmente si es diminuto. Clics por cruces geométricos, con tope por frame y sin reproducir cruces atrasados al recuperar progreso.

### 9.3 Casino

Modelo analítico por fases: órbita exterior, espiral, rebotes amortiguados y asentamiento. Semilla visual y versión de trayectoria fijan parámetros. Usar curvas de enlace con continuidad de posición y velocidad; no concatenar exponenciales con saltos.

Con rotor móvil, calcular aterrizaje en coordenadas de rueda:

```text
thetaBolaMundo(T) = thetaRotor(T) + thetaSectorLocal + jitter
thetaInicialBola = thetaBolaMundo(T) - integralVelocidadAngular - desfaseRebotes
```

El jitter y oscilación final quedan dentro del sector. Una bola legible no cabe físicamente en sectores extremadamente pequeños: marcador de resultado y resaltado exacto, sin ensanchar probabilidades. Validar ese compromiso visual en M5.

Rotación de bola derivada de distancia recorrida y radio; sombra simple, rodadura y rebotes. Sin motor físico externo. El resultado no depende de FPS ni de integrar paso a paso.

### 9.4 Reloj común

`SpinStart` incluye tick inicial servidor, duración en ticks, ángulo inicial y parámetros canónicos. Cliente estima tick servidor mediante sincronización y correcciones acotadas; no empieza al recibir el paquete. Espectador tardío recibe instantánea activa y entra en la fase correspondiente.

Trayectoria como función pura de instantánea y progreso. Si llega al final sin `SpinResult`, mantener marcador y «Esperando confirmación del servidor». No anunciar entrega localmente. Si los ticks servidor dejan de avanzar, detener progreso estimado tras el margen de sincronización.

## 10. Red y datos públicos

Tres vistas:

1. **Bloque público:** nombre, tipo, estado, revisión, sectores, textos cortos y animación activa. Sin plantillas completas.
2. **Juego:** premios públicos, probabilidades, consecuencias, precio personal y recargo, tras interacción válida.
3. **Administración:** borrador completo y referencias de muestras, con permiso vigente.

Los pesos no son secretos: se infieren de los sectores. Proteger modificaciones y componentes privados, sin ocultar probabilidades.

| Dirección | Mensaje | Contrato |
|---|---|---|
| C2S / S2C | `OpenPlay` / `PlaySnapshot` | Sesión, identidad y cotización revisada |
| C2S / S2C | `OpenEditor` / `EditorSnapshot` | Sesión autorizada y revisión |
| C2S | `SelectTemplate` | Slot real de inventario y destino; servidor copia |
| C2S / S2C | `SaveDraft` / `SaveResult` | Metadatos y referencias servidor; conflicto o publicación |
| C2S | `SpinRequest` | Sesión, machineId, requestId, revisión de cotización, gesto e impulso |
| S2C | `SpinStart` | spinId, revisión congelada, sector, semilla visual, reloj y trayectoria |
| S2C | `SpinResult` | Resultado y estado real de cada acción |
| S2C | `Reject` | requestId, código, texto traducible y cotización actual si procede |
| C2S / S2C | `ClaimPending` / `PendingSnapshot` | Recogida servidor con sesión e idempotencia |

Usar `requestId` antes de aceptar y `spinId` para operaciones aceptadas. Cada sesión tiene una secuencia creciente de solicitudes; el servidor conserva su máximo procesado y rechaza secuencias anteriores aunque expire la caché de respuestas. Un reintento con la misma secuencia devuelve la respuesta cacheada o exige consultar estado, sin repetir cobro. La caché tiene TTL y capacidad limitada. Una sesión nueva no acepta identificadores de otra anterior; tras reiniciar, todas las sesiones anteriores quedan invalidadas y las operaciones se recuperan por `spinId`.

Comprobar dimensión, posición ya cargada, identidad, sesión, alcance de 8 bloques, modo de jugador, revisión y permisos. Posición descargada se rechaza sin cargar. Limitar tamaños al decodificar y mutar solo en hilo servidor.

Negociar protocolo y capacidades al conectar. `environment: "*"` no exige por sí solo un mod remoto: verificar handshake y rechazo comprensible de clientes ausentes/incompatibles en M0. Se instala en cliente y servidor.

Enviar animación pública a quienes rastrean chunk y estén a menos de 32 bloques, además del participante. Incluir instantánea activa en sincronización de bloque para altas tardías. Resultado privado solo al jugador; espectadores reciben resumen. Sin posiciones por frame ni configuración completa por tick.

## 11. Persistencia y recuperación

### 11.1 Identidades y responsabilidades

- `machineId`: UUID estable, independiente de posición. Traslado autorizado lo conserva; copia creativa recibe otro. Detectar duplicados entre máquinas cargadas antes de aceptar tiradas.
- `configRevision`: publicación, distinta de `schemaVersion` de disco.
- `spinId`: UUID de operación, conservado en reintentos.
- `actionId`: `(spinId, indiceAccion)` para efectos y entregas.

BlockEntity conserva configuración, identidad y resumen. Registro mundial conserva participante, origen, precio cobrado, revisión, resultado congelado, trayectoria, estados y recibos. **Solo el gestor mundial resuelve**; cargar el bloque no ejecuta de nuevo la operación.

Buzón por UUID y registro de recargos/castigos por jugador e identidad. Una obligación no es solo un `ItemStack`: también puede ser efecto, daño o spawn.

### 11.2 Cobro y atomicidad real

Antes de habilitar economía real, diseñar y verificar recuperación:

1. Preparar intención con precio e instantánea congelados.
2. Hacer durable la intención mediante estrategia elegida en M0.
3. Aplicar cobro completo en hilo servidor y registrar marcas de recuperación.
4. Publicar aceptación solo al cumplir el contrato de guardado elegido.
5. Registrar pasos preparados, aplicados y confirmados por acción.

Marcar `SavedData` como modificado no equivale a escritura durable ni sincroniza el guardado del jugador. Un UUID en una lista tampoco garantiza exactamente una vez. M0 debe escoger diario durable con reconciliación o recibos/escrow compatibles con persistencia de jugadores y demostrar ventanas de fallo con inyección de caídas.

Si no se sabe si se cobró o entregó, usar `REVIEW_REQUIRED`: conservar evidencia y bloquear nuevas tiradas del afectado hasta resolución de operador. No reembolsar ni ejecutar a ciegas. Fallo anterior al cobro no genera entrega; cobro confirmado sin aceptación recuperable exige devolución verificada. No devolver niveles restaurando un estado antiguo completo del jugador.

Publicar exige recuperación automática ante reinicio normal y fallos demostrados. Documentar cualquier ventana residual ante terminación abrupta. Si M0 no consigue una solución razonable, mantener tiradas reales deshabilitadas en el prototipo.

### 11.3 Entregas y buzón

Intentar al terminar. Sin espacio, conservar remanente; una entrega parcial registra cantidades y no recrea el stack completo al reintentar. `JOIN` y recogida explícita reintentan objetos. No borrar antes de confirmar tratamiento.

Sin drops automáticos: evita robo, despawn y muerte. Resumen al entrar y comandos de pendientes/recogida. Máximo propuesto 128 obligaciones de objetos por jugador; alcanzarlo bloquea nuevas tiradas antes de cobrar, sin descartar premios antiguos.

Dañar, invocar y entregar necesitan recibos propios. Contadores y anuncios se deduplican por operación. Repetir una animación nunca justifica repetir daño.

### 11.4 Descarga, traslado y versiones

Máquinas inactivas descargadas no necesitan ticker mundial ni retienen geometría o sesiones. Operaciones activas tienen cuota global, sin tickets de chunk. Tras resolver, guardar estadísticas pendientes por identidad y fusionarlas al cargar; incluirlas en el item si se retira la máquina.

Retirada solo staff, sin giro activo ni editor ajeno. Un único bloque conserva configuración e identidad, sin soltar muestras. Copia creativa conserva como máximo configuración, nunca identidad, estadísticas u obligaciones. Eliminación externa no borra operaciones aceptadas.

Versionar codecs y migraciones. Datos futuros o corruptos dejan máquina fuera de servicio y conservan original para diagnóstico. Restauraciones parciales pueden invalidar recibos: respaldar y restaurar juntos mundo, jugadores y diario.

### 11.5 Retención y operación

Separar obligaciones de logs rotatorios. Propuesta: auditoría 30 días, máximo 256 MiB, rotación diaria o por tamaño. Conservar recibos mientras una obligación pueda reintentarse; compactar solo tras probar que no se reejecutará. Contadores de 64 bits.

Auditar spinId, jugador, máquina, revisiones, pesos, resultado, precio, acciones y errores. No registrar NBT completo ni libros. Si falta almacenamiento, pausar nuevas tiradas y avisar al staff; nunca borrar obligaciones para seguir cobrando.

## 12. Render, sonido y rendimiento

Geometría compartida UI/bloque y cacheada por revisión. Estado de render separado de datos mutables. Reutilizar patrones de TheBall, verificando la API concreta de BlockEntity: un renderer de entidad no prueba una firma intercambiable.

- Madera/metal, cristal emisivo, bisagra y candado de dos estados.
- Fortuna con canto, eje y clavijas; casino con cuenco, rotor, separadores y bola.
- Cámara fija; zoom máximo 5 % en casino, desactivable.
- Sin shaders, postproceso ni motor físico externo. Sombreado por caras y sombra geométrica de bola.
- Culling y simplificación por distancia; detener atracción fuera de vista.
- Audio derivado del progreso; atenuación para espectadores y mezcla que evita duplicar sonido UI/caja.
- Topes propuestos: 8 clics por frame y 4 loops de rodadura cercanos; sin sonidos atrasados al recuperar foco.
- Celebración con partículas locales sin daño, sin entidad de fuegos artificiales. Anuncio global opcional y limitado. No anunciar muerte si se canceló el daño.

Objetivos, todavía sin medir: coste añadido p95 <0,5 ms/tick con 100 máquinas cargadas y 20 giros activos; render añadido p95 <1 ms/frame con 10 máquinas visibles en equipo documentado. Registrar base, hardware, mods, resolución y escala GUI. Los 20 giros requieren 20 jugadores o arnés de prueba, sin alterar la regla de producción.

## 13. Comandos y configuración

| Comando | Acceso | Resultado |
|---|---|---|
| `/ludopathyroulette ayuda` | Todos | Reglas y recogida |
| `/ludopathyroulette stats` | Todos, mirando máquina | Totales públicos |
| `/ludopathyroulette pendientes` | Jugador | Sus premios y consecuencias |
| `/ludopathyroulette recoger` | Jugador | Intenta recoger objetos pendientes |
| `/ludopathyroulette editar` | Staff | Editor dentro del alcance |
| `/ludopathyroulette probar` | Staff | Demostración identificada |
| `/ludopathyroulette pausar` / `reanudar` | Staff | Disponibilidad |
| `/ludopathyroulette retirar` | Staff | Retirada validada sin giro activo |
| `/ludopathyroulette diagnostico <spinId>` | Op 2 | Estado y evidencias |
| `/ludopathyroulette resolver <spinId> ...` | Op 2 | Resolución explícita auditada |
| `/ludopathyroulette recargar` | Op 2 | Valida y aplica política global |

Fijar sintaxis de resolución tras M0; no incluir «repetir todo» genérico. Comandos de posición requieren jugador o coordenadas explícitas y no cargan chunks.

Configuración global: `allowCreative`, `requireSneak`, alcance, cuotas, gratuidad, acciones habilitadas, listas permitidas, máximo de operaciones y buzón, auditoría y presupuestos visuales. Recargar valida antes de reemplazar. No cambia precio/pesos de giros aceptados; deshabilitar una acción peligrosa sí impide ejecutarla y registra omisión.

No introducir topes diarios que sustituyan una casilla ganadora. Una futura cuota pausa la máquina o publica otra revisión antes de aceptar nuevas tiradas.

## 14. Obtención e integración

Items: `ludopathyroulette:ficha`, `fortuna` y `bola`. Ficha intercambiable, sin saldo externo. Receta propuesta pendiente de balance: 1 oro + 1 hierro producen 8 fichas. Máquinas con madera, metal, cristal y redstone; casino añade oro. Orientación al colocar, sin recetas por orientación.

Craftear una caja no autoriza configurarla ni emitir premios. Decidir antes de distribuir si aporta valor permitir crafteo o si la obtención será solo creativa para staff.

Gradle independiente en `mods/LudopathyRoulette/`, mod id `ludopathyroulette`, grupo `com.ludopathyroulette`, documentación/comentarios españoles y traducciones `es_es`/`en_us`. Fuentes cliente separadas y arranque dedicado sin cargar render.

Al implementar: README con versiones, instalación en ambos lados y recetas/materiales derivados de JSON; fila en README raíz y job propio de CI. Esta revisión solo modifica el plan.

## 15. Arquitectura propuesta

```text
src/main/java/com/ludopathyroulette/
  LudopathyRouletteMod
  registry/             bloques, items, menús, payloads y sonidos
  block/                RouletteBlock, HatchHitTest, RouletteBlockEntity
  config/               RouletteConfig, Segment, Cost, PunishmentPlan, codecs
  permission/           EditPermission, ActionPolicy
  spin/                 SpinService, WeightedRoller, SpinSnapshot, ActionExecutor
  economy/              PriceCalculator, InventoryPayment, DeliveryService
  persistence/          OperationJournal, PendingStore, RecoveryService
  network/              sesiones, cotizaciones, validadores y payloads
  menu/                 PlayMenu, EditorMenu con muestras servidor
  command/              RouletteCommand
src/client/java/com/ludopathyroulette/client/
  RouletteClient
  screen/               FortunaScreen, BolaScreen, EditorScreen
  render/               RouletteBlockRenderer, WheelGeometry, BallRenderer
  animation/            FortunaTrajectory, BolaTrajectory, ServerTickClock
  audio/                RouletteSoundController
src/main/resources/
  fabric.mod.json, assets/ludopathyroulette/, data/ludopathyroulette/
src/test/               matemáticas, validación y recuperación
```

Funciones puras para pesos, precio, validación y trayectorias; servidor aplica inventarios y mundo. Evitar framework de plugins o jerarquías por cada estilo. Sin mixins previstos salvo hueco concreto demostrado en M0.

## 16. Hitos con criterios de salida

| Hito | Trabajo | Evidencia para avanzar |
|---|---|---|
| M0 — viabilidad | APIs, handshake, muestras, XP, recuperación | Arranque dedicado; cliente incompatible rechazado; prototipo con ventanas de caída documentadas y probadas |
| M1 — objeto y permisos | Caja, puerta, identidad, codecs y resumen | Cuatro orientaciones; persistencia; ningún acceso no autorizado |
| M2 — editor | Borradores, reparto, precio, avisos y conflictos | Guardar/reabrir; dos editores sin sobrescritura; muestras sin crear/consumir stacks |
| M3 — economía | Cobro, idempotencia, buzón, acciones y diario | Sin doble cobro; caídas probadas; resolver sin chunk; ambigüedades aisladas |
| M4 — Fortuna | Gesto, trayectoria, reloj, accesibilidad y audio | Destino correcto con distintos FPS, latencias y sectores extremos |
| M5 — Casino | Rotor, fases continuas, bola y rebotes | Aterrizaje relativo y lectura clara con 2/4/12 casillas |
| M6 — entrega | Espectadores, operación, rendimiento, README y CI | Matriz superada y pruebas reales en Logitanos |

M3 precede a acabados visuales. Compilar no demuestra recuperación ni ausencia de duplicaciones.

## 17. Pruebas y aceptación

### 17.1 Automatizadas focalizadas

- Pesos: fronteras exactas, suma máxima, orden y rechazo de datos inválidos. Distribución con semilla fija, muestra y tolerancia justificadas; no usar «1 000 tiradas ±2 %» como garantía universal.
- Porcentajes: restos, empates, decimales españoles, filas diminutas y suma exacta.
- Precio: componentes exactos, inventario distribuido, falta de XP, redondeos y recargos. Ningún cobro parcial al fallar.
- Sesiones: duplicados, peticiones atrasadas, caducidad, nueva identidad en misma posición y revisión obsoleta.
- Codecs: ida/vuelta, límites, corrupción, versiones futuras y registros ausentes.
- Recuperación: caída antes/después de cada escritura y mutación, entrega parcial, acción sin recibo final y restauración incompatible. Comprobar invariantes y estados de revisión, no solo ausencia de excepciones.
- Trayectorias: 10 000 combinaciones reproducibles por rueda; continuidad, sectores extremos, saltos de frames y aterrizaje interior.

### 17.2 Integración en juego

| Escenario | Resultado exigido |
|---|---|
| Dos jugadores, misma máquina y tick | Solo una aceptación y un cobro |
| Un jugador, dos máquinas | Rechazo de segunda si hay obligación bloqueante |
| Precio editado durante arrastre | Nueva cotización y otra acción antes del cobro |
| Pesos editados durante giro | Instantánea anterior visual y funcional |
| Permiso revocado con editor abierto | Sin guardado ni nuevas muestras |
| Inventario lleno o parcialmente libre | Entrega verificable y remanente en buzón |
| Desconexión o muerte | Obligaciones preservadas; aviso al volver |
| Descarga y reinicio | Sin tickets ni nuevo sorteo |
| Rotura, explosión, pistón y copia | Sin retirada no autorizada ni identidad duplicada activa |
| Spawn sin hueco o protección | Omisión explicada, sin cargas forzadas |
| Tótem o daño cancelado | Mensaje y consecuencia reales |
| Recargo y reconexión | Precio/usos conservados, rechazos no consumen |
| Espectador tardío o latencia | Misma fase, revisión y sector |
| 20/144 FPS y pérdida de foco | Mismo destino, sin sonidos atrasados |
| TPS bajos o congelación | Sin entrega prematura ni anuncio local falso |
| Cliente ausente/incompatible | Rechazo comprensible |
| Servidor sin entorno gráfico | Arranque sin clases cliente |

### 17.3 Visuales y carga

Capturas de ambas ruedas con 2/4/12 casillas, premio diminuto, nombres largos, componentes relevantes y todas las escalas GUI admitidas. Probar movimiento reducido, silencio, orientaciones y tirada antigua tras publicar otra revisión.

Medir escenarios de 12, buzones pendientes, spam y exploración con miles de máquinas descargadas. Registrar CPU, RAM, disco y red. Una máquina inactiva descargada no retiene geometría ni sesiones en memoria.

**Terminado:** ambas ruedas configurables y jugables sin comandos; coste y consecuencias visibles; destino visual coincide con servidor; reconectar no duplica ni evita obligaciones; fallos recuperables y ambiguos se distinguen; permisos, rendimiento y recuperación demostrados en juego.

## 18. Decisiones fijadas y pendientes

Fijadas para implementar sin contradicciones:

- Dos bloques con lógica común, sin conversión en caliente.
- Premios ilimitados autorizados y cobro consumido; sin stock ni recaudación.
- Slots de muestra sin transferencia.
- Reparto publicado y proporcional; servidor decide resultado.
- Recargos y castigos sobreviven a desconexiones.
- Premios sin espacio al buzón, sin drop automático.
- Spawn habilitado solo tras comprobar compatibilidad; daño letal sin garantía de muerte.
- Resolución independiente del chunk, por ticks y sin tiempo apagado.

| Pendiente | Momento | Condiciona |
|---|---|---|
| Estrategia durable y ventanas residuales | M0 con prueba ejecutable | Habilitar economía real |
| Descuento de niveles y fracción | M0 | Texto de precio y reembolso |
| Daño, entidades y protecciones instaladas | M0/M3 en pruebas | Acciones peligrosas |
| Compactación segura de recibos | M3 | Disco acotado sin reejecución |
| Bola legible en sector diminuto | M5 | Presentación honesta |
| Crafteo o solo creativo y recetas | Antes de M6 | Balance y materiales |

## 19. Fuera de alcance inicial

Apuestas entre jugadores, jackpot progresivo, giro automático, recompensas por rachas, comandos arbitrarios, efectos en área, stock finito, economías externas, clasificación global, base de datos externa y física libre. Estas ampliaciones no deben complicar el flujo principal ni introducir probabilidades ocultas.
