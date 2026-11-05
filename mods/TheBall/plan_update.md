# Plan de actualización: el Trono y la Reina de Aluminio

> **Estado: implementado en la v1.1.0 (compila; pendiente de probar en el juego, ver §14). Las desviaciones respecto a este diseño están en [§17](#17-implementación-v110).**
> Documento de planificación de la siguiente gran actualización de The Ball (v1.1+), escrito en una sesión de diseño con el
> dueño del servidor. Las cifras son ajustables antes de programar. Las menciones a clases/APIs de Minecraft 26.3 son
> orientativas y **hay que verificarlas al compilar** (esa versión cambió mucho la API respecto a 1.21).

## Índice

1. [Resumen](#1-resumen)
2. [Punto de partida: lo que ya hace el mod](#2-punto-de-partida-lo-que-ya-hace-el-mod)
3. [El Trono](#3-el-trono)
4. [Datos que se guardan](#4-datos-que-se-guardan)
5. [Experiencia](#5-experiencia)
6. [Recompensas y coronación](#6-recompensas-y-coronación)
7. [Armadura del Rey del Aluminio y Corona](#7-armadura-del-rey-del-aluminio-y-corona)
8. [Cetro del Rey de Aluminio](#8-cetro-del-rey-de-aluminio)
9. [Dios del aluminio: Ascensión de la Reina](#9-aluminio-explosivo-ascensión-de-la-reina)
10. [Configuración](#10-configuración)
11. [Comandos de administración](#11-comandos-de-administración)
12. [Ids, textos y assets nuevos](#12-ids-textos-y-assets-nuevos)
13. [Fases de implementación](#13-fases-de-implementación)
14. [Plan de pruebas](#14-plan-de-pruebas)
15. [Riesgos y casos límite](#15-riesgos-y-casos-límite)
16. [Decisiones abiertas](#16-decisiones-abiertas)
17. [Implementación (v1.1.0)](#17-implementación-v110)

---

## 1. Resumen

Hay un **trono**. Pones tu bola de aluminio encima, con una **corona**, y la vas agrandando con papel como siempre. Cada hoja
que añades **en el trono** da **experiencia**, y la experiencia por hoja sube mucho cuanto más grande es la bola. Por el camino
se desbloquean **recompensas** (comida y armadura por piezas). Al llegar a **2000 hojas** (una bola de **8 bloques de
diámetro**: la *Reina de Aluminio*) quienes más han aportado reciben la **Corona de aluminio** (casco con superpoderes) y el
**Cetro**. Con corona + cetro se desbloquea la habilidad final, **Dios del aluminio**: una transformación estilo anime de 5 segundos.

Principios de diseño:
- **Es un esfuerzo de servidor**, no de un jugador solo: 17.919 papeles para llegar a la Reina.
- **Nadie se lo lleva todo por picar el último papel**: el XP se paga a quien pone cada papel y las recompensas dependen de lo aportado.
- **Sin trampas rentables**: el coste y el XP son fijos, no hay forma de cobrar dos veces.
- **Sin romper el servidor**: poderes con esperas y costes, sin destruir bloques por defecto, todo configurable.

---

## 2. Punto de partida: lo que ya hace el mod

Datos del código actual (`src/main/java/com/theball/`). **Este plan no los cambia**, se apoya en ellos:

| Dato | Valor / comportamiento | Dónde |
|---|---|---|
| Hojas máximas | `MAX_SHEETS = 2000` | `AluminumBallEntity` |
| Coste de papel por hoja nueva | `foilCostFor`: `1 + hojas/30` hasta 500 (sube hasta 17); luego baja hasta 1 en 2000 | `AluminumBallEntity` |
| Diámetro | `diameterFor`: 9 hojas = 0,12 · 500 = 0,46 · 1000 = 3,0 · 1500 = 5,5 · **2000 = 8,0** | `AluminumBallEntity` |
| Bola sólida | a partir de diámetro ≥ 1 (≈ 610 hojas) | `SOLID_DIAMETER` |
| Alimentar | click derecho con papel: `onPlayerUse` resta `foilCostFor(hojas)` papeles y suma 1 hoja; en creativo no gasta | `AluminumBallEntity` |
| Recoger | click derecho **sin** papel en ninguna mano: la bola pasa al inventario con sus hojas | `AluminumBallEntity` |
| Soltar / lanzar | `AluminumBallItem` **no** tiene `useOn`: mantener click derecho carga (20 ticks para el máximo) y al soltar la bola sale; un toque rápido la deja delante | `AluminumBallItem` |
| Datos del item | componente `theball:sheets` (entero) | `ModComponents` |
| Datos de la entidad | `SynchedEntityData` para las hojas; guardado con `ValueInput`/`ValueOutput` | `AluminumBallEntity` |
| Rastreo | `clientTrackingRange(10)`, `updateInterval(1)`, `fireImmune()` | `ModEntities` |
| Fusión en yunque | `AnvilMenuMixin`: suma las hojas de dos bolas (máx. 2000), coste `2 + hojas/50` niveles (máx. 39) | `mixin/` |
| Orden de registro | `ModComponents → ModBlocks → ModItems → ModEntities → ModWorldgen` (el orden importa) | `TheBallMod` |
| Patrón de "piezas sólidas" | la portería usa entidades invisibles (`GoalPartEntity`) para colisión | `GoalEntity` |

### Coste real de crecer la bola (hechos que justifican el diseño del XP)

| Tramo de hojas | Papeles necesarios | % del total |
|---|---|---|
| 9 → 500 | 4.411 | 25 % |
| 500 → 1000 | **7.174** | **40 %** |
| 1000 → 1500 | 4.503 | 25 % |
| 1500 → 2000 | 1.831 | 10 % |
| **Total 9 → 2000** | **17.919** | 100 % |

Son ≈ 32.000 lingotes (≈ 3.600 bloques de aluminio usando la cortapiedras: 1 bloque → 5 papeles).

**Consecuencia:** el tramo más caro es 500–1000 y las últimas 500 hojas son baratas en papel. Pagar XP solo por hojas premiaría
poco lo más duro y mucho lo más fácil. Por eso el XP se paga por tamaño (para que el final sea enorme) y el tramo caro rinde menos por papel; ver §5.2.

---

## 3. El Trono

### 3.1 El bloque
- **Id:** `theball:throne`, nombre "Trono de aluminio". Ocupa 1 bloque (cubo) pero se ve como un trono: base ancha, respaldo alto y
  brazos, con la parte superior plana como asiento/peana donde se apoya la bola.
- **Receta propuesta** (3×3, con forma): 7 bloques de aluminio + 1 bloque de oro:

  ```
  A . A
  A G A
  A A A
  ```
  (`A` = bloque de aluminio, `G` = bloque de oro). Se puede cambiar; ver [decisiones abiertas](#16-decisiones-abiertas).
- Se rompe con pico de piedra o mejor y **se suelta a sí mismo** (`loot_table`), como la mena. Resistencia a explosiones alta (como
  obsidiana) para que un creeper no lo arruine.
- Orientación: mira hacia donde mire el jugador al colocarlo (propiedad `facing`); solo cambia el modelo, la bola es esférica.

### 3.2 Interacciones

| Acción | Resultado |
|---|---|
| Click derecho con una **bola** (item) sobre el trono vacío | La bola se **sienta**: se convierte en entidad anclada. Conserva hojas, contribuciones y recompensas dadas |
| Click derecho con **papel** sobre el trono con bola (o sobre la bola) | Alimenta la bola, igual que ahora (mismo coste por hoja). Suma XP y contribuciones (apartado 5) |
| Mantener click derecho con papel | Alimenta en **tanda**: una hoja cada 4 ticks mientras tengas papel suficiente. Se corta al soltar |
| **Shift + click derecho** con la mano vacía | Retira la bola: vuelve al inventario como item con todos sus datos |
| Click derecho con la mano vacía (sin shift) | Mensaje en la action bar con el estado: hojas, próximo hito, tu aporte en % |
| Golpear la bola sentada | **No la empuja** (está anclada). Hace un sonido suave de lana y nada más |
| Romper el trono con bola sentada | La bola se **libera** como entidad normal (con física); no se convierte en item (puede medir 8 bloques) |
| Click derecho con otro item | Nada |

Solo **una bola por trono**. No se permiten dos tronos con la misma bola (la bola es una entidad única).

### 3.3 La bola sentada
- La entidad existente `AluminumBallEntity` gana un **ancla** (`BlockPos` del trono). Mientras está anclada:
  - Sin gravedad ni rozamiento ni empujes (no se mueve): `noPhysics`/`setNoGravity` y se fija la posición cada tick.
  - Su centro está en `(trono.x + 0,5 ; asiento.y + diámetro/2 ; trono.z + 0,5)`: **la base apoya en el asiento** y crece hacia
    arriba y hacia los lados. Cada vez que cambia el diámetro se recoloca.
  - Mantiene colisión sólida (los jugadores pueden subirse a una Reina de 8 bloques; es un efecto bonito).
  - Sigue siendo apuntable para alimentarla y recogerla (shift + click).
- Si el trono desaparece (rotura, comando, piston si se permitiera), el ancla se limpia y la bola vuelve a la física normal.

### 3.4 Comprobación de espacio libre
Una bola de 8 bloques necesita **8×8×8 libres** (más el asiento). Antes de aceptar una hoja nueva en el trono:

1. Calcular el diámetro **nuevo** (`diameterFor(hojas + 1)`) y su caja (AABB) centrada sobre el asiento.
2. Comprobar colisiones con bloques (excluyendo el propio trono). Si choca → **no se acepta la hoja** y no se gasta papel.
3. Mensaje en la action bar: *"No hay espacio: la bola necesita un hueco de 8 × 8 × 8 bloques"* (con el tamaño real).
4. Para no hacer el cálculo en cada tick, solo se comprueba cuando el diámetro entero (`ceil`) cambia respecto al último.

Fuera del trono la bola no tiene esta restricción (ahí se mueve libremente y se empuja contra las paredes como ahora).
La comprobación se puede desactivar en config (`require_free_space`), pero entonces la bola atravesará techos y paredes.

### 3.5 La corona sobre la bola
La corona se dibuja en el **renderer** (cliente) cuando la bola está anclada. No es una entidad aparte.

| Hojas | Aspecto de la corona |
|---|---|
| 0 – 499 | Corona sencilla de aluminio (plata) |
| 500 – 999 | + 1 joya (azul) |
| 1000 – 1499 | + 2 joyas (azul y roja) |
| 1500 – 1999 | + 3 joyas (azul, roja y verde), brillo suave |
| **2000** | Corona **dorada** con diamante central, partículas continuas y haz de luz hacia arriba (la Reina coronada) |

- La corona **escala con el diámetro** de la bola (relación fija: ancho de la corona ≈ 60 % del diámetro) y se apoya en el punto
  más alto de la esfera, ligeramente inclinada hacia delante.
- La textura de la corona va en el mismo atlas que el resto (cuadrada 64×64); modelo propio hecho por código (como ya se hace
  con la esfera) o como pieza de modelo JSON dibujada por el renderer.
- El nivel visual se calcula en el cliente a partir de las hojas sincronizadas: **no hace falta enviar nada nuevo**.

### 3.6 Feedback al jugador
- **Action bar al alimentar**: `"Reina de Aluminio  ·  1.247 / 2.000 hojas  ·  Siguiente hito: 1.300  ·  Tu aporte: 3,2 %"`.
- **Al cruzar un hito de 100 hojas** (sentada): fuegos artificiales pequeños sobre la bola, sonido de campana, mensaje al
  jugador que lo cruzó y XP flotante con el valor.
- **Al cruzar un umbral de recompensa** (250/500/1000/1500/2000): fuegos grandes + mensaje a los jugadores cercanos.

---

## 4. Datos que se guardan

Todo viaja **con la bola**, no con el trono, así que sobrevive a recogerla, moverla de trono y fusionarla. Al sentar/retirar la
bola los datos pasan de item a entidad y viceversa.

| Dato | Item (data component) | Entidad (guardado) | Para qué |
|---|---|---|---|
| Hojas | `theball:sheets` (ya existe) | `SynchedEntityData` (ya existe) | Tamaño |
| Aportes | `theball:contributions` | lista `{uuid, papeles}` | Repartir recompensas |
| Recompensas entregadas | `theball:rewards_given` | máscara de bits (`int`) | No repetir premios |
| ¿Coronada? | — | derivado: `hojas == 2000` y `rewards_given` incluye el bit final | Efectos finales una sola vez |

- **Aportes**: se anota **siempre** que alguien alimenta la bola (dentro o fuera del trono), con los papeles *realmente gastados*
  (`foilCostFor(hojas)` en ese momento). En creativo (`hasInfiniteMaterials`) **no se anota** nada, para que nadie falsifique aporte.
- **Máscara `rewards_given`**: bit 0 = 250, bit 1 = 500, bit 2 = 1000, bit 3 = 1500, bit 4 = 2000.
- **Migración**: las bolas existentes (sin estos componentes) se leen con valores por defecto (sin aportes, sin recompensas). No
  hace falta convertir nada en los mundos antiguos.
- **Fusión en el yunque** (`AnvilMenuMixin`): `hojas` = suma (máx. 2000, ya existe); `contributions` = suma por jugador;
  `rewards_given` = **OR** de las dos máscaras (un premio entregado en cualquiera de las dos sigue contando como entregado).
  Fabricar la bola directamente siempre sale más barato que fusionar (el coste por hoja baja después de 500), así que fusionar
  no es una trampa rentable.
- **Importante**: el XP **no necesita estado guardado** (ver apartado 5). Las hojas añadidas solo suben, así que no hay forma de
  pagar dos veces la misma hoja.

---

## 5. Experiencia

### 5.1 Regla
El XP se paga **por hoja añadida a una bola sentada en el trono**, **a quien pone ese papel**. No hay hito "cobrable" ni nada que
reclamar: se cobra al instante, y por eso nadie puede quedarse con lo que han aportado otros.

> **XP de la hoja número `n` = XP del hito de `n` ÷ 100** (tabla del apartado 5.2)

- El **hito** de una hoja es `n / 100` (hito 1 = hojas 1–100, … hito 20 = hojas 1901–2000).
- Cada hito de 100 hojas reparte su XP a partes iguales entre sus 100 hojas. Los hitos crecen en progresión geométrica para que
  el final sea enorme.
- No se paga XP en **creativo**, ni por hojas añadidas **fuera** del trono (pasan a formar parte de la bola sin dar XP, salvo el
  aporte, que sí cuenta para las recompensas).
- Como el XP por hoja no es entero, se **acumula** por jugador durante el "clic" de alimentar y se entrega el entero al soltar o al
  cruzar un hito, guardando el resto decimal en memoria (se pierde, como mucho, 1 XP si el jugador se desconecta).

### 5.2 Tabla de hitos (con los valores por defecto)

| Hito | Hojas | XP del hito | XP por hoja | Niveles que sube | Nivel al terminar | Papeles del hito |
|---|---|---|---|---|---|---|
| 1 | 0–100 | 12 | 0,1 | +1,5 | 1,5 | 211 |
| 2 | 100–200 | 18 | 0,2 | +1,7 | 3,2 | 550 |
| 3 | 200–300 | 26 | 0,3 | +1,9 | 5,0 | 880 |
| 4 | 300–400 | 37 | 0,4 | +2,1 | 7,1 | 1.220 |
| 5 | 400–500 | 51 | 0,5 | +2,3 | 9,4 | 1.550 |
| 6 | 500–600 | 69 | 0,7 | +2,5 | 11,9 | 1.647 |
| 7 | 600–700 | 92 | 0,9 | +2,8 | 14,7 | 1.541 |
| 8 | 700–800 | 129 | 1,3 | +3,1 | 17,9 | 1.435 |
| 9 | 800–900 | 201 | 2,0 | +3,5 | 21,3 | 1.329 |
| 10 | 900–1000 | 294 | 2,9 | +3,9 | 25,2 | 1.222 |
| 11 | 1000–1100 | 415 | 4,2 | +4,3 | 29,5 | 1.116 |
| 12 | 1100–1200 | 598 | 6,0 | +4,8 | 34,3 | 1.010 |
| 13 | 1200–1300 | 904 | 9,0 | +5,3 | 39,6 | 901 |
| 14 | 1300–1400 | 1.303 | 13,0 | +5,9 | 45,5 | 791 |
| 15 | 1400–1500 | 1.815 | 18,1 | +6,6 | 52,1 | 685 |
| 16 | 1500–1600 | 2.472 | 24,7 | +7,3 | 59,4 | 579 |
| 17 | 1600–1700 | 3.307 | 33,1 | +8,1 | 67,5 | 472 |
| 18 | 1700–1800 | 4.367 | 43,7 | +9,0 | 76,5 | 366 |
| 19 | 1800–1900 | **5.708** | 57,1 | **+10,0** | 86,5 | 260 |
| 20 | 1900–2000 | **22.523** | 225,2 | **+30,0** | 116,5 | 154 |

**Diseño:** el primer hito sube **1,5 niveles** y cada hito hasta el 19 sube **×1.111** más que el anterior (progresión geométrica),
llegando a **10 niveles en el hito 19**. El **hito 20 es el gran final: 30 niveles** de golpe. El XP de cada hito sale de convertir
esos niveles con la curva de vanilla, partiendo de nivel 0 (los niveles son orientativos: dependen del nivel con el que empiece cada
jugador; el XP, no).

**Totales:** ≈ **44.341 XP** (nivel ~116 partiendo de nivel 0). Las últimas 500 hojas dan **≈ 87 %** del XP.
Niveles por tramo: 9–500 → +9,4 · 500–1000 → +15,9 · 1000–1500 → +26,9 · 1500–2000 → +64,4.
Ya no hay término por papel gastado: con él el primer hito daría 3,7 niveles y no se podría empezar en 1,5. Por eso el tramo
500–1000 (el más caro en papel) rinde menos XP por papel que el resto; si molesta, ver [decisiones abiertas](#16-decisiones-abiertas).

### 5.3 Casos a cubrir
- **Varias hojas de golpe** (alimentar en tanda): cada hoja paga por separado, se cruzan los hitos que toque y se muestran
  las fanfarrias en orden (máx. una por tick para no saturar).
- **Desconexión mientras alimentas**: el XP ya entregado se mantiene; no hay estado a medias.
- **Bola cambiada de trono**: nada que migrar (no hay estado de XP).
- **Cambio de config** con partidas empezadas: el XP futuro usa los valores nuevos; no se recalcula lo ya pagado.

---

## 6. Recompensas y coronación

### 6.1 Calendario de premios

| Hojas | Premio |
|---|---|
| 250 | Pack de comida: 8 bocatas + 4 durums |
| 500 | **Botas** del Rey del Aluminio |
| 1000 | **Pantalones** del Rey del Aluminio |
| 1500 | **Peto** del Rey del Aluminio |
| **2000** | **Corona** (casco) + **Cetro** + coronación |

Más: **Medalla del tributo** (item cosmético) al llegar a 2000 para quien haya gastado ≥ 2 % del papel total.

### 6.2 Quién lo recibe
- Se calcula con los **aportes** guardados en la bola. El "papel total" de referencia es la suma de `foilCostFor(n)` de 9 a 2000
  (= 17.919 papeles) y se calcula con la función, no con un número escrito a mano.
- Reciben el premio los jugadores con **≥ 10 % del papel total** (≈ 1.792 papeles); medalla con **≥ 2 %** (≈ 358).
  Ambos umbrales van en la config.
- **Cuándo se comprueba:** cuando la bola sentada cruza el umbral y el bit correspondiente de `rewards_given` está a 0. Si la bola
  se hizo crecer fuera del trono y se sienta ya pasada del umbral, los premios pendientes se entregan **en cuanto se sienta**.
- **Quién tiene que estar**: los jugadores con derecho que estén **conectados** reciben el premio en su inventario (si no cabe, se
  cae a sus pies). Los que **no estén conectados** quedan **pendientes**: el premio se entrega la próxima vez que interactúen con
  ese trono con la mano vacía. Los pendientes se guardan en el trono (`pending_rewards`: uuid + bits).
- **Opción `once_per_player`:** si está activa, cada jugador solo puede recibir el botín de 2000 una vez en todo el servidor (se
  apunta en un registro del mundo). Por defecto **desactivada** (cuesta 17.919 papeles, es un precio justo).

### 6.3 Coronación (al llegar a 2000)
Secuencia, ocurre **una sola vez** por bola (controlada por `rewards_given`):

| Tiempo | Efecto |
|---|---|
| 0 s | Se bloquea alimentar más. Mensaje a **todo el servidor**: *"¡{jugador} ha coronado a la Reina de Aluminio!"* |
| 0–2 s | La bola se ilumina, suena un acorde ascendente, partículas `END_ROD` suben en espiral |
| 2 s | La corona cambia a la dorada (apartado 3.5) con un destello blanco |
| 2–5 s | Fuegos artificiales dorados sobre la bola, 3 haces de luz salen hacia el cielo |
| 5 s | Entrega de premios a quienes cumplan el umbral (corona, cetro, medalla) |
| Después | Haz de luz permanente mientras la bola esté sentada y coronada; el trono queda como "monumento" |

---

## 7. Armadura del Rey del Aluminio y Corona

### 7.1 Piezas
No se fabrican: **solo se consiguen como recompensa** (más comando y pestaña de creativo). Se reparan con **lingotes de aluminio**
en el yunque.

| Pieza | Id | Defensa | Durabilidad |
|---|---|---|---|
| Corona de aluminio (casco) | `theball:aluminum_crown` | 3 | 13 × 40 = 520 |
| Peto del Rey del Aluminio | `theball:aluminum_chestplate` | 8 | 16 × 40 = 640 |
| Pantalones del Rey del Aluminio | `theball:aluminum_leggings` | 6 | 15 × 40 = 600 |
| Botas del Rey del Aluminio | `theball:aluminum_boots` | 3 | 11 × 40 = 440 |

- Comunes a las cuatro: **dureza (toughness) 3,0**, **resistencia al empuje 0,05** por pieza, **encantabilidad 25**, material de
  reparación = lingote de aluminio. Rareza **épica** (brillo de encantamiento).
- Atributo **ligera**: cada pieza suma **+2,5 % velocidad de movimiento** (modificador `add_multiplied_base`), total **+10 %**. Sin
  ralentización.
- **Bonus de set completo** (las 4 piezas puestas): activa las mejoras del cetro (apartado 8): bolas rebotonas gratis y Microondas con más objetivos.
  Se detecta comparando las 4 ranuras de armadura del jugador.
- En 26.3 la armadura necesita su **material** y su **recurso de equipo** (capas 3D del cuerpo y las piernas, 64×32). Hay que
  comprobar el formato exacto al implementarlo.

### 7.2 Poderes de la Corona (casco)
Hace falta llevar la **corona puesta** (no hace falta el set completo). Pocos y con espera, para no romper el servidor.

#### Modo Bola (activo)
- **Cómo:** agachado + correr (a la vez) con la corona puesta.
- **Efecto:** te encoges en una bola y ruedas: velocidad ≈ x2 en suelo, dirección controlable, no puedes saltar. Atropellas a los
  mobs y jugadores enemigos y los lanzas por los aires (empuje ≈ el del golpe a una bola grande), con **4 de daño** por impacto
  (cada mob como máximo una vez por segundo).
- **Protección:** inmune a proyectiles (flechas, bolas de fuego) mientras ruedas.
- **Duración / espera:** 6 s / 30 s. Termina antes si dejas de correr 1 s, o con salto, ataque o pasajero.
- **Cuerpo:** se muestra al jugador como una esfera de aluminio rodando (misma esfera del mod, escalada a su hitbox) y su
  hitbox se reduce.

#### Rebote (pasivo)
- Sin daño de caída (cualquier altura).
- Al aterrizar desde más de 3 bloques **rebotas** una vez con ≈ 50 % de la velocidad de caída (máx. 6 bloques de altura) y un sonido
  de lana. Agacharse al caer cancela el rebote.

#### Reflejo (pasivo)
- El **30 %** de los proyectiles que te alcancen (flechas, tridentes, bolas de fuego, bolas de nieve…) **vuelven** a quien los
  lanzó con la misma velocidad. Los demás los recibes con normalidad.
- Efecto: destello plateado en el punto de impacto y sonido `AMETHYST_BLOCK_CHIME`.

#### Opcionales (a decidir)
- **Decreto real** (agachado + click con la mano vacía): grito de la Reina, empuja a los enemigos a 8 bloques y los marca con
  `Glowing` 10 s. Espera 45 s.
- **Brillo** (pasivo): alumbras un poco a tu alrededor con partículas y los mobs hostiles te detectan a menos distancia.

---

## 8. Cetro del Rey de Aluminio

- **Id:** `theball:aluminum_scepter`. Se apila de 1. Rareza **épica**. **No tiene durabilidad**: su "gasolina" es el papel de aluminio
  del inventario.
- **Modelo:** una vara larga de aluminio con una bola pequeña coronada en la punta (se puede reutilizar la esfera del mod).
- **Munición:** el coste se descuenta del inventario del jugador (sin importar la ranura); si no hay papel suficiente el poder no
  se activa y sale mensaje *"Te falta papel de aluminio"*. En creativo es gratis.

### 8.1 Poderes

| Acción | Poder | Coste | Espera |
|---|---|---|---|
| Click derecho | **Bola rebotona** | 1 papel (**gratis** con el set completo) | 8 ticks |
| Mantener click derecho | **Golazo** | — | 20 ticks tras soltar |
| Agachado + click derecho | **Microondas** | 10 papeles | 60 s |
| Agachado + mantener 3 s (con corona puesta) | **Dios del aluminio** | 50 papeles | 3 min |

#### Bola rebotona
- Proyectil nuevo (`theball:foil_shot`): bolita brillante de ≈ 0,25 bloques, velocidad 1,6 bloques/tick, gravedad baja (0,02).
- **Rebota** hasta **4 veces** contra bloques (refleja la velocidad y pierde 15 % cada vez) y contra mobs.
- Cada impacto contra una entidad: **6 de daño** + empuje. Un mismo mob no recibe daño de la misma bola dos veces seguidas.
- Estela de partículas (`END_ROD`) y sonido de lana/metal al rebotar. Desaparece a los 4 rebotes o a los 5 s.
- No daña a aliados (mismo equipo) ni a mascotas del jugador.

#### Golazo (chute)
- Al **mantener** el click derecho se carga durante 20 ticks (barra de carga); al soltar:
  1. Se busca la **bola del mod más cercana** (cualquiera, sentada o suelta) a ≤ 6 bloques y dentro de un cono de ≈ 20° delante
     de la mira. Si no hay, se cancela sin gastar espera.
  2. Se le aplica un **impulso** hacia la mira: `velocidad = (0,5 + 1,5 × carga) / masa` (la masa es la de la bola, ya existente).
  3. Una bola así lanzada **daña** a las entidades que atraviesa: `daño = clamp(diámetro × velocidad × 6, 2, 40)`, con empuje
     proporcional. Una bola de 8 bloques a tope arrasa casi todo.
- Una bola **sentada en el trono no se puede chutar** (solo con shift + click derecho se retira).
- El daño por chute no se produce hasta que la bola supera velocidad 0,6, para que un toque suave no mate a nadie.

#### Microondas (el papel de aluminio en el microondas)
- Radio 8 bloques alrededor del jugador. Elige hasta **6 objetivos** (mobs hostiles primero, luego jugadores no aliados).
- **Rayos en cadena**: del cetro salta a un objetivo, de ahí al siguiente más cercano, etc. Cada uno recibe **8 de daño** y
  `Glowing` 3 s. Partículas `ELECTRIC_SPARK` y sonido de chisporroteo.
- Con el **set completo** salta hasta **10 objetivos**.

### 8.2 Detalles
- Las esperas usan el sistema de cooldown de items del jugador (se ve la barra gris sobre el cetro).
- Cada poder tiene su **título** en la action bar al usarlo.
- No funciona en modo espectador. En creativo no gasta papel, pero **sí** tiene esperas.

---

## 9. Dios del aluminio: Ascensión de la Reina

Habilidad especial: una **transformación de 5 segundos**. Requisitos: **cetro en la mano** y **Corona puesta** (no hace falta el set completo; el set solo mejora otros poderes).

### 9.1 Cómo se activa
Agachado + mantener click derecho con el cetro durante 3 s (60 ticks). Si sueltas antes, no pasa nada (el Microondas se dispara al
soltar un toque corto con agachado). Gasta **50 papeles** al **completar la carga**; si no hay papel, la carga no arranca.

### 9.2 Máquina de estados (todo por tick de servidor)

| Fase | Duración | Qué pasa |
|---|---|---|
| **Carga** | 60 ticks (3 s) | Columna de luz plateada desde el cielo sobre el jugador; círculo mágico giratorio en el suelo; papeles y chispas orbitan hacia él; campana grave que sube de tono; barra de carga en la action bar. Cancela quien se mueva más de 3 bloques o reciba un golpe fuerte |
| **Ascensión** | 10 ticks | Destello blanco (`FLASH`) y el jugador sube **3 bloques** suavemente (sin gravedad, velocidad vertical controlada). La corona se agranda detrás de la cabeza como un halo |
| **Ráfaga** | 80 ticks (4 s) | Cada **5 ticks** sale una **bola de aluminio explosivo** hacia donde mire el jugador (**16 bolas**). El jugador flota (se mueve lento, puede girar libremente) |
| **Remate** | 20 ticks (1 s) | Las bolas que aún orbitan convergen delante del jugador y forman una **gigante** que explota al final |
| **Descenso** | ≈ 3 s | Efecto `Slow Falling`: baja flotando, sin daño de caída |
| **Espera** | 3 min | Cooldown (estado guardado por jugador, ver 9.5) |

### 9.3 Detalle de las bolas y las explosiones
- **Bola de aluminio explosivo** (proyectil `theball:explosive_shot`): 0,4 bloques, velocidad 2,0, sin gravedad, vida máx. 3 s.
  Explota al chocar con un bloque o un mob:
  - Radio **3 bloques**, **6 de daño** (la mitad al borde), empuje fuerte, sin destruir bloques.
  - Efectos: `EXPLOSION` pequeña, `FIREWORK` plateados y sonido apagado.
- **Bola gigante del remate**: radio **7 bloques**, **30 de daño** en el centro, empuje enorme y onda expansiva visual (anillo de
  `END_ROD` + `EXPLOSION_EMITTER`). Sin destruir bloques.
- **Brasas de aluminio** (termita): tras la explosión gigante quedan 5 s unas partículas de brasas plateadas en el suelo del
  radio; los enemigos que las pisen reciben **2 de daño por tick de fuego** y se prenden. **No prenden bloques ni dañan terreno.**
- **Sin fuego amigo:** nada de esto daña al jugador, sus aliados (mismo equipo) ni a sus mascotas.
- **Protección del servidor:** no se puede usar en zonas con `protect_spawn` si se configura, y no destruye bloques salvo que se
  active `explosive.break_blocks` (por defecto **false**).

### 9.4 Efectos visuales y de sonido

| Elemento | Partícula / sonido (referencias de vanilla) |
|---|---|
| Columna de luz | `END_ROD` en línea vertical + haz del `beacon` simulado con partículas |
| Círculo mágico | Anillo de `ELECTRIC_SPARK` girando, radio 2,5 |
| Aura de ascensión | `END_ROD` + `GLOW` alrededor del cuerpo cada tick |
| Halo | Corona del jugador escalada ×2,5 y rotando lentamente (renderer del casco) |
| Destello | `FLASH` al ascender y al remate |
| Disparos | `FIREWORK` + sonido corto de campana |
| Remate | `EXPLOSION_EMITTER`, anillo de `END_ROD`, trueno lejano |
| Carga | `BELL_RESONATE` con tono creciente |
| Título | Pantalla completa **"¡DIOS DEL ALUMINIO!"** (título y subtítulo) a ti y a los jugadores a ≤ 32 bloques |

- **Luz:** un jugador no puede emitir luz real en Minecraft. El brillo es partículas + el efecto `Glowing`. Alternativa más
  compleja (opcional): bloques de luz invisibles (`light`) que siguen al jugador mientras dura; hay que limpiarlos con cuidado.

### 9.5 Reglas, estado y casos límite
- **Mientras dura:** **Resistencia III** (no invulnerable), no se pueden usar otros items, no se puede interactuar con bloques.
- **Se interrumpe** si el jugador **muere**, **cambia de dimensión**, **se desconecta** o **sube a una montura/vehículo**. El
  cooldown se aplica igualmente a los 3 min desde el inicio de la ascensión.
- **Cooldown persistente por jugador** (por UUID), guardado en el mundo: si te desconectas y vuelves, sigue corriendo.
- **No se acumula**: no puede activarse mientras otra transformación (Modo Bola) esté en marcha.
- **Multijugador:** varios jugadores pueden transformarse a la vez; los efectos de cada uno son independientes.
- **Rendimiento:** limitar partículas por tick y por jugador (config `explosive.max_particles_per_tick`, por defecto 120).

---

## 10. Configuración

Archivo **`config/theball.json`** del servidor (se crea con valores por defecto si no existe). Todo lo "de balance" va aquí para
ajustarlo sin recompilar.

```json
{
  "throne": {
    "require_free_space": true,
    "feed_burst_interval_ticks": 4
  },
  "xp": {
    "milestone_xp": [12,18,26,37,51,69,92,129,201,294,415,598,904,1303,1815,2472,3307,4367,5708,22523],
    "pay_in_creative": false
  },
  "rewards": {
    "min_share_for_loot_percent": 10,
    "min_share_for_medal_percent": 2,
    "once_per_player": false,
    "broadcast_coronation": true,
    "food_pack": { "bocata": 8, "durum": 4 }
  },
  "crown": {
    "ball_mode":   { "enabled": true, "duration_ticks": 120, "cooldown_ticks": 600, "damage": 4 },
    "bounce":      { "enabled": true, "min_fall_blocks": 3 },
    "reflect":     { "enabled": true, "chance_percent": 30 },
    "decree":      { "enabled": false, "radius": 8, "cooldown_ticks": 900 },
    "glow":        { "enabled": false }
  },
  "scepter": {
    "bouncy_ball": { "cost_foil": 1, "free_with_full_set": true, "damage": 6, "max_bounces": 4, "cooldown_ticks": 8 },
    "goal_kick":   { "charge_ticks": 20, "max_target_distance": 6, "max_damage": 40 },
    "microwave":   { "cost_foil": 10, "radius": 8, "targets": 6, "targets_full_set": 10, "damage": 8, "cooldown_ticks": 1200 }
  },
  "explosive": {
    "cost_foil": 50,
    "charge_ticks": 60,
    "barrage_ticks": 80,
    "shots": 16,
    "shot_damage": 6,
    "shot_radius": 3,
    "finale_damage": 30,
    "finale_radius": 7,
    "cooldown_ticks": 3600,
    "break_blocks": false,
    "max_particles_per_tick": 120
  }
}
```

---

## 11. Comandos de administración

Requieren permiso de operador. Útiles para pruebas y para arreglar errores sin tocar el mundo a mano.

| Comando | Qué hace |
|---|---|
| `/theball reload` | Recarga `config/theball.json` sin reiniciar |
| `/theball reward <jugador> <hoja>` | Entrega a mano el premio de 250/500/1000/1500/2000 |
| `/theball cooldown reset <jugador>` | Reinicia todas las esperas del cetro y de la corona |
| `/theball throne info` | (mirando al trono) Muestra hojas, aportes por jugador y recompensas entregadas |
| `/theball throne setsheets <n>` | (mirando al trono) Fija las hojas de la bola sentada (para pruebas) |

---

## 12. Ids, textos y assets nuevos

### 12.1 Ids

| Elemento | Id |
|---|---|
| Bloque e item del trono | `theball:throne` |
| Corona (casco) | `theball:aluminum_crown` |
| Peto | `theball:aluminum_chestplate` |
| Pantalones | `theball:aluminum_leggings` |
| Botas | `theball:aluminum_boots` |
| Cetro | `theball:aluminum_scepter` |
| Medalla del tributo | `theball:tribute_medal` |
| Proyectil de bola rebotona | `theball:foil_shot` (entidad) |
| Proyectil de aluminio explosivo | `theball:explosive_shot` (entidad) |
| Componentes de la bola | `theball:contributions`, `theball:rewards_given` |
| Material de armadura | `theball:aluminum_king` |

### 12.2 Textos nuevos (es_es / en_us)

| Clave | Español |
|---|---|
| `block.theball.throne` | Trono de aluminio |
| `item.theball.aluminum_crown` | Corona de aluminio |
| `item.theball.aluminum_chestplate` | Peto del Rey del Aluminio |
| `item.theball.aluminum_leggings` | Pantalones del Rey del Aluminio |
| `item.theball.aluminum_boots` | Botas del Rey del Aluminio |
| `item.theball.aluminum_scepter` | Cetro del Rey de Aluminio |
| `item.theball.tribute_medal` | Medalla del tributo |
| `message.theball.throne.no_space` | No hay espacio: la bola necesita un hueco de %s × %s × %s bloques |
| `message.theball.throne.status` | Reina de Aluminio · %s / 2000 hojas · Tu aporte: %s % |
| `message.theball.coronation` | ¡%s ha coronado a la Reina de Aluminio! |
| `message.theball.scepter.no_foil` | Te falta papel de aluminio |
| `message.theball.explosive.title` | ¡DIOS DEL ALUMINIO! |

### 12.3 Assets
- **Texturas 16×16:** cetro, medalla, 4 iconos de armadura, trono (item).
- **Texturas de bloque:** trono (varias caras, 16×16 o modelo con elementos).
- **Armadura puesta:** 2 capas (cuerpo y piernas) de 64×32.
- **Corona de la bola:** textura 64×64 con las 5 variantes (plata, +1, +2, +3 joyas, dorada).
- **Esfera del Modo Bola:** reutiliza `textures/entity/aluminum_ball.png`.
- **Proyectiles:** textura pequeña brillante (16×16) cada uno.
- **Imágenes de crafteo:** solo el **trono** tiene receta en mesa; ver `CLAUDE.md` para generarla con el script. Los items de
  recompensa no llevan receta (se documentan como tabla).
- Las texturas se generan con Python/Pillow, como las actuales (`tools/generate_textures.py`), para mantener el estilo.
- **Sonidos:** se reutilizan los de vanilla (campana, yunque, lana, fuegos). No hace falta añadir audio.

---

## 13. Fases de implementación

Cada fase debe quedar **jugable y publicable** por separado, con README actualizado, imágenes de crafteo, prueba en el juego y su
commit. Orden recomendado (cada una depende de la anterior):

### Fase 1 — Trono, XP y datos (la base)
- [x] `ThroneBlock` + `ThroneBlockEntity` (id, modelo, receta, loot, tags de pico, resistencia).
- [x] Ancla en `AluminumBallEntity` y lógica de sentar/retirar (sin física, recolocar al crecer).
- [x] Comprobación de espacio libre (apartado 3.4) con mensaje.
- [x] Renderer: corona sobre la bola con las 5 variantes y escala.
- [x] Componentes `contributions` y `rewards_given`; paso item⇄entidad; fusión en el yunque actualizada.
- [x] Registro de aportes en `onPlayerUse` (siempre, salvo creativo).
- [x] Pago de XP por hoja (solo trono) con acumulación fraccionaria y tabla en config.
- [x] Feedback: action bar, fanfarria por hito, mensaje de estado con la mano vacía.
- [x] Config `theball.json` (carga, valores por defecto, `/theball reload`).
- **Criterio de aceptación:** una bola se sienta en el trono, crece con papel, paga el XP esperado (comprobar con la tabla del
  apartado 5.2), la corona cambia de aspecto, y retirarla y volver a sentarla no repite nada.

### Fase 2 — Recompensas, armadura y coronación
- [x] Items de armadura (material, recurso de equipo, atributo de velocidad, texturas y modelos).
- [x] Entrega de premios por umbral (apartado 6.2), incluyendo pendientes para jugadores desconectados.
- [x] Secuencia de coronación (apartado 6.3), mensaje global y medalla.
- [x] Poderes de la Corona: Modo Bola, Rebote, Reflejo.
- [x] Detección de set completo (para las mejoras del cetro de la fase 3).
- [x] Textos en `es_es` y `en_us`; entradas en la pestaña de creativo.
- **Criterio de aceptación:** con `/theball throne setsheets` se recorren los umbrales y los premios llegan una vez, solo a
  quien cumple el porcentaje; cada poder de la corona funciona y respeta su espera.

### Fase 3 — Cetro
- [x] Item del cetro, modelo y cooldowns.
- [x] Entidad `foil_shot` (proyectil rebotón) y su render.
- [x] Golazo: búsqueda de bola en cono, impulso y daño según tamaño.
- [x] Microondas: selección de objetivos y rayos en cadena.
- [x] Coste en papel desde el inventario, con las excepciones del set completo.
- **Criterio de aceptación:** los tres poderes funcionan, descuentan el papel indicado, respetan las esperas y no dañan a aliados.

### Fase 4 — Dios del aluminio
- [x] Estado de habilidad por jugador (carga, ascensión, ráfaga, remate, descenso, espera) persistente.
- [x] Entidad `explosive_shot` y la bola gigante del remate (explosión sin destruir bloques).
- [x] Efectos (partículas, sonidos, título en pantalla, halo de la corona).
- [x] Interrupciones (muerte, dimensión, desconexión, vehículo).
- [x] Límite de partículas (`max_particles_per_tick`). [ ] Pruebas de rendimiento con varios jugadores: pendientes.
- **Criterio de aceptación:** una activación completa dura lo previsto, no rompe bloques, no daña aliados y no deja efectos colgados
  si el jugador se desconecta a mitad.

---

## 14. Plan de pruebas

Pruebas manuales (no hay tests automáticos en el proyecto). Usar `/theball throne setsheets` y el creativo para ahorrar papel.

**Trono y bola**
- [ ] Sentar una bola de 9, 500, 1000 y 2000 hojas: queda apoyada por la base, centrada, sin moverse.
- [ ] Alimentar con y sin hueco libre: sin hueco, rechaza la hoja, no gasta papel y avisa del tamaño necesario.
- [ ] Mantener click con papel: alimenta en tanda y se corta al faltar papel o al soltar.
- [ ] Retirar con shift + click: el item conserva hojas, aportes y recompensas.
- [ ] Romper el trono con la bola sentada: la bola se libera con física.
- [ ] Reiniciar el servidor / descargar y volver a cargar el chunk con la bola sentada: sigue sentada.
- [ ] La corona cambia en 500, 1000, 1500 y 2000.

**XP**
- [ ] Un jugador añade una hoja a mitad de cada hito y se verifica el XP contra la fórmula.
- [ ] Dos jugadores alimentan alternando: cada uno recibe el XP de sus propias hojas.
- [ ] Creativo: no recibe XP ni se anotan aportes.
- [ ] Alimentar fuera del trono y luego sentar: no paga XP retroactivo y no se puede cobrar dos veces.

**Recompensas**
- [ ] Cruzar 250/500/1000/1500/2000 entrega el premio una sola vez; el bit de `rewards_given` queda guardado.
- [ ] Jugador con 9 % de aporte no recibe botín; con 10 % sí; con 2 % recibe la medalla.
- [ ] Jugador desconectado recibe el premio pendiente al volver y usar el trono.
- [ ] Fusión de dos bolas en el yunque: aportes sumados, premios no repetidos.

**Corona, cetro y habilidad**
- [ ] Rebote: sin daño de caída desde 50 bloques; agacharse cancela el rebote.
- [ ] Reflejo: ≈ 30 % de las flechas vuelven (probar con muchas).
- [ ] Modo Bola: atropella, no puede saltar, termina a tiempo, respeta la espera.
- [ ] Bola rebotona: rebota 4 veces, no daña aliados.
- [ ] Golazo: chuta una bola grande y daña según su tamaño; una bola sentada no se puede chutar.
- [ ] Microondas: encadena objetivos; sube a 10 con el set completo.
- [ ] Dios del aluminio: sin corona o sin cetro no arranca; con papel insuficiente no arranca; se interrumpe bien al morir/cambiar de
  dimensión/desconectarse; no rompe bloques; el cooldown sobrevive a un reinicio.

**Multijugador y rendimiento**
- [ ] 3 jugadores usando Dios del aluminio a la vez: sin lag grave (ver `max_particles_per_tick`).
- [ ] Una Reina de 8 bloques es visible desde lejos y no desaparece al girar la cámara (comprobar el rango de rastreo y el
  *frustum culling* del renderer).

---

## 15. Riesgos y casos límite

| Riesgo | Mitigación |
|---|---|
| **API de 26.3** (render, equipo de armadura, atributos, componentes) distinta a lo que se documenta | Compilar pronto cada fase y corregir sobre el error real; no fiarse de las firmas de este documento |
| La bola de 8 bloques se corta o desaparece en el cliente (culling del renderer, tamaño de la caja) | Revisar el rango de rastreo (`clientTrackingRange`) y ampliar la caja de culling del renderer para bolas grandes |
| El trono se queda sin espacio y el jugador no entiende por qué no crece | Mensaje claro con el tamaño necesario y, si se quiere, partículas que marquen el volumen necesario |
| Griefing (alguien rompe el trono) | Resistencia alta; la bola se libera; el premio y los aportes no se pierden (viajan con la bola) |
| Abuso con el creativo | No paga XP ni anota aportes; el comando de reward es solo para operadores |
| Cobrar dos veces | El XP no tiene estado: cada hoja se paga una vez al añadirse; los premios llevan máscara de bits en la bola |
| Jugadores desconectados al coronar | Premios pendientes guardados en el trono |
| Habilidades demasiado fuertes en PvP | Esperas largas, coste en papel y daños configurables; se pueden desactivar poderes uno a uno |
| Demasiadas partículas | Límite configurable por tick y por jugador |
| Cambio de config a mitad de partida | Solo afecta al futuro; no se recalcula nada pagado |

---

## 16. Decisiones abiertas

- [ ] **XP sin término por papel**: la curva geométrica (1,5 → 10 → 30 niveles) deja el tramo 500–1000 con poco XP por papel. ¿Se compensa con
  un extra por papel solo a partir del hito 6?
- [ ] **Receta del trono** (propuesta: 7 bloques de aluminio + 1 bloque de oro).
- [ ] ¿Incluir *Decreto real* y *Brillo* entre los poderes de la corona?
- [ ] ¿`once_per_player` para el botín final (por defecto no)?
- [ ] ¿Luz real para el aura de Dios del aluminio (bloques de luz invisibles) o solo partículas?
- [ ] ¿Bolas rebotonas gratis solo con set completo, o siempre gratis?
- [ ] ¿Se permite alimentar la bola sentada con hoppers/dispensers (automatización)? Por defecto **no**.
- [ ] ¿Puede haber varios tronos en un servidor y varias Reinas? Por defecto **sí**: cada bola es independiente.
- [ ] Ajuste fino de daños, esperas, costes y valores de XP tras probar en el servidor.

> **Valores elegidos al implementar la v1.1.0** (todos cambiables en `config/theball.json`): receta 7 bloques de aluminio
> + 1 de oro · *Decreto real* implementado pero **desactivado** · *Brillo* no implementado · `once_per_player` = no ·
> solo partículas (sin bloques de luz) · bolas rebotonas gratis **solo con set completo** · sin hoppers/dispensers ·
> varios tronos y varias Reinas: sí · tramo 500–1000 sin XP por papel (curva decidida así).

---

## 17. Implementación (v1.1.0)

Todo lo de las fases 1–4 está programado y **compila** contra Minecraft 26.3 / Fabric API 0.165.0; el servidor arranca
y registra todo. **Falta la prueba en el juego** (checklist del apartado 14). Dónde está cada cosa: ver la sección
«Para desarrolladores» del [README](README.md).

### Cambios respecto al diseño

| Diseño | Implementación | Por qué |
|---|---|---|
| XP acumulado y entregado al soltar | Se entrega el XP entero **en cada hoja** y se guardan los decimales en memoria | Más simple y el jugador ve subir la barra mientras alimenta; mismo total |
| Premio solo con ≥ 10 % | Igual, y **si nadie llega**, se lo lleva el mayor aportador (`rewards.reward_top_if_none`) | Con muchos jugadores repartidos podía quedarse sin dueño la armadura |
| Medalla con ≥ 2 % | Igual; los pendientes usan el bit 5 en `pending_rewards` | Para poder dar la medalla a desconectados |
| Decreto real: agachado + click con la mano vacía | **Agachado + saltar** | El servidor no se entera de un click al aire con la mano vacía (vanilla no lo envía) |
| Esperas en el cooldown del item | Bola rebotona y Golazo: cooldown del item. **Microondas**: espera propia en memoria (se reinicia con el servidor). Modo Bola, Decreto y Dios del aluminio: guardadas en el jugador (attachment `theball:power`) | Un único cooldown de item habría bloqueado la bola rebotona 60 s tras un Microondas |
| Modo Bola: hitbox reducida y render de esfera | Atributo vanilla `scale` × 0,5 + entidad visual (`theball:power_visual`) que el cliente dibuja en la posición interpolada del jugador | Sin mixins de render (lo más frágil entre versiones) |
| Halo: render del casco | La misma entidad visual dibuja la corona de oro gigante sobre la cabeza | Igual que arriba |
| Remate: la bola gigante explota delante del jugador | Se forma delante y **sale disparada**; explota al chocar (o a los 3 s) | Queda más espectacular y se puede apuntar |
| Carga de 3 s | La carga empieza tras 6 ticks manteniendo (para distinguirla del toque del Microondas): ~3,3 s en total | — |
| Armadura puesta | Texturas propias de 64×32; la Corona puesta es un aro con puntas y joyas pintado en la capa del casco | — |

### Extras que no estaban en el diseño
- **Barra de jefe** de la Reina con 20 marcas (una por hito) para los jugadores cercanos mientras alguien alimenta.
- **Ranking del tributo**: holograma (`text_display`) con el top 3 de aportes sobre la bola.
- **Partículas rojas** que dibujan el cubo que falta cuando no hay espacio.
- Estela de chispas verdes de la bola al jugador al cobrar XP; título «¡LARGA VIDA A LA REINA!» al coronar.
- **Golazo real**: si una bola chutada con el cetro entra en una portería, celebración especial.
- **Logros** propios (pestaña The Ball): Su Majestad la Bola, Mecenas, ¡Larga vida a la Reina!, Golazo real y ¡DIOS DEL ALUMINIO!
- Tooltip de la bola con diámetro y top 3 de aportes; tooltip de la medalla con nombre, papeles y %.
- `build.gradle`: arreglados `processResources` y `jar` para que funcionen con la caché de configuración de Gradle.

## 18. Cambios tras las primeras pruebas (v1.1.0)

- **Sentar una bola ya grande**: no cobra los hitos anteriores (250/500/1000/1500); solo se premia lo que se cruza sentada.
- **Decreto real**: activo por defecto (`crown.decree.enabled = true`; las configs antiguas se migran con `configVersion`).
- **Modo Bola**: se entra y se sale con **doble toque de Shift** (quieto), sin duración ni cooldown.
- **Golazo** eliminado. Mantener click derecho = **Bola explosiva** (5 papeles, daño 8, radio 3,5, espera 2 s).
- **Microondas** eliminado (muy fuerte). Lo sustituye la **Embestida** (agachado + toque: impulso, 2 papeles, 5 s).
- La corona sobre la bola es **siempre de oro** (con puntas en pirámide y molduras); los iconos de corona, armadura,
  cetro y medalla se han rehecho (`tools/generate_king_textures.py`, sombreado de bordes + contorno).
- El logro oculto «Golazo real» ya no se puede conseguir con el cetro (la lógica de `GoalEntity.celebrateRoyal` sigue ahí).
- **Modo Bola**: saltos grandes (salto ×1,9) y rebote al caer (80 %, hasta 9 bloques); cámara automática en tercera persona (la esfera no se dibuja en primera persona, desde dentro se veía el interior).
- **Cetro 3D**: modelo de cubos (`tools/generate_scepter_model.py`: palo con anillas, bola, corona) en la mano; icono 2D en inventario/suelo/marco. Pose de reposo inclinada y, al cargar/lanzar, con la corona apuntando al frente.
- **Dios del aluminio rediseñado**: tras la ascensión hay un modo de 20 s con 8 bolas orbitando (cargas): click = bola, mantener = salva de 3, agachado + click = golpe al suelo con subida automática; remate final con la bola gigante. Config: `explosive.mode_ticks`, `orbs`, `salvo_shots`, `slam_*`, `orbit_damage`.
- **Armadura**: iconos que ocupan todo el lienzo 16x16; en Modo Bola la esfera es mayor (tapa cuerpo, botas y corona).
