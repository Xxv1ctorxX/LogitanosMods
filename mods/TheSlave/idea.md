# The Slave — documento de diseño

> Mod de coña: un tipo igual que un jugador que trabaja para ti de verdad. No invoca items, los va a buscar al mundo.
> Sale duende, hombre o mujer, al azar y a partes iguales. Se le paga con pan, y como no se lo des se arrastra.
> **Es débil y vulnerable, y sobre todo servicial**: nunca se rebela, nunca protesta, nunca miente y nunca negocia.
> Si algo va mal, se disculpa.

**Estado: diseño, todavía sin implementación en esta carpeta. Revisión: 5 de noviembre de 2026.**
Este documento define el comportamiento esperado; los nombres de API son orientativos hasta verificarlos en M0.
La base local de referencia es TheBall: Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3 y Java 25.
Son las versiones declaradas en el repositorio, no una comprobación de disponibilidad o compatibilidad de TheSlave.

**Lectura rápida:** ciclo y gestos (5), capacidades y límites (6), regreso y ritual (10), implementación (15),
hitos y pruebas (19-20), eficiencia sin pérdida de capacidades (24), mapas y saludo (25), estrategia minera y
cuevas con antorchas (26). La sección 23 concreta experiencia, transacciones y recuperación de errores.
Las decisiones de producto están en 17; una API propuesta no se considera validada por estar escrita aquí.

---

## 1. La idea en una frase

Le das un objeto, le das un latigazo y el esclavo se marcha a conseguirlo: mina, tala, caza y craftea lo que haga falta,
aprovechando tus hornos y mesas si los tiene cerca, y vuelve con lo que haya encontrado o negando con la cabeza si no pudo.

## 2. Invariante del mod (la regla que no se rompe nunca)

**Cada item que trae el esclavo tiene un origen real y contabilizado.** Hay cinco fuentes válidas:

- `Block.getDrops(...)` de un bloque que ha roto él.
- El botín de un mob que ha matado él.
- Una receta vanilla o del mod, ejecutada consumiendo ingredientes reales.
- Una entrega explícita del dueño: referencia, equipo o suministros, sin contarlos como producción nueva.
- Una extracción autorizada del cofre asignado, descontando exactamente lo que se incorpora.

Crear un `ItemStack` para representar una salida de receta es válido; crearlo sin consumir su fuente no lo es.
Cada operación conserva objetos, componentes y sobrantes; la referencia nunca se consume ni cuenta para el objetivo.
Un enum de procedencia ayuda a auditar, pero no impide que un llamador mienta: las pruebas verifican entradas y salidas
reales (15.8 y 23.4). Eso hace que el mod sea un juego y no un botón de "dame hierro".

## 3. Quién trabaja

**Nombre**: el que le pongas al adoptarlo, y no hay otro. Va sobre su cabeza, **visible para cualquiera que lo mire**,
como la etiqueta de un animal. Id de entidad: `theslave:esclavo`.

**Aspecto**: **un jugador**. Mismos brazos y piernas, mismo esqueleto, misma manera de caminar. Hay tres versiones
—duende, hombre y mujer— y cada contrato saca una al azar, con las mismas probabilidades. El hombre usa el modelo de
jugador de toda la vida. **El duende y la mujer no**: cada uno tiene modelo propio (3.1), el duende encorvado y con las
piernas cortas, la mujer con figura femenina adulta. Los tres usan skin de jugador de 64 × 64 y **la skin se elige de una
carpeta del servidor** (3.2): sueltas ahí cualquier skin de jugador descargada y entra en el bombo, sin configurar nada.

Como son jugadores, se comportan visualmente como uno: caminan con el ciclo de brazos y piernas de siempre, miran a los
lados, se agachan, giran la cabeza para negar y saludan con la mano cuando vuelven con éxito. El gag de "hay un tipo ahí
trabajando mientras tú miras" funciona porque no parecen bichos, parecen jugadores más del servidor.

### 3.1 Las tres versiones

| Versión | Prob. | Tamaño (ancho × alto) | Modelo | Skin de reserva | Voz |
|---|---|---|---|---|---|
| Duende | 1/3 | 0.5 × 0.95 | **propio, encorvado** | por defecto: piel verde apagada, orejas puntiagudas pintadas, ropa remendada | aguda y cascada |
| Hombre | 1/3 | 0.6 × 1.8 | jugador de toda la vida | por defecto: jugador normal, ropa gastada y cinturón de cuerda | normal |
| Mujer | 1/3 | 0.6 × 1.8 | **propio, figura femenina** | por defecto: jugador normal, ropa gastada y cinturón de cuerda | normal, un punto más aguda |

La tirada se hace **una sola vez**, al gastar el contrato, con la misma probabilidad para cada una y **fija en código**.
No hay ajuste de servidor que la mueva: al contrato le toca lo que le toca. Queda guardada en
NBT: sobrevive a recargar el mundo, a morir y a resucitar en el ritual de la tumba (10.6). El que te tocó es el que te queda, y no hay
manera de cambiarlo sin romper el contrato y gastar otro.

**El duende sigue siendo un jugador, pero encorvado.** Comparte esqueleto y animaciones con el hombre, y su skin también
es una skin de jugador normal, así que cualquier PNG de 64 × 64 le vale. Lo que cambia es la postura: va **inclinado
hacia delante**, con la espalda curvada, la cabeza adelantada, los brazos más largos que las piernas y las rodillas
dobladas. Se lee como un duende de cuento y no como un jugador enano recortado, que es lo que sale al escalar el modelo
de jugador sin más. Camina con su propio ciclo, más rápido y con más bamboleo, y conserva las animaciones de saludar y
afirmar.

Lo que cambia el duende en el juego, y solo el duende:

- Corre un 20 % más (0.31 en vez de 0.26) y tiene 12 de vida en vez de 16.
- Gasta la mitad de hambre, así que aguanta muchas más horas de trabajo con el mismo pan.
- Con 0.95 de alto **cabe por túneles de un bloque**, así que excava galerías estrechas y llega a vetas donde tú no
  entras sin picar. Esa es su ventaja real y la razón para querer que te toque uno.
- Con los brazos colgando por delante casi le llegan a la rodilla, así que minar le sale natural, pero pega menos: 1 de
  daño con el puño.

**Hombre y mujer son idénticos en estadísticas a propósito.** Lo que cambia es la skin, el cuerpo, el tono de voz y cómo
hablan. Nada de bonificaciones por género: no aporta nada al chiste y sobra.

**El modelo de la mujer.** Es el que más se aparta del jugador: donde el duende cambia la postura, la mujer cambia las
proporciones. Lleva torso con cintura, caderas más anchas que los hombros, pecho con volumen redondo, no cuadrado y
piernas algo más estrechas, o sea, la figura de una mujer adulta normal que trabaja al aire libre.

El cuerpo está hecho para que **la ropa y la armadura le sigan sentando bien**. Las cajas nuevas reutilizan el UV del
torso de la skin de 64 × 64, así que la textura que ya tiene dibujada el torso le cubre también la cintura y las caderas
sin tocar el PNG, y cualquier skin de jugador vale igual. En el duende pasa lo mismo: la cadera y la joroba repiten ese UV,
así que la camisa que lleve dibujada la skin le sube por la chepa sin cortes. El detalle técnico está en 15.2.

El hitbox no cambia: **0.6 × 1.8, la misma caja que el hombre**. Si el cuerpo se viera más ancho del lado de las
caderas, se ve, pero no pasa nada por ahí. En el juego se comporta igual que el hombre, misma velocidad, misma vida,
mismo daño, mismo hambre.

### 3.2 La skin es un PNG que sueltas en una carpeta

No hay fichero de configuración, ni lista, ni JSON, ni nombres que casar. **Todo PNG que esté en la carpeta es una skin
posible.** Se suelta un archivo y ya está: el siguiente contrato puede sacarlo.

La primera vez que arranca el mod crea esto y **escribe dentro las tres skins del mod**, así que la carpeta nunca está
vacía y las tres se pueden abrir en Paint y repintar:

```
config/theslave/skins/
  duende/
    duende_por_defecto.png
  hombre/
    hombre_por_defecto.png
  mujer/
    mujer_por_defecto.png
  comun/                          opcional, la crea vacía
```

Cómo funciona:

- Se tira la versión del contrato (1/3 cada una), y después **se elige un PNG al azar de la carpeta de esa versión**, con
  la misma probabilidad para todos los archivos que haya.
- **`comun/` vale para las tres.** Lo que sueltes ahí entra en el bombo de duende, hombre y mujer a la vez. Es el atajo
  cuando te bajas cuarenta skins y no quieres repartirlas en tres carpetas.
- **Duplicar un archivo con otro nombre lo hace más probable.** Si quieres que una skin salga el doble, cópiala dos veces
  con nombres distintos. No hay pesos ni números que aprender.
- El nombre del archivo da igual, y las mayúsculas también.

**Compatible con skins de jugador, literalmente.** Vale cualquier PNG que la web de turno te dé para tu skin de
Minecraft: los 64 × 64 de ahora y **también los 64 × 32 antiguos**, que se convierten al formato nuevo igual que hace el
propio juego. No hay que recortar, ni renombrar, ni pasar por un editor. Te bajas la skin, la arrastras a la carpeta y
aparece en el siguiente esclavo. Lo único que no vale es un PNG que no sea cuadrado de skin: se ignora con una línea en
el log.

Límites, todos para que un archivo mal puesto no tumbe nada:

- **PNG de 64 × 64 o 64 × 32.** Cualquier otro tamaño se ignora.
- **32 KB por archivo.** Una skin real pesa 2 o 3 KB, así que el límite solo salta si alguien mete una foto.
- **Carpeta vacía o archivo roto** → se usa la skin por defecto que va dentro del jar. El mod nunca se queda sin textura
  y nunca se cae por una skin.

La carpeta es la de la instancia, así que en un mundo de un jugador es la misma que verías en cualquier mod con ajustes.
Si el mod no puede crearla (instalación de solo lectura), lo dice una vez en el log y tira de las skins del jar.

**Cómo llega la skin al cliente.** El cliente necesita los píxeles para dibujar y no puede leer la carpeta del servidor,
así que se los manda el servidor: lee el PNG, lo mete en un paquete y el cliente lo guarda en su caché de texturas. Se
pide **una vez por skin** y el id es el hash de sus píxeles ya normalizados (15.16), así que dos esclavos con la misma skin comparten una sola
textura y un solo envío. Un servidor con veinte esclavos manda las skins distintas y nada más. **Ni el servidor ni el
cliente llaman a ninguna web**: nada de descargar de Mojang ni de NameMC en tiempo de ejecución, que eso en un servidor
público es un problema y aquí no hace falta.

**Cuándo se lee.** Al cargar el mundo y con `/theslave skins recargar` (solo operadores). Un esclavo ya creado **no
cambia de skin**: guarda la suya en NBT, igual que la variante. Tocar la carpeta afecta a los contratos siguientes, no a
los que ya están trabajando. Es a propósito: si el admin cambia las skins, tu Paco no amanece siendo otro.

Una cosa que conviene saber: **el modelo lo decide la versión, no la skin.** Si metes una skin dibujada para el cuerpo de
hombre en la carpeta de mujer, se pinta sobre la silueta de mujer. Funciona, se ve raro, y es cosa de quien elige el
archivo.

Cada versión habla a su manera. El mismo fracaso no suena igual:

| Motivo | Duende | Hombre | Mujer |
|---|---|---|---|
| No hay mena | "He mirado todo lo que alcanzo, jefe. No hay." | "He mirado por todas partes. No he encontrado nada." | "He bajado hasta el fondo. Lo siento." |
| Le pides un imposible | "No sé llegar a eso, jefe. No llego." | "Yo... no sé hacer eso. Perdona." | "No puedo. Perdóname." |

**Cuerpo**: `PathfinderMob` de 0.6 × 1.8 (hombre y mujer) o 0.5 × 0.95 (duende), con inventario propio de 12 huecos,
una ranura de **referencia** (el objeto que le entregas como encargo), una ranura de **mano** y cuatro ranuras de
**armadura**. Lleva puesto lo que le pongas (agachado + clic derecho): si le vistes con hierro aguanta más y si le das
una espada pega más, pero no se la queda para siempre, la suelta al morir como cualquier jugador. Al duende también le
vale la armadura, encogida con el cuerpo.

**Stats de partida** (hombre y mujer):
- Vida: 16 (8 corazones).
- Velocidad: 0.26 (algo más lento que un jugador esprintando).
- Ataque: 2 con el puño, más lo que lleve encima.
- Hambre propia: sí, con barra de 20 puntos, ver 9.2. No le hace desobedecer, solo ir más lento.
- Sigue al dueño a hasta 24 bloques, se teletransporta si se queda a más de 48 y hay línea de visión, siempre que el
  dueño esté en su misma dimensión. Si el dueño está en el Nether o en el End no hay teletransporte que valga: se va a
  casa y espera allí (10.4).

Si te toca duende: 12 de vida, velocidad 0.31, 1 de daño con el puño y la mitad de gasto de hambre.

**Cómo se consigue**: item `theslave:contrato` (pergamino con letra pequeña). Botón derecho sobre un bloque sólido:
se consume, **sortea la versión** y aparece el esclavo de pie, delante de ti, mirándote. En ese momento no sabe hacer
nada, está en blanco y **no es de nadie**: sin dueño, sin nombre y sin etiqueta. Un tío de pie en mitad del campo, igual
que cualquier jugador del servidor. Ese es el gag: no parece un bicho raro, parece alguien que se ha perdido.

**Cómo se adopta: un latigazo y un nombre.** No hay menú de "reclamar" ni clic derecho amable. Se adopta como se adopta
todo aquí, con el látigo:

1. Clic derecho con el látigo sobre un esclavo **sin dueño**. Suena el chasquido y se queda quieto, mirándote.
2. Se abre una pantalla con una sola casilla: **"¿Cómo se llama?"**. Escribes el nombre y aceptas. No hay botón de
   "luego": o hay nombre o no hay esclavo.
3. Al aceptar, el nombre se le pone **sobre la cabeza, visible para todo el que lo vea**, como la etiqueta de un animal:
   a través de las paredes y hasta 64 bloques. Y desde ese momento es tuyo y te obedece.

El nombre **no se puede cambiar nunca**. Ni con etiqueta, ni con yunque, ni con otro contrato. Es la letra pequeña que
firmaste: "no se admiten devoluciones ni cambios de modelo". Piénsalo antes de escribir, porque te lo vas a comer el
resto de la partida y **todas sus líneas de chat salen con ese nombre delante**, como las de un jugador más del
servidor: `<Paco> Aquí está. ¿Hace falta algo más?`

Si cierras la pantalla sin escribir nada, el esclavo se queda ahí, sin dueño y sin etiqueta, mirándote sin entender, con
la boca abierta y las manos delante. No se gasta nada y puedes volver a darle el latigazo cuando quieras. Mientras no
tenga nombre no es de nadie y no acepta órdenes, ni tuyas ni de nadie: trabaja el primero que lo bautice.

**Propiedad**: el esclavo pertenece a un UUID y el dueño es quien le puso el nombre. Solo él puede darle órdenes y solo
él ve los informes. Se puede transferir (12.1) y el nombre se queda: un
esclavo transferido conserva el que le puso el primero. Un esclavo ajeno te mira, baja la cabeza y no se mueve: no es
suyo y lo sabe.

## 4. Los objetos

### Contrato de servidumbre

Receta: 3 papel + 1 saco de tinta + 1 pluma. Sale 1.

Describe la letra pequeña en el tooltip: "El firmante renuncia a su tiempo libre, a su dignidad y a la propiedad de
todo lo que encuentre. No se admiten devoluciones ni cambios de modelo."

La versión no se elige: al gastarlo se tira un dado y sale duende, hombre o mujer. Un tercio cada uno. Y ya que el
tooltip promete que no se admiten cambios de modelo, se cumple: te quedas con el cuerpo que te toque.

**El contrato no te lo da: te lo pone delante.** Aparece sin dueño y sin nombre, y ahí se queda hasta que le des el
primer latigazo (sección 3). El contrato trae al tipo, el látigo lo bautiza y se lo queda.

### Látigo

Receta: 1 palo + 2 cuero + 1 hilo en diagonal. Sale 1. Durabilidad 64 (ver "Durabilidad del látigo" más abajo).

Es a la vez el mando a distancia y el chiste. Sin él no hay órdenes **y sin él no hay esclavo: el látigo es lo que
adopta** y lo que te obliga a ponerle nombre (sección 3). Tiene 1 segundo de enfriamiento para que no se pueda espamear
el clic.

Además de confirmar, **marca el techo**: un latigazo, dos o tres, y cuantos más des, más trabajo cae (sección 5). Con el
enfriamiento de un segundo, pedir "todo lo que encuentres" son tres segundos de chasquidos y de vergüenza ajena, que es
exactamente el tono del mod.

Y cuando ya está trabajando sirve para otra cosa: **le da un latigazo de miedo y acelera**. Poco, acumulable y con
tope de ×3 (9.3). Sirve para espabilar a un esclavo que se arrastra por hambre, no para tener un esclavo a toda
velocidad todo el rato.

**Y sirve para cobrar.** El esclavo no te suelta nada por las buenas: cuando vuelve se planta delante con la carga
encima y espera. Un latigazo y te lo pasa todo al inventario (sección 5). Es el único gesto del mod y sirve para
todo: adoptar, pedir, acelerar y cobrar. Para dar órdenes no hay menú, no hay tecla y no hay comando. (Sí hay una
pantalla de equipo para vestirlo, 15.14, pero no sirve para mandar ni para sacarle la carga.) La tabla completa de qué
hace cada gesto está en 5.2.

### Durabilidad del látigo

64 usos. Cada latigazo gasta 1, con dos excepciones: **el de cobrar es gratis** (es el trato, no maltrato) y el de
repetir orden (5.2) cuenta como los de confirmar. Un ciclo normal (tres latigazos de pedir + uno de cobrar) cuesta 3, así
que salen ~20 órdenes por látigo. Zurrarle en camino gasta durabilidad igual que cualquier otro, hasta el tope de 9.3,
y ahí está el freno natural al abuso. Se repara en yunque con cuero (`repairIngredient = LEATHER`). Al romperse
suena el chasquido de fin y el chat dice *"Se me ha acabado el látigo, jefe. Perdona, no era mía la culpa."* dicho por el
esclavo, que es lo más servil que existe.

## 5. El ciclo de la orden

Antes de todo esto, **la adopción** (sección 3): un latigazo y un nombre. Un esclavo sin nombre no recibe órdenes, así
que el ciclo empieza cuando ya se llama como tú decidiste.

Estados internos de la entidad: `OCIOSO → RECIBIENDO → PLANIFICANDO → EN_COLA → PREPARANDO → TRABAJANDO → VOLVIENDO → INFORMANDO`, y
`ESPERANDO` cuando no estás y le toca esperar en casa (10.4).

1. **Dar la orden.** Clic derecho sobre el esclavo con un item en la mano: **se lo estás entregando, no enseñando**.
   **Una unidad** del montón pasa a su ranura de **referencia** (el resto se queda contigo, no se juega un stack de
   diamantes a que el esclavo se muera) y queda flotando sobre su cabeza. Solo cuenta el tipo de item: si lo que
   entregas está encantado, nombrado o tiene componentes de valor, lo rechaza con *"Eso es demasiado bueno para mí, jefe.
   Dame uno normal."*
   El esclavo lo mira, lo huele, lo muerde. Ese item dice **qué** quiere. Chat: *"El esclavo apunta: Lingote de hierro.
   ¿Cuántos latigazos?"*
2. **Marcar el techo y confirmar.** Clic derecho con el látigo una, dos o tres veces:

   | Latigazos | Techo | Qué significa |
   |---|---|---|
   | 1 | 10 (o el tamaño de stack, si apila a menos) | "Diez y para". Item que no apila: 1 |
   | 2 | 1 stack | Hasta 64 (o 16 si apila a 16). Item que no apila: 4 |
  | 3 | sin techo solicitado | "Vacía lo que encuentres". Lo frenan el reloj, la capacidad y los topes de 6.8. Item que no apila: 8 |

   **El límite de tiempo es el mismo para los tres** (30 minutos, sección 6.8). Lo que cambia es el techo, y la regla es
   la del tono del mod: más latigazos, más trabajo. Con uno se trae diez y vuelve pronto; con dos, un stack; con tres se
   queda hasta agotar el tiempo o chocar con el tope duro. Un latigazo al hierro es "diez y para", dos es "llégame a un
   stack" y tres es "vacía lo que encuentres". (Esto resuelve la duda 18: los techos estaban al revés.)

   Suena el chasquido, el esclavo se estira, aprieta la referencia contra el pecho y se va. Cómo se cuentan los
latigazos y cuándo arranca la orden está en 5.1: uno que le des después de que haya arrancado, en mitad del trabajo, ya
no es una orden, es aceleración (9.3) y maltrato (9.1).
3. **Planificar.** Calcula de forma incremental cómo conseguirlo (sección 6) y lo canta en el chat para que sepas qué está
   haciendo: *"Necesito un pico de piedra. Voy a por un tronco."*
4. **Trabajar.** Ejecuta la cola de tareas. Puedes seguir con tu vida. Si se aleja demasiado de ti, te avisa.
5. **Volver.** Se planta a **dos bloques** de ti y **se queda ahí con la carga encima**: no la suelta al suelo ni te la
   mete en el inventario sin que se lo pidas. Chat: *"Ya estoy aquí. Cuando quieras te lo doy."* **La
   entrega es un latigazo**: todo lo que tenga de la orden, la carga y la referencia, pasa de golpe a tu inventario. Si
   tiene cofre de suministros asignado, la mete ahí sin que le pidas nada, que para eso está. Si no le cabe todo a tu
   inventario, lo que sobre cae a tus pies y te avisa en el chat, para que no se quede dentro de su inventario para
   siempre.

   **La vuelta no tiene castigo de velocidad**: ni el hambre ni la moral le frenan, va a su ritmo base aunque se esté
   muriendo (10.4). Y si no estás —desconectado, muerto, en el Nether o en el End— o estás a más de 128 bloques de casa,
   no se queda dando vueltas donde terminó ni cruza el mundo: **se va a casa y te espera allí**.
6. **Informar.** El latigazo que cobra es el mismo que le hace hablar. Si tuvo éxito: *"Toma. Espero que esté
   bien."* Si falló: niega con la cabeza tres veces, te devuelve la referencia con ese latigazo y suelta el motivo
   exacto, pidiendo perdón por no haber podido (sección 8). Zurrarle después de que ya te lo haya dado no cobra nada:
   solo le baja la moral (9.1).

### 5.1 Cómo se confirma una orden (máquina de estados)

Técnicamente es un sub-estado de `RECIBIENDO`, con dos temporizadores y nada más:

1. Al entregar la referencia se abre una **ventana de 5 segundos** (100 ticks) para el primer latigazo. Los humanos son
   lentos y hay que cambiar de item en la barra. Si vence sin latigazo, el esclavo te devuelve la referencia sin drama
   (*"Esperaba el latigazo, jefe. Te devuelvo esto."*) y vuelve a `OCIOSO`. No se gasta nada.
2. Cada latigazo válido suma 1 al contador (máximo 3; el cuarto cuenta como 3 y no gasta durabilidad) y **reinicia un
   temporizador de 1,5 s** (30 ticks). El enfriamiento del látigo es 1 s, así que hay margen para el siguiente y no para
   un despiste.
3. Cuando ese temporizador vence sin otro latigazo, la orden **arranca** con el contador que haya: 1, 2 o 3. Ahí se pide
   el plan (6.2) y el contador se congela. Es el momento en que se fija el techo.
4. Entregar otro item dentro de la ventana **sustituye** la referencia (devuelve la anterior) y reinicia el contador.
5. El techo se guarda en la orden como entero (`techo`, o `-1` para "sin techo"), no como "número de latigazos": el
   planificador nunca sabe cuántas veces zurraste, solo cuánto hay que traer.

Un latigazo durante `PLANIFICANDO` o `EN_COLA` se descarta sin gastar durabilidad. Un latigazo con la
orden ya `TRABAJANDO` es aceleración (9.3).

### 5.2 Tabla de gestos (qué hace cada clic derecho)

La tabla que faltaba: el diseño reparte gestos por todo el documento y esto los junta para que no haya dos que choquen.
Todo aplica solo al **dueño**. Cualquier otro jugador recibe el gesto de "ajeno" (11).

| Mano | Agachado | Estado del esclavo | Efecto |
|---|---|---|---|
| Látigo | no | sin dueño | Adopta: chasquido, queda quieto, abre `NombreScreen` (3) |
| Contrato | no | propio y quieto | Lo libera sin dueño y conservando nombre (12.1); gasta el contrato |
| Item cualquiera | no | propio, `OCIOSO` | Entrega la referencia y abre la ventana de 5.1 |
| Pan, comida | sí | propio, cualquier estado | Se la come ya (9.2) |
| Armadura / arma / herramienta | sí | propio, `OCIOSO` o `ESPERANDO` | La equipa en la ranura que toque, devolviendo la anterior |
| Cubo, antorchas | sí | propio | La guarda como material de supervivencia (10.2) |
| Cualquier otro item | sí | propio | Lo rechaza: *"No soy una mochila, jefe. Perdona."* (el invariante de la sección 2) |
| Mano vacía | no | propio | Estado en la action bar: orden, tiempo restante, hambre, moral (15.14) |
| Mano vacía | sí | propio | Abre la pantalla de equipo (15.14) |
| Látigo | no | propio, con referencia recién entregada | Cuenta para el techo (5.1) |
| Látigo | no | propio, `OCIOSO` y sin referencia | *"¿El qué, jefe?"* y no gasta durabilidad |
| Látigo | no | propio, `TRABAJANDO` / `VOLVIENDO` | Acelera (9.3) y baja moral (9.1) |
| Látigo | no | propio, plantado con carga (`INFORMANDO`) o `ESPERANDO` con carga | Cobra (5); gratis |
| Látigo | sí | propio, `OCIOSO` y con una orden anterior | **Repite la última orden** sin entregar item (5.3) |
| Látigo | sí | cofre | Asigna cofre de suministros / casa (10.4) |
| Látigo | no | al aire | Cancela la orden en curso (5) |
| Mano vacía | no | sobre su tumba | **Suplicar**: abre la pantalla de la lápida; con todo cumplido, su botón empieza el conjuro (10.6) |
| Cualquier mano | no | sobre La Muerte | Abre la pantalla del altar para entregar las ofrendas (10.6) |
| Látigo | no | sobre su tumba | Nada: *"No se azota a un muerto, jefe."* (10.6) |
| Pico | sí | sobre su tumba (dueño) | Entierro definitivo, con segundo clic de confirmación (10.6) |
| Cualquiera | cualquiera | de otro jugador | Baja la cabeza y no se mueve (11) |

Dos reglas para que no haya sorpresas: **comida sin agacharse es una orden de comida**, no un bocado (por eso el pan se
da agachado); y **un latigazo que apunta al esclavo gana siempre al latigazo al aire**, para que no cancele sin querer
quien está dando un latigazo mal apuntado.

### 5.3 Repetir la última orden

Es la mejora de calidad de vida que más se va a usar: tras un fallo, o para pedir otro stack igual, hay que volver a dar
el item, esperar, dar tres latigazos y cobrar. Con **agachado + látigo sobre el esclavo parado**, repite la última orden
(item y techo) sin pedirte el item. Es consistente con el invariante: el item de referencia es el encargo, no una
materia prima, y se guarda en el NBT del esclavo (`UltimaOrden`: id de item y techo). Si el reintento es tras un fallo,
usa la lección aprendida de la sección 8 (radio ampliado, otra fuente). Gasta 1 de durabilidad, como confirmar.

### Detalles del ciclo

- **La referencia vuelve siempre**: es el encargo, no una paga. Si lo consigue, vuelve con ella puesta más lo que haya
  sacado del mundo. Si falla o cancelas, te la devuelve igual de intacta, a tu inventario (se funde con tu montón, o cae a tus pies si no cabe).
- **Cancelar**: clic derecho con el látigo al aire (sin apuntar a nada) hace que interrumpa y vuelva. Tarda lo que
  tarde en volver, no es instantáneo.
- **Cobrar**: el latigazo sobre el esclavo es "dame lo que llevas". Es la única forma de sacarle la carga, porque no
  la suelta sola ni la deja en el suelo, que ahí se la lleva un creeper. Si no lleva nada encima: *"No traigo nada,
  jefe. Perdona."*
- **Cambiar de orden a mitad**: mientras planifica, espera turno, trabaja o vuelve, no acepta otra referencia ni la
  retira de tu mano. Informa: *"Sigo con el hierro, jefe. Cancélame y vuelve a pedírmelo."* Primero cancelas, vuelve y
  cobras; después das la orden nueva. Así no se confunden una aceleración, un encargo y un objeto prestado.
- **Darle cosas sin darle órdenes**: agachado + clic derecho. Así se le pone armadura, se le da una espada o se le mete
  comida en la boca. **El pan entra por aquí** (9.2): es la única forma de subirle el hambre y, con ella, la velocidad.
  El clic normal siempre significa "trabaja".
- **Latigazo en camino**: si ya está trabajando o volviendo, el látigo ya no es una orden, es un acelerador temporal.
  Acumula hasta ×3 y se gasta por bloques recorridos (9.3). Es útil para un esclavo que se arrastra por hambre, y es
  inútil con uno que ya va fino, porque el tope está donde está.
- **Si no estás cuando vuelve**: no te busca por el mundo ni se queda plantado en la mina. Se va a casa, guarda la
  carga en el cofre si se lo tienes asignado o se la queda encima, y espera ahí hasta que aparezcas (10.4). Cuando
  apareces, un latigazo y te la da. Y si estás, pero lejos de casa, hace lo mismo: no cruza el mundo para llevarte un
  stack de hierro.
- **Repetir**: si vuelve negando te ha devuelto el objeto, así que se lo vuelves a dar, un latigazo, y reintenta la
  misma orden con la lección aprendida. Recuerda por qué falló la anterior (item y cantidad), así que el segundo intento
  amplía el radio o prueba otra fuente en vez de repetir el mismo fracaso.
- **Si ya lo tienes tú**: le da igual. Lo consigue igual, sin discutir y sin decir nada. Para eso le pagas, para no
  tener que ir tú.
- **Si no llega al techo**: vuelve igual y te entrega lo que haya juntado, referencia incluida, con el latigazo de
  siempre. No se queda a vivir en la mina por un stack que no existe dentro del radio que alcanza. Con tres latigazos
  esto es la norma, no la excepción: no hay cifra que cumplir, hay tiempo que agotar.

## 6. El cerebro: cómo decide qué hacer

No hay un árbol de decisiones escrito a mano para cada item. Hay un **planificador** que trabaja hacia atrás desde el
objetivo usando las recetas del juego.

### 6.1 Fuentes de conocimiento

- **Recetario**: se leen las recetas cargadas en el servidor al arrancar (`RecipeManager`), de tipo `crafting`,
  `smelting`, `blasting` y `stonecutting`, incluidas las del resto de mods instalados. Se indexan por item de salida.
  Las recetas con tag como ingrediente ("cualquier tabla") se resuelven comprobando qué variante concreta existe en el
  mundo o en el inventario. **Las conoce todas**, no aprende ninguna: al arrancar se lo lee entero y no se le olvida.
  Conocer una receta no es poder ejecutarla (6.7).
- **Mapa de estaciones**: posiciones de mesas de crafteo, hornos, yunques, cortapiedras, mesas de herrería y cofres
  que el esclavo ha visto a menos de 32 bloques de su punto de origen. Se guarda en NBT.
- **Visión de menas**: inicia un **barrido incremental** de chunks disponibles, propios o compartidos (15.4), y anota
  menas y distancias. Cada sección nueva se procesa una vez; se revalida si cambia o su caché caduca. Se lee la **paleta de
  cada sección** de chunk, que es la lista corta de qué bloques distintos contiene, y solo las secciones cuya paleta trae
  una mena se recorren bloque a bloque. En un mundo normal son doscientas lecturas baratas y unas pocas secciones caras.
  Conserva posiciones compactas y cursores; no repite toda el área al avanzar. Límites y uso en 6.8, 24 y 26.
- **Tu cofre de suministros**: si le asignas uno (agachado + látigo sobre el cofre), consulta su contenido antes de
  salir a buscar nada.
- **Tablas del mundo**: dónde vive cada mena, qué animal da cada comida y cuánto aguanta cada pico. Son datos fijos de
  vanilla, no se calculan. Ver 6.5.

### 6.2 El algoritmo

```
resoluble(item, profundidad):
    si inventario autorizado, sin referencia ni reservas ajenas, cubre la cantidad: RESERVAR
    si profundidad > 6: NO
    candidatas = recetas que producen item + fuentes naturales del item
    para cada candidata:
        coste = suma de resoluble(ingrediente) + coste de estación + riesgo + distancia
        si es viable: se queda con la más barata
    devolver la mejor, o NO
```

Coste de estación: usar una mesa cercana cuesta 0, craftear una cuesta el precio de sus ingredientes, usar una lejana
cuesta la caminata. Riesgo: bajar a una cueva cuesta más que talar un árbol porque hay mobs. La cantidad pedida
determina lotes y consumos; la infraestructura compartida se calcula una sola vez (23.3). Las recetas que no sabe ejecutar se descartan
antes de puntuar, no después (6.7).

### 6.3 Ejemplo real: 3 lingotes de hierro

Lo que el planificador produce con un mundo normal, sin nada en el cofre:

```
1. No hay lingotes en el cofre.
2. El hierro no se craftea. Hay que minar mena y fundir.
3. Fundir exige horno y combustible, y minar exige pico.
4. Pico de piedra → 3 piedra + 2 palos + mesa.
   4.1 Talar al menos 3 troncos → 12 tablones, sin incluir aún el combustible.
   4.2 Reservar 4 tablones para la mesa, 3 para el pico de madera y 4 para producir 8 palos.
   4.3 Craftear y colocar mesa; craftear pico de madera con 3 tablones y 2 palos.
   4.4 Minar 3 bloques de piedra → 3 de adoquín; con 2 palos, craftear pico de piedra.
5. Buscar mena de hierro en radio 32 con olfato. Encontradas 2.
   Minar las 2 (necesita pico de piedra, ya lo tiene).
6. Horno: no hay cerca. Minar 8 piedras más y craftear horno.
7. Combustible: buscar carbón. Si no hay, talar otro tronco y fundirlo usando 1 tablón sobrante.
   Un tronco no se convierte solo en carbón vegetal: necesita ese combustible inicial.
8. Fundir el hierro en bruto obtenido de las 2 menas → 2 lingotes si cada una dio una unidad. Falta 1.
9. Consultar las otras posiciones del barrido o explorar dentro del radio autorizado.
   Encontrada 1 más → minar, fundir su hierro en bruto → 3 lingotes.
10. Volver.
```

Fíjate en lo importante: **el esclavo se construye su propia cadena de herramientas** y se la queda. La segunda vez que
le pidas hierro ya tiene pico, mesa y horno, y el trabajo dura la mitad. Esa progresión emergente es el corazón del mod.

### 6.4 Qué reutiliza de tu base

Antes de craftear nada comprueba, en este orden:

1. Cofre de suministros.
2. Su propio inventario (herramientas y sobrantes de encargos anteriores).
3. Estaciones soportadas a menos de 32 bloques (mesa, horno, alto horno y cortapiedras; no yunque).
4. Craftear lo que falte y colocarlo él mismo en un hueco libre cerca del punto de trabajo.

Si usa tu horno, te lo deja tal cual estaba. Si colocó el suyo, lo recoge al terminar y se lo lleva a casa.

### 6.5 Lo que sabe del mundo

Esta es la diferencia entre un mob que va a por hierro y un jugador. El jugador no busca hierro en cualquier parte:
sabe que el hierro vive por la capa 16, que el diamante vive pegado a la lava y que la vaca da filete. Nada de eso es
IA, son **tablas**. Se cargan al arrancar y se consultan gratis, sin leer un solo bloque.

**Y no son tablas cerradas.** Para reconocer una mena no se mira una lista de items escrita a mano: se mira si el bloque
está en una etiqueta de mena, las `*_ORES` de vanilla y las `c:ores` y `c:ores/<material>` de la convención de Fabric.
Un mod que añada titanio con su etiqueta entra en el sistema solo, sin recompilar nada. Las capas de la tabla son la
pista de dónde buscar cuando la etiqueta no dice nada: si la mena nueva no tiene preferencia conocida, se busca donde
la encuentre el barrido de 6.1.

**Menas: dónde baja a buscar.**

| Recurso | Capas | Dónde busca de verdad | Peligro |
|---|---|---|---|
| Carbón | 0 a 192 | laderas, cuevas someras, Y 40 a 100 | bajo |
| Cobre | −16 a 112 | Y 48, y más en cuevas de estalactitas | bajo |
| Hierro | −64 a 320 | Y 16 en cueva, Y 232 en montaña | medio |
| Oro | −64 a 32 | Y −16, y en badlands casi en superficie | alto, comparte capa con la lava |
| Lapislázuli | −64 a 64 | Y 0 | medio |
| Redstone | −64 a 16 | Y −59 | muy alto |
| Diamante | −64 a 16 | Y −59, nunca por encima de 16 | muy alto |
| Esmeralda | montañas, −16 a 320 | Y 236, vetas de un bloque | bajo |
| Obsidiana | donde el agua toca la lava | cuevas profundas y lagos | muy alto |

Tres cosas que salen de esa tabla y que son puro comportamiento de jugador:

- La tabla le dice **dónde bajar a buscar**, no dónde está la mena. El barrido de 6.1 sigue mirando el mundo de verdad:
  si hay una veta de hierro a 20 bloques en la capa 60, va a por ella aunque la tabla diga 16.
- **Diamante y redstone suben el umbral de preparación al máximo**, porque comparten capa con la lava. Sin cubo de agua
  no se baja a por ellos (10.2).
- Si para llegar necesita bajar por debajo de Y −50 y no lleva agua ni armadura, lo dice y no baja. No es cobardía, es
  que ya sabe cómo acaba eso.

**Comida: qué animal busca.**

| Le pides | Caza | Dónde mira | Si no hay |
|---|---|---|---|
| Filete | vaca | praderas, llanuras, bosques | informa; el cerdo no da filete |
| Chuleta | cerdo | taiga, bosque, llanura | informa; no cambia el pedido |
| Pollo | pollo | cualquier bioma de hierba | informa |
| Cordero | oveja | llanuras, montañas | informa |
| Conejo | conejo | desierto, taiga, nieve | informa |
| Pescado | especie exacta del item pedido | caza acuática accesible, sin caña en v1 | lo dice y no insiste |
| Pan | trigo | cultivos maduros autorizados fuera de aldeas | no hay pan sin trigo |
| Manzana | hojas de roble y roble oscuro | rompe hojas, nunca las de un árbol plantado por ti | lo dice |
| Zanahoria o patata | cultivos maduros autorizados | cosecha fuera de aldeas y zonas protegidas | lo dice |

Y cuenta lo que cae por animal antes de desenvainar: vaca 1 a 3 filetes, cerdo 1 a 3 chuletas, oveja 1 o 2, pollo 1,
conejo 1. **Mata lo justo**, redondeando hacia arriba, y no arrasa un rebaño por dos filetes. Si el pedido es un item
concreto va a por ese animal aunque haya otro más cerca. Si lo que tiene es hambre propia, vale cualquier cosa
comestible y elige la más cercana. La carne la cocina siempre que pueda: cruda vale la mitad.

**Herramientas: cuál y cuántas.**

| Lo que va a picar | Pico mínimo | Repuesto que se lleva |
|---|---|---|
| piedra, carbón, cobre | madera | 1 de piedra |
| hierro, lapislázuli | piedra | 1 de piedra más |
| oro, redstone, diamante | hierro | 1 de hierro |
| obsidiana | diamante | 1 de diamante |

La vida útil de un pico es un número fijo y él la lleva contada: madera 59 bloques, piedra 131, hierro 250, diamante
1561. Antes de meterse en una mina hace una cuenta de tres líneas:

```
bloques = tamaño estimado del yacimiento + galería de acceso
picos   = ceil(bloques / vida_útil)
```

Si el plan necesita más de un pico, **craftea el repuesto antes de bajar** y lo lleva en el primer hueco libre. No sube
a por el repuesto a mitad de la mina. Y si al pico que lleva le quedan menos de 10 usos, no entra: craftea otro primero,
porque el pico que se rompe a 30 bloques de profundidad es el que te obliga a volver con las manos vacías.

### 6.6 Reglas de jugador

Las siete cosas que hace cualquiera que haya jugado Minecraft y que ningún mob de vanilla hace. No son lógica nueva:
son comprobaciones de bloque metidas **antes** de romper, no después de caerse.

1. **Nunca pica hacia abajo.** Ni un bloque, ni "solo uno para ver". Baja en escalera de 1×1, un bloque de profundidad
   por cada bloque de avance, o en galería de dos de alto si ya está en el nivel. Picar en vertical es exactamente cómo
   se descubre un lago de lava con la cara, y es la muerte más tonta del juego.
2. **Nunca rompe el bloque sobre el que está de pie.** Antes de romper mira el de debajo. Si no es sólido, no lo rompe.
   Si es lava, se aparta primero.
3. **No rompe lo que sostiene arena, grava o agua.** Mira el bloque de arriba y los cuatro vecinos. Arena o grava encima
   se le cae en la cabeza. Agua detrás inunda la galería. En los dos casos lo deja, o lo rompe en otro orden: primero el
   de arriba, luego el de abajo.
4. **Marca la vuelta.** Antorcha a la derecha al entrar, que al volver queda a la izquierda. La regla de toda la vida,
   y además apunta las coordenadas de la boca de la cueva en NBT (10.3).
5. **No malgasta herramienta.** El pico solo toca lo que el pico mina. Tierra, arena, grava y madera se sacan con la
   mano, la pala o el hacha. Un pico de hierro gastado en tierra es un pico que no está cuando aparece el diamante.
6. **No se queda sin luz ni sin salida.** Antorcha cada 8 bloques y, si la tarea baja más de 20 bloques, no entra con
   menos de 4 antorchas. Sin luz le aparecen cosas por la espalda.
7. **Comprueba la espalda.** Cada 8 bloques excavados se da la vuelta y mira. Un `getEntities` de radio 8 cada 160
   ticks, que no cuesta nada y evita el susto.
8. **Elige acceso antes de extraer (26).** Prioriza cueva o mina explorada con antorchas si el desvío compensa. Si no,
   usa cueva natural o abre bocamina en ladera discreta, nunca bajo sus propios pies ni en campo o casa. Desde el
   frente seguro sí orienta la galería hacia la veta conocida, comprobando cada bloque. No entra en aldeas ni rompe
   una construcción para alcanzar una entrada. Saber dónde está la mena no elimina la fase de acceso.

Y la que resume todas: **si algo no lo haría él con su propio personaje, el esclavo tampoco lo hace.**

### 6.7 Hasta dónde llega

**Conoce todas las recetas del servidor**, incluidas las de otros mods, y ninguna se le olvida. Pero conocer una receta
no es poder ejecutarla, igual que tú sabes cómo se hace un faro y no lo haces por gusto.

La criba son tres preguntas, y las tres tienen que ser sí antes de que la receta entre en el plan:

1. ¿Todos los ingredientes salen de un bloque roto, de un mob muerto o de otra receta que también pasa la criba?
2. ¿La cadena del producto se resuelve en 3 transformaciones o menos, con infraestructura limitada aparte?
3. ¿Se hace en mesa de crafteo, horno, alto horno o cortapiedras? Nada de yunques ni mesas de herrería.

La infraestructura (mesa, herramientas, horno y combustible) puede ampliar el grafo hasta **6 niveles totales**;
no añade capacidad para pociones, encantamientos o estaciones no soportadas. Se detectan ciclos y se limita el número
de candidatos, no solo la profundidad (23.3). Lo que no pasa la criba se explica antes de salir.

| Nivel | Ejemplos | ¿Puede? |
|---|---|---|
| Directo del mundo | menas, troncos, tierra, arena, carne, lana, cuero | sí |
| Una receta | tablones, palos, mesa, horno, antorcha, cofre, cama, herramientas y armadura hasta hierro | sí |
| Dos recetas | pico de piedra, pan, cubo, escudo, carbón vegetal, sopa de setas | sí |
| Tres recetas | yunque, pistón, libro y estantería, tinte y lana de color | sí, con paciencia |
| Mecanismos | dispensador, observer, comparador, raíl de impulso | no |
| Encantamientos y pociones | mesa de encantar, libro encantado, cualquier poción | no |
| Nether y End | blaze, cuarzo, perla, ojo de ender | no |
| Bloques de almacenamiento | bloque de hierro, de diamante | sí, pero no le salen a cuenta y te lo advierte |

Esa última fila importa: si le pides un bloque de hierro sabe hacerlo, pero son nueve lingotes y va a tardar, así que te
lo dice antes de salir en vez de volver tres días después. Y si el servidor tiene un mod que añade una receta rara con
un ingrediente de otro mod, el esclavo la conoce igual: el recetario no distingue de quién es cada cosa.

### 6.8 Presupuesto, fatiga y topes

El problema no es que encuentre diamantes, es que encuentre 150. Tres frenos, todos baratos y todos explicables en el
chat.

**1. El reloj.** Cada orden lleva presupuesto de tiempo: **30 minutos reales** por defecto, día y medio de juego. Cuando
se acaba, vuelve con lo que tenga aunque esté a dos bloques de una veta. Es lo que hace que un stack de diamantes sea
un trabajo y no un paseo, y lo que impide las expediciones infinitas.

**2. La fatiga.** Terminar una **veta completa** (todas las menas del mismo tipo conectadas entre sí a menos de 8
bloques del primer bloque, con máximo 64 bloques por grupo). La conexión se hace por caras sobre candidatos del
barrido, sin cargar chunks y en lotes. El cansancio se paga con una pausa antes de seguir picando:

| Recurso | Pausa por veta | Se acumula |
|---|---|---|
| carbón, cobre, hierro | 10 s | +50 % por cada veta siguiente |
| oro, lapislázuli, esmeralda | 40 s | +50 % |
| redstone, diamante | 2 min | +50 % |

La pausa **se ve y se come el reloj**: se sienta, come si tiene algo, se limpia el sudor, suelta una línea. No es un
temporizador invisible, es tiempo que no está picando. El incremento es **lineal**: pausa de la veta n = base ×
`(1 + 0,5 × (n − 1))`. Cinco vetas de diamante cuestan 2 + 3 + 4 + 5 + 6 = **20 minutos** de los treinta, además
del trabajo y el viaje. Con hambre por debajo de 8 las pausas **se duplican** (9.2).

**3. El tope.** El techo de los latigazos ya recorta (sección 5), pero con **tres latigazos no hay techo**, y ahí hace
falta uno duro o el mundo se vacía en una tarde afortunada:

| Recurso | Tope duro por orden, con cualquier número de latigazos |
|---|---|
| carbón, cobre, hierro | 64 |
| oro, lapislázuli, esmeralda, redstone, diamante | 32 |

**El tope de recurso se aplica a todas las órdenes**, también con uno o dos latigazos: la cantidad efectiva es el mínimo
entre techo solicitado, tope de recurso y capacidad reservada. Así dos latigazos no permiten traer 64 diamantes cuando
tres solo permiten 32. La confirmación muestra el límite efectivo. Las vetas completas siguen costando su pausa:
el tope no perdona la fatiga. Para todo lo que
no sea mena (madera, comida, lo crafteado), el tope duro de los tres latigazos es **lo que quepa en 8 huecos**: los otros
4 se reservan para herramientas, antorchas y comida, que no se pueden desplazar por un encargo.

Con los tres frenos, media hora de trabajo da lo que da: veinte o treinta de hierro y un puñado de diamantes. Si el
barrido ve 150 diamantes, no los trae. Trae 32, paga la fatiga y vuelve, y lo dice: *"He visto cuarenta. Te traigo
treinta y dos, jefe. He llegado al límite. Perdona."*

## 7. Las tareas concretas

- **Minar**: solo rompe bloques que haya identificado como recurso natural (menas, piedra, troncos, cultivos, arena,
  grava). Nunca rompe bloques construidos por jugadores, ni cofres, ni camas, ni puertas. Solo excava más de 3 bloques
  si tiene una mena detectada al otro lado, así que no agujerea tu casa. Baja en escalera, nunca en vertical, no rompe
  el bloque que pisa (6.6) y entra por minas o laderas, no en línea recta a la veta (6.6 regla 8).
- **Talar**: troncos de cualquier especie, respetando que el hacha y el pico se gastan. Deja la copa colgando o la
  recoge, da igual, es coña.
- **Cazar**: animales pasivos para comida. Ataca con lo mejor que tenga en la mano. Si le ataca un hostil, huye hacia
ti antes que pelear, salvo que lleve armadura puesta.
- **Cocinar**: mata vaca, recoge carne cruda, la mete en el horno con el combustible que tenga. Se queda lo justo
  para su propio hambre, el resto es para ti, y te lo dice en el informe en vez de callárselo.
- **Craftear**: ejecuta la receta de verdad, con el crafteo correspondiente al tamaño de la rejilla (2×2 en el
  inventario, 3×3 en mesa).
- **Comer**: tiene su propia barra de hambre (9.2). Se come lo que lleve encima y, si no lleva nada, sigue trabajando
  cada vez más despacio hasta que le des pan. No se niega a una orden por hambre, nunca.

## 8. Fallo, disculpa y reintento

Cuando vuelve sin haber conseguido el objetivo (nada encontrado, o menos cantidad de la pedida, o se le rompió la
herramienta y no pudo rehacerla):

1. Llega, se para delante de ti y **conserva la referencia intacta hasta el latigazo de cobro**. El fallo no abre una
   vía de entrega distinta: referencia y carga parcial se entregan juntas, antes de la explicación.
2. Niega con la cabeza tres veces (animación de giro de cuello con sonido de negación).
3. Suelta el motivo en el chat, en primera persona, con la voz baja y pidiendo perdón. Nunca pone excusas, nunca echa la
   culpa a nadie y nunca discute. Motivos posibles:

| Motivo | Línea de chat |
|---|---|
| No hay mena en el radio | "He mirado todo lo que alcanzo. No hay. Lo siento." |
| Necesita herramienta que no puede hacer | "No tengo pico de piedra, así que no llego al hierro. Perdona." |
| No hay combustible ni madera | "No hay con qué fundir, ni un palo. No he podido." |
| Se le rompió la herramienta | "Se me ha roto el pico. Perdóname, lo he intentado." |
| Huyó de mobs | "Había un esqueleto y he huido. Soy un cobarde, lo sé." |
| Le pediste algo imposible | "Eso no sé hacerlo. No llego. Perdón." |
| El hambre lo frenó hasta agotar el tiempo | "He ido muy lento, tenía hambre. Traigo cuatro y me caigo." |
| El objetivo queda fuera de su radio de trabajo | "No he podido llegar más lejos. Hasta aquí alcanzo." |
| Solo hay ruta cruzando lava o un barranco | "Había lava en medio. No he cruzado. Tenías razón tú." |
| Va mal equipado a un sitio peligroso | "No he bajado. Sin antorchas ni armadura me moría." |

4. Si le devuelves el objeto y le das otro latigazo, reintenta. El segundo intento cambia de estrategia: amplía el
   radio o prueba otra fuente. Nunca relaja permisos ni umbrales de peligro; solo baja si ahora cumple la preparación.
5. Si no estás cuando vuelve sin nada, esos tres pasos los hace cuando aparezcas. La referencia no se la queda: la
   lleva encima, espera en casa con ella y te la devuelve en cuanto te ve y le das el latigazo (10.4).

## 9. Moral y hambre: dos formas de ir más despacio

Dos barras, dos formas de que el esclavo vaya lento, y un acelerador de usar y tirar. Ni las barras ni el látigo le
hacen desobedecer: **el esclavo siempre hace caso**. Lo que cambia es a qué velocidad y cuánto tarda en hacerlo. Y las
dos barras solo cuentan mientras busca o trabaja: **la vuelta a casa o hacia ti va siempre al 100 %** (10.4).

### 9.1 Moral: el látigo tiene dos filos

Matar a latigazos es el gag, pero abusar debe notarse en el juego. **La moral nunca lo vuelve rebelde**: solo lo
entristece y lo frena. Sigue obedeciendo igual, tarda más y se disculpa más.

- Cada latigazo que le das **mientras trabaja** (no para confirmar) le baja la moral un 10 %, acumulable hasta −50 %.
  El latigazo de cobrar no cuenta: pedirle lo que ha traído es el trato, no maltrato (sección 5).
- Con moral baja camina más despacio, tarda más en cada tarea, se queda mirando al suelo y se le nota el esfuerzo.
  Chat: *"Perdón. Voy lo más rápido que puedo."* Se multiplica con el hambre (9.2), así que un esclavo hambriento y
  apaleado es un adorno, pero un adorno obediente.
- La moral se recupera sola, un 25 % por día de juego, o de golpe si le das de comer.
- **No miente, no finge y no sabotea nada**, ni con la moral por los suelos. Si vuelve diciendo que no encontró, es
  que no encontró. Lo único que cambia con la moral baja es lo lento que va y lo mucho que se disculpa.

### 9.2 Hambre: pan o se arrastra

Tiene barra de hambre propia, de 20 puntos, igual que un jugador. **No se la llena el aire: se la llena el pan que le
des tú** (agachado + clic derecho con la comida en la mano, sección 5). Cualquier comida vale, pero el pan es la
unidad de medida del mod: 5 puntos por barra, la comida más barata de hacer y la que él espera.

**Cuánto gasta**

| Situación | Gasto |
|---|---|
| Parado, ocioso, esperando órdenes | 1 punto cada 5 minutos |
| Trabajando (caminar, minar, talar, craftear) | 1 punto cada 90 segundos |
| Corriendo o huyendo de un mob | 1 punto cada 40 segundos |

Una barra llena son **30 minutos de trabajo seguido**, o sea una orden entera con el presupuesto de 6.8. Si le pides
el máximo, se queda seco y tienes que alimentarlo antes de la siguiente. El duende gasta la mitad (sección 3.1), así
que con él cunde el doble el pan. Esa es su verdadera ventaja económica.

**Cuánto le frena**

| Hambre | Velocidad | Qué se nota |
|---|---|---|
| 20-17 | 100 % | nada, va fino |
| 16-13 | 90 % | un paso más corto |
| 12-9 | 75 % | se le oye la barriga |
| 8-5 | 55 % | arrastra los pies y respira fuerte |
| 4-1 | 35 % | cada bloque es una negociación |
| 0 | 25 % | y empieza a perder 1 de vida cada 4 segundos |

El multiplicador afecta a lo que anda **y a lo que pica**: con hambre 4 tarda el triple por bloque. No afecta a si
obedece. Obedece siempre, con la cara que pone.

**Y solo mientras busca o trabaja.** En la vuelta a casa o hacia ti no se aplica: vuelve siempre al 100 %, por mucha
hambre que lleve (10.4). El hambre sigue bajando por el camino, porque anda, así que un esclavo a 0 puede caerse muerto
a dos bloques de tu puerta. Alimentarlo antes de mandarlo es la única solución.

**Se puede morir de hambre.** A 0 puntos pierde 1 de vida cada 4 segundos hasta caer. Muere como cualquier otro
muerto de la sección 10: suelta todo, la orden se cancela y queda una tumba (10.6). Por eso el pan no es decoración.

**Qué hace al respecto sin que se lo pidas**

- Si lleva comida en su inventario y baja de 14, se la come él. Si le diste pan de sobra, se lo administra solo.
- Si no lleva nada y baja de 6, lo dice en el chat y sigue trabajando. No vuelve, no se sienta, no negocia.
- Con hambre por debajo de 8, las pausas por veta de 6.8 **se duplican**: un hambriento tarda el doble en recuperarse
  entre veta y veta.

**Lo que dice**

| Hambre | Línea |
|---|---|
| 12-9 | "Jefe... si tuvieras un pan. Perdona." |
| 8-5 | "Voy a seguir. Voy a seguir, ¿eh?" |
| 4-1 | "Pan. Por favor. Pan. Ya está, sigo." |
| 0 | "Me caigo. Lo siento. He terminado, ¿eh?" |

### 9.3 El látigo acelera (y se le pasa)

Un latigazo en camino no es una orden, es miedo, y el miedo corre. Es la única forma de ir más rápido que existe en
el mod: no hay pociones, no hay hechizos, hay cuero en la espalda.

- **Solo en camino.** Cuenta cuando está andando hacia el objetivo, trabajando o volviendo. Parado en tu base, comiendo
  o esperando órdenes no acelera nada: ahí el latigazo solo le baja la moral (9.1) y gasta durabilidad. La excepción es
  cuando está plantado delante de ti con la carga: ese latigazo no acelera, **cobra** (sección 5), y no le baja la
  moral. En la vuelta suma sobre el 100 %, que ya es su velocidad sin castigo (10.4), así que sirve para llegar antes,
  no para deshacer un frenazo que no existe.
- **Cuánto.** Cada latigazo en camino multiplica su velocidad por 1.15, y se acumula:

  | Latigazos seguidos | Multiplicador |
  |---|---|
  | 1 | ×1.15 |
  | 2 | ×1.32 |
  | 3 | ×1.52 |
  | 4 | ×1.75 |
  | 5 | ×2.01 |
  | 6 | ×2.31 |
  | 7 | ×2.66 |
  | 8 o más | ×3.00 (tope) |

- **Tope ×3, y es un tope de verdad.** El octavo lo alcanza (1.15⁸ = 3.06, recortado a ×3.00); del noveno en adelante ya no sube y se queda en ×3.00. La
  bolsa de impulso, en cambio, es de 24 bloques por latigazo **hasta el octavo**: el noveno ya no suma recorrido (9.4).
  Sigue sonando el chasquido, sigue gastando durabilidad, sigue bajando la moral y sigue sin acelerar nada. Es la
  única forma de que el látigo no sea un botón de velocidad infinita: a partir del noveno pagas cuero para nada.
- **El tope no arregla el hambre.** Se aplica sobre lo que ya lleva encima, así que un esclavo a hambre 4 (35 %) con
  el tope puesto va al 105 %: igual que uno sano sin latigazos, y con la cara torcida. Un hambriento con látigo va
  como un sano. Un sano con látigo va el triple. **El pan sigue siendo más barato que el látigo.**
- **Mientras dura el impulso, la moral no frena.** El acelere tapa el cansancio: el multiplicador se calcula sobre el
  hambre y el impulso, y la moral se aplica cuando el impulso se apaga. Así el ×3 es real. Lo que te queda después es
  un esclavo agotado andando a la mitad, disculpándose, y eso sí se nota y no se puede zurrar para quitarlo.
- **Se gasta andando, no con el reloj.** Cada latigazo añade **24 bloques** de recorrido acelerado. Cuando se acaban,
  vuelve solo a su velocidad de hambre, sin avisar y sin transición. Minar también consume impulso: un bloque de túnel cuenta
  como dos de camino.
- **No acelera el pico.** El látigo asusta, no enseña a picar. El tiempo por bloque y la fatiga por veta de 6.8 no
  cambian. Solo corre más entre sitio y sitio, que es donde se le va la vida.

**Lo que dice mientras le zurras**

| Multiplicador | Línea |
|---|---|
| ×1.15 / ×1.32 | "¡Voy! ¡Voy, jefe!" |
| ×1.52 / ×1.75 | "¡Que voy, que voy!" |
| ×2.01 - ×2.66 | "¡Corro! ¡Corro!" |
| ×3.00 | "Ya no puedo más. Lo siento. Ya no." |

### 9.4 Cómo se calcula la velocidad (técnico)

Una sola función, `SpeedModifier.multiplicador(estado)`, que se recalcula **cuando cambia algo** (tramo de hambre, moral,
latigazo, impulso agotado, cambio de fase) y no por tick. El resultado se aplica como un único `AttributeModifier`
transitorio sobre `MOVEMENT_SPEED` (operación `ADD_MULTIPLIED_TOTAL`, id propio del mod), de modo que el pathfinder y
la animación de caminar se enteran solos.

```
fase TRABAJANDO / BUSCANDO:
    v = base × tramoHambre × (impulso > 0 ? boost : tramoMoral)
fase VOLVIENDO / yendo a casa:
    v = base × (impulso > 0 ? boost : 1.0)          // sin hambre ni moral (10.4)
tiempo por bloque picado = tiempoVanilla / (tramoHambre × tramoMoral)      // el látigo no acelera el pico (9.3)
```

- `boost = min(1.15^n, 3.0)`, con `n` = latigazos acumulados (0 a 8). `boost` sin impulso vale 1.
- **Bolsa de impulso** = 24 bloques × `n`, máximo 192. Se descuenta por distancia recorrida desde la última lectura
  (1 bloque de camino = 1; 1 bloque excavado = 2). Al vaciarse, `n` vuelve a 0 y se recalcula una vez.
- La moral baja con cada latigazo aunque haya impulso, pero **su efecto se aplaza** hasta que el impulso se apaga
  (9.3), por eso se calcula aparte: `tramoMoral` se lee, no se aplica, mientras la bolsa tenga bloques.
- `tramoHambre` sale de la tabla de 9.2 y solo cambia al cruzar un umbral (20/16/12/8/4/0), así que el modifier se
  reescribe a lo sumo seis veces por barra.
- Test unitario sin Minecraft (`SpeedModifierTest`): hambre 4 + n=8 → 1.05; hambre 20 + n=8 → 3.00; fase VOLVIENDO
  con hambre 0 → 1.00; n=9 → 3.00.

## 10. Riesgos, muerte y vuelta a casa

- Ataca de verdad el mob hostil que se le cruce, pero no es un guerrero.
- **Si muere**: suelta todo lo que llevaba (inventario, herramientas, referencia) donde cayó. La orden en curso se cancela; el vínculo contigo no (10.5).
  La referencia cae con el resto, así que puedes recogerla del suelo y dársela al que vuelva.
  **No reaparece solo.** En el sitio donde murió se levanta una **tumba** con su nombre, y solo un **ritual** la
  vuelve a abrir (10.6). Vuelve **con la misma cara y el mismo cuerpo**, sin nada encima pero con la memoria de
  estaciones y herramientas aprendidas. Si era duende, sigue siendo duende. Chat al resucitar: *"He vuelto. Perdóname
  por haber muerto."*
- Si muere en el vacío o en lava, lo que llevaba se pierde como a cualquier jugador. Es Minecraft, pasa.
- **Daño por caída, lava, ahogamiento**: no los aguanta, los evita. Ver 10.1 y 10.2.

### 10.1 Instinto: no se mata solo

No hay simulación de física ni IA pesada. Son **tres capas baratas**, y la que más trabajo hace no se ejecuta por tick:
se paga una sola vez, dentro del pathfinding que vanilla ya calcula igualmente.

**Capa 1 — la lava no existe como camino (coste cero en runtime).**

El pathfinder vanilla *sí* contempla cruzar lava y fuego: les pone un coste extra (`PathType.DAMAGE_FIRE`, malus 8) y los
usa igual si el desvío cuesta más de 8 bloques. Eso es justo lo que hace que un mob se meta en la lava. Se corta de raíz:

- Fuego, lava, magma y nieve en polvo pasan a ser nodos **intransitables** para él. No es que los esquive, es que **no
  los ve**.
- Dos maneras de hacerlo, hay que mirar cuál sigue viva en 26.3: `setPathfindingMalus(PathType.DAMAGE_FIRE, -1)` en el
  constructor de la entidad, si el malus negativo sigue significando bloqueado, o una subclase de `WalkNodeEvaluator` que
  devuelva `PathType.BLOCKED` en lugar de `DAMAGE_FIRE`.
- Es por entidad y sin estado: no cuesta nada más que el pathfinding que ya se paga.
- Efecto secundario correcto: si un tramo solo se puede hacer cruzando lava, **no hay camino**, así que no va. Que es
  exactamente lo que quieres.

**Capa 2 — mirar antes de dar el paso (una comprobación por nodo, no por tick).**

Cuando la navegación avanza al siguiente nodo (una vez por segundo, poco más o menos), se hacen como mucho cuatro
lecturas de bloque:

1. ¿El bloque de destino es lava o fuego? Nodo inválido.
2. ¿Qué hay debajo del destino? Se cuenta hacia abajo hasta 4 bloques o hasta dar con suelo sólido.
   - Caída de 1 o 2 bloques: adelante, es un escalón.
   - Caída de 3: adelante solo si abajo hay agua.
   - Caída de más de 3 sin agua: **nodo inválido**. Aquí es donde no se tira por el barranco.
3. ¿Debajo hay lava? Nodo inválido, sin discusión.

Nodo inválido no es quedarse quieto: se recalcula la ruta con ese nodo marcado como bloqueado, y si no queda ruta
alternativa, avisa y vuelve (sección 8). Ni `raycast` por tick ni búsqueda en área.

**Capa 3 — reacción, y solo cuando ya hay fuego.**

Cada 10 ticks (dos veces por segundo, no más) se leen tres bloques: pies, cabeza y el de debajo. Si ninguno es peligroso,
la función sale en la primera línea. Ese es todo el gasto en reposo.

Si está ardiendo o dentro de lava:

| Situación | Qué hace | Coste |
|---|---|---|
| Ardiendo en tierra | Busca agua en radio 6 y se mete. Si no hay, corre hacia ti | ≤ 220 lecturas, una vez, y para al encontrar agua |
| Dentro de lava | Sale en línea recta al primer bloque no-lava con suelo firme en radio 4, saltando si hace falta | ≤ 80 lecturas |
| Ahogándose | Sube nadando (`setCanFloat(true)`, gratis) y busca aire en radio 4 | casi siempre gratis |
| Cayendo | Si lleva más de 3 bloques de caída y ve agua debajo, se orienta hacia ella | 1 lectura por tick mientras cae |

La lava es el único caso que justifica gastar algo más, porque es el único que te mata en dos segundos. Y aun así son
lecturas de bloque normales, ni entidades, ni explosiones, ni escaneos del mundo.

**Lo que NO se hace**, porque es lo que hunde el TPS:

- Nada de BFS ni flood fill para "mapear peligros". El mapa de peligros es el pathfinding, que ya se calcula.
- Nada de barrer todo cada tick. El olfato (6.1) procesa secciones nuevas/invalidadas en lotes y conserva cursores.
- Lecturas y modificaciones del mundo en el hilo del servidor, en lotes con presupuesto. Solo archivos y
  decodificación de skins pueden procesarse fuera, sin acceder al mundo (23.7).
- Nada de `fireImmune`. Se puede morir quemado, ese es el chiste. Solo intenta que no pase.

Coste total en reposo: **6 lecturas de bloque por segundo**. Caminando, cuatro más por nodo. Ardiendo, una búsqueda de
220 bloques y se acabó. Eso es todo el sistema.

### 10.2 Prepararse antes de bajar

La inteligencia de verdad no es esquivar la lava, es no llegar a ella mal preparado. Antes de arrancar una tarea marcada
como peligrosa (bajar a una cueva, minar cerca del nivel de lava, cruzar una zona sin luz) hace un repaso de una sola
pasada, en un tick:

1. **Armadura**: se pone lo mejor que tenga, del inventario o del cofre de suministros. Si no tiene nada, lo dice:
   *"Bajo sin nada. Voy a intentarlo igual."*
2. **Antorchas**: craftea las que pueda (carbón + palo) y las pone cada 8 bloques mientras excava. No es decoración: es
   lo que evita que le aparezca un esqueleto a la espalda. Items reales, gastados de verdad.
3. **Comida**: si tiene el hambre por debajo de 14, come antes de bajar, de lo que lleve encima. Un esclavo con hambre
   avanza al 55 %, tarda el triple en picar y a 0 se muere por el camino (9.2). Si no lleva nada, baja igual y lo dice.
4. **Cubo de agua**: si tiene un cubo, lo llena antes de bajar. Es su carta de salvación: si se prende, se lo echa
   encima. Es literalmente lo que haría un jugador, y son tres hierros que él mismo ha fundido.
5. **Pico de repuesto**: cuenta los bloques que va a picar contra la vida útil del pico (6.5) y craftea el que falte
   antes de entrar. Con menos de 10 usos en el que lleva, no baja.
6. **Bajada**: si el plan exige bajar más de 4 bloques, baja **picando escalones**, nunca saltando, y deja el hueco de
   vuelta para subir por donde bajó.

Y sabe decir que no. Si el plan exige bajar a una cueva profunda y no tiene cubo, ni antorchas, ni armadura, no baja:
vuelve y lo explica. Es más gracioso un esclavo que se planta que uno que se derrite.

**Cuando una caída es la única salida**, no salta ni coloca bloques en el aire bajo sus pies. Busca una escalera
lateral con apoyo, materiales reales y permisos. Si no puede, activa la recuperación de 10.3. El pilar solo sirve
para **subir**, colocando sobre una cara válida; no resuelve un descenso sin apoyo.

### 10.3 Cómo vuelve y por qué no se queda pillado

Volver no se resuelve con pathfinding. Se resuelve apuntando por dónde ha ido.

**El rastro.** Cada vez que cambia de bloque, apunta la posición en una lista circular de 256 entradas. Son 2 KB por
esclavo y una comparación de posición por tick, nada más. Para volver, recorre la lista al revés. Es el camino exacto
que ya hizo. Cada tramo se revalida: el rastro es una pista, no garantiza que nadie haya roto el puente o puesto
lava. Si se agotan las 256 entradas, las anclas conservan la referencia del regreso.

**Las anclas.** Además del rastro guarda cuatro posiciones en NBT: la boca de la cueva en la que entró, el horno que
colocó, la mesa que colocó y el centro de tu base. Si el rastro no sirve (se cayó por un agujero, se le derrumbó el
túnel), recalcula ruta a la ancla más cercana. Y los bloques que él mismo colocó los reconoce como suyos, así que puede
desatascar su propio túnel sin romper nada tuyo.

**Detección de atasco.** Solo durante tareas de desplazamiento: si en 100 ticks no se ha movido medio bloque ni ha
avanzado de nodo, está pillado. Minar, cocinar y descansar tienen su propio progreso y no disparan rescates. Entonces
sube la escalera de desatasco, un intento por escalón y sin repetir:

| Intento | Qué hace | Cuándo |
|---|---|---|
| 1 | Salta y gira 90 grados | a los 100 ticks |
| 2 | Aparta un obstáculo frontal natural autorizado; nunca rompe el bloque que pisa | a los 140 |
| 3 | Recalcula ruta al ancla más cercana | a los 200 |
| 4 | Vuelve contigo por teletransporte, y lo cuenta en el chat | a los 400 |

Los atascos tontos tienen atajo propio: en un agujero de un bloque salta. En uno de dos se hace un pilar de dos. En agua
flota y nada (`setCanFloat`). En telaraña la rompe con la espada. En una esquina de un bloque el duende pasa y el hombre
no, y por eso el hombre pica.

Y una regla de diseño que evita el 90 % de los atascos antes de que pasen: **nunca deja un escalón de más de un bloque**
en lo que excava. Si no puede subir por donde bajó, no baja.

### 10.4 La vuelta a casa: sin castigo y sin ti

**Dónde está su casa.** En el punto exacto donde te lo quedaste, con el latigazo y el nombre. Si le asignas un cofre de
suministros (agachado + látigo sobre el cofre), la casa pasa a ser ese cofre, porque ya hay un sitio de la base al que
va y viene solo. Se escribe en NBT una vez y no se mueve salvo que le cambies el cofre. Es la misma posición que el
ancla "centro de tu base" de 10.3, con nombre propio.

**La vuelta no se paga.** La velocidad en la vuelta es **siempre la base**: 0.26, o 0.31 si es duende. El hambre y la
moral no la tocan. Un esclavo a 4 de hambre que vuelve con el hierro no se arrastra: llega a tu puerta al mismo ritmo
que uno comido. El castigo de las dos barras de la sección 9 existe solo mientras busca y trabaja, que es cuando el
retraso te cuesta algo. Por el camino le sigue bajando la barra de hambre, porque anda, y si llega a 0 puede caerse
muerto en la puerta. El látigo sigue sumando encima (9.3) si quieres que llegue antes.

**Qué hace si no estás.** Antes de encaminarse mira dónde estás:

| Dónde estás | Qué hace |
|---|---|
| En el mundo, en tu misma dimensión y cerca de casa | Va a por ti y se planta a dos bloques con la carga |
| En el mundo, en tu misma dimensión, pero lejos de casa | Va a casa y espera. No cruza el mundo para buscarte |
| Desconectado, o muerto esperando respawn | Va a casa y espera |
| En el Nether o en el End | Va a casa y espera. Al portal no se asoma: él no sale del Overworld (sección 11) |

**Lejos quiere decir más de 128 bloques de casa.** Ahí ya no es una entrega, es una travesía: ocho chunks de mundo que
él no ha cargado, con ríos, barrancos y mobs de por medio, y con el hambre bajando todo el rato. Así que no va. Vuelve a
casa, guarda la carga y espera. Si te acercas a casa, te ve llegar, se te pone delante en la puerta y te la da con el
latigazo. Si quieres el pedido en mitad de una expedición, vuelve tú, que para eso tienes piernas y un caballo.

**Y si el que está lejos es él**, la casa sigue siendo el destino: primero el rastro de 10.3, luego el ancla más
cercana, y si no hay ruta o se queda atascado a más de 256 bloques de casa, se teletransporta a casa. Es el mismo
teléfono de la escalera de 10.3, pero apuntando a casa en vez de a ti, porque a ti puede que no te encuentre.

**Qué hace en casa esperando** (`ESPERANDO`):

- Se queda cerca del cofre o de la puerta, quieto, sin dar vueltas ni alejarse más de 8 bloques del punto. Si un mob
  le ataca se defiende, pero no persigue: no se va a pelear al bosque y dejarte la carga tirada.
- Si la carga iba al cofre de suministros, la mete antes de sentarse. Si iba a tus pies, la lleva encima hasta que
  aparezcas: no la deja en el suelo, que ahí se la lleva un creeper. Te la da con un latigazo, no la suelta sola.
- Come solo si baja de 14 y lleva pan encima (9.2). La moral se le recupera con el descanso al ritmo normal (9.1).
- Gasta hambre de ocioso, 1 punto cada 5 minutos (9.2), **solo si está cargado y tickeando**. Una espera larga sin
  comida sí puede matarlo; en un chunk descargado el hambre se congela. La interfaz avisa si no tiene reserva.
- No acepta órdenes de nadie, ni tuyas por chat: las órdenes se dan de tú a tú, con la mano y el látigo. Pero el
  látigo sí le sirve para lo otro: **en `ESPERANDO` un latigazo no pide trabajo, pide la carga**. Te la da, se queda sin
  nada encima y se vuelve a sentar.
- Chat al llegar: *"Estoy en casa. Aquí me quedo, esperando."* Cuando vuelves y te acercas a menos de 8 bloques:
  *"Ya estás aquí. Perdona, no sabía si volvías."* y espera a que le des el latigazo para darte lo que trae. Si te
  fuiste lejos en vez de desaparecer, la línea es otra: *"Estás lejos. Me quedo en casa esperándote."*

**Si te vas a mitad de una orden**, no la abandona: la termina o agota el presupuesto de 6.8, y luego vuelve a casa. Lo
que no hace es empezar una orden nueva sin ti, porque no hay nadie que se la pida.

**Si la casa cae en un chunk descargado** llega igual, porque mientras vuelve lleva su propio ticket de chunk encima
(15.4). Y en cuanto se sienta a esperar lo suelta: el trozo se descarga solo y él deja de existir para el servidor hasta
que alguien vuelve. No se pierde nada, el esclavo está en su NBT y la casa en el mundo.

### 10.5 Muerte y reaparición (técnico)

Hay dos cosas que el documento llamaba "contrato" y conviene separar: la **orden** (efímera, se cancela al morir) y el
**vínculo** contigo (nombre, variante, skin, memoria; sobrevive). Morir cancela la orden, no rompe el vínculo.

- **Dónde se guarda lo que sobrevive.** La entidad muerta ya no existe, así que el legado vive en un `SavedData` del
  mundo (`EsclavoRegistro`, en `data/theslave_registro.dat`), indexado por `esclavoId` estable, con índice secundario
  `dueñoUUID → conjunto de esclavoId` para admitir hasta tres sin sobrescribirlos: nombre, variante, hash de la
  skin, memoria de estaciones (máx. 64), casa y última orden. Se escribe al adoptar y se actualiza al morir y cada vez
  que el esclavo se descarga, no por tick.
- **Cuándo vuelve.** Nunca por su cuenta ni por el paso del tiempo: **solo cuando el dueño completa el ritual de la
  tumba** (10.6). El registro guarda el estado `MUERTO` con la posición y dimensión de la tumba hasta entonces, aunque
  pasen semanas.
- **No hay duplicados.** El registro guarda `vivo` (UUID de la entidad) o `tumba` (posición, dimensión, tick de la
  muerte). Resucitar exige `vivo == null` y una tumba válida; al cargar un esclavo cuyo UUID no coincide con el del
  registro, esa entidad se descarta (arregla duplicados por mundos restaurados o `/summon` torpes).
- **Lo suelto.** El inventario cae como `ItemEntity` normal junto a la tumba, con el tiempo de despawn vanilla (5 min).
  El esclavo no tiene forma de recuperar por su cuenta lo que dejó: es tu trabajo, y la tumba te dice dónde.
- **Freno a la inmortalidad.** Ya no hace falta uno artificial: cada resurrección cuesta un ritual caro (10.6).
- **Un esclavo sin dueño que muere** no vuelve: el registro solo se escribe al adoptar.

### 10.6 La tumba y el ritual de resurrección

**La tumba.** Al morir (con dueño), se coloca el bloque `theslave:tumba` en el bloque donde cayó o, si ahí no cabe
(lava, vacío, agua, bloque sólido), en la última posición segura de su rastro (10.3) o, si no la hay, en su casa. Es una
lápida de piedra y tierra removida con su nombre grabado; el nombre y la variante salen de su `BlockEntity`
(`TumbaBlockEntity`: dueño, nombre, variante, hash de skin, tick de la muerte, número de veces que ha muerto).

- **Indestructible** para mobs, explosiones, fuego, pistones y líquidos. No se rompe sola.
- **Solo la rompe su dueño, agachado y con pico** (entierro definitivo): borra el legado y no suelta nada. Pide un segundo
  clic en 5 s, con el aviso *"¿Seguro, jefe? No hay vuelta atrás."* Para un tercero es un bloque de dureza infinita.
- Se ve desde lejos: el nombre flota sobre ella a 32 bloques, y al conectar el dueño ve en la action bar *"Paco ha muerto en x, y, z
  (Overworld)."* (sin chat ni toasts) Mientras existe, `vivo == null` y el contrato no puede traer otro esclavo a ese dueño.
- Solo existe en el Overworld (11). El látigo sobre una tumba no hace nada, salvo una línea en la action bar: *"No se azota a un
  muerto, jefe."*

**Ambiente permanente de la tumba** (barato, sin tick pesado): una columna fina de partículas `soul` que sube muy
despacio (una cada 2 s), el murmullo de `ambient.soul_sand_valley.mood` cuando un jugador se acerca a menos de 6 bloques
y una luz casi nula (nivel 0). No cuesta nada si no hay nadie cerca.

#### El ritual en tres tiempos

El ritual se hace **de noche** (`dayTime` 13000-23000) y se juega con un solo gesto: **suplicar** = clic derecho con la
**mano vacía** sobre la tumba (sin agacharse). **Nada de esto pasa por el chat**: todo va por pantallas propias y por
una capa de pantalla completa durante la escena. Tampoco hay iconos ni emojis: ni marcas de verificación, ni
símbolos; el estado se lee por texto, color y luz.

**1. Primera súplica: la pantalla de la lápida (`TumbaScreen`).**

Se abre una pantalla con forma de **lápida**: panel alto con la parte superior en arco, dibujado por código (sin
textura prefabricada) con piedra oscura, grietas y musgo apagado, borde biselado y vignette en las esquinas. El mundo de
fondo se oscurece un 60 % y se desenfoca. Se lee como una piedra grabada:

```
                 AQUÍ YACE
                   PACO
          Cayó en el Overworld, día 14
              Llevas 1 muerte con él

   La tierra pide, antes de oírte:

   Noche cerrada ................ Cumplido
   Cuatro antorchas de alma ..... Faltan 2 (este y oeste)
   Un contrato de servidumbre ... Cumplido
   Una manzana dorada ........... Falta
   Soledad (16 bloques) ......... Cumplido

              [ SUPLICAR ]        [ Cerrar ]
```

- Cada línea es **texto grabado**: las cumplidas brillan en azul alma con un halo suave; las que faltan están en gris
  hueso, hundidas, con el detalle de lo que falta en texto (nunca con un símbolo).
- Una antorcha ausente se señala además **en el mundo**: mientras la pantalla está abierta, el hueco donde falta
  cada antorcha de alma muestra una pequeña llama azul fantasma (partícula solo para el dueño). No hace falta contar
  bloques ni recordar la cruz.
- La pantalla se refresca cada 10 ticks (inventario, antorchas, hora, hostiles), así que se ilumina sola mientras
  colocas una antorcha o recoges la manzana. No hay coste y no hay peligro: **la lápida es el manual del ritual**.
- El botón **Suplicar** está apagado (letras hundidas y sin brillo) mientras falte algo, con el texto de la causa
  principal debajo ("Es de día; la tierra no te oye con esta luz"). En cuanto todo está cumplido se enciende en azul y
  late despacio. Teclado: `Intro` suplica, `Esc` cierra.
- Si el dueño no tiene nada que cumplir porque ya hay un ritual en curso en esa tumba, la pantalla muestra solo
  "Hay un ritual en curso" y no deja suplicar.

**2. Segunda súplica: el conjuro.**

Pulsar **Suplicar** con todo cumplido cierra la pantalla y arranca la escena, de 34 a 94 s a 20 TPS (23.6), con su cuenta de ticks en
la `BlockEntity`. Durante la escena el dueño se queda quieto en el círculo (puede girar la cabeza y usar la mano; si se
aleja más de 8 bloques, se interrumpe) y su pantalla pasa a modo **cine**: barras negras que entran por arriba y por
abajo en 1 s, HUD vanilla oculto (barra de vida, hotbar, mira) y vignette oscuro. Ver "La capa de pantalla" más abajo.

**3. La Muerte pide las ofrendas: la pantalla del altar (`OfrendaScreen`).**

Al final de la escena aparece **La Muerte** y mira al dueño. Un clic derecho sobre ella abre la **pantalla del altar**:
una losa baja y negra, con la mano abierta de La Muerte dibujada en el centro y **dos huecos grabados** (uno para el
contrato de servidumbre y otro para la manzana dorada), cada uno rotulado con su nombre en letras de ceniza. El dueño
**arrastra el item desde su inventario** (que se ve debajo, a oscuras) hasta el hueco:

- Cada hueco **solo acepta su item**; cualquier otro rebota con un temblor corto del hueco, sin gastarse.
- Al soltar el item correcto, el hueco se enciende y el item **parece arder, pero queda en custodia hasta sellar** (animación de llama de 1 s sobre
  el hueco, sonido de `fire.ambient`) y La Muerte responde con su frase en la parte alta de la pantalla.
- Con los dos huecos encendidos, el botón **Sellar el pacto** pasa a azul. Al pulsarlo se cierra la pantalla y empieza
  el sellado. Si cierras la pantalla a medias, lo ya entregado **queda en los huecos** (se devuelve solo si el ritual se
  interrumpe) y puedes volver a abrirla con otro clic sobre ella.

**Las ofrendas:** 1 contrato de servidumbre y 1 manzana dorada (no encantada). Las lleva el dueño en el inventario; no
se dejan en la tumba.

#### La capa de pantalla (`RitualOverlay`)

Todo texto de la escena sale en esta capa, que sustituye al chat y a los títulos de vanilla:

- **Barras de cine** arriba y abajo (12 % de la altura cada una), con entrada y salida suaves.
- **Subtítulos de La Muerte** centrados bajo el centro de la pantalla, en una tipografía serif propia de caracteres
  anchos y espaciados, **aparecen letra a letra** (32 caracteres/s), se mantienen y se disuelven. Color hueso con
  sombra negra; sin recuadro. No hay marcos, nombres ni prefijos: la propia posición y el temblor dicen quién habla.
- **Temblor de cámara** muy leve (± 0,4°) sincronizado con las campanadas y el latido; se puede quitar en ajustes.
- **Vignette** oscuro que se cierra de forma gradual hasta el sellado y se abre al final.
- **Destello** blanco de 4 ticks cuando cae el rayo visual y un pitido apagado que dura 1 s.
- **Aberración cromática** leve (separar los canales rojo y azul 1-2 px) durante la aparición de La Muerte, con un
  `PostChain` de pantalla completa mínimo. Si el cliente no lo soporta o hay shaders, se omite sin más.

Es **código de cliente** (un `HudRenderCallback` y una cadena de post-proceso). Se activa con un único payload
`RitualEscenaPayload` (fase, tick, texto, intensidad), así que el servidor sigue siendo quien manda el guion.

#### La escena, plano a plano

Todo lo dirige el servidor con un contador de ticks en la `BlockEntity` (`RitualEscena`), que envía el guion al cliente
por `RitualEscenaPayload` y ejecuta lo de mundo (partículas, sonidos, rayo, efecto Oscuridad).

| Tiempo | Qué pasa | Efectos |
|---|---|---|
| 0 s | Te arrodillas; entran las barras de cine | Se corta el ambiente. Un único latido de `warden.heartbeat`. Las cuatro antorchas parpadean. Subtítulo: *"Suplicas por su alma."* |
| 0-4 s | El mundo se apaga | Efecto **Oscuridad** (`darkness`) a todo jugador a ≤ 16 bloques; el viento sube; `ash` cae lenta; el vignette empieza a cerrarse |
| 4-8 s | La tierra respira | Niebla baja de `squid_ink` y `large_smoke` desde la tumba, a ras de suelo (alcance 6 bloques). Latido más rápido |
| 8-12 s | Tres campanadas | `block.bell.resonate` a pitch 0,5, una por segundo. Cada una hace parpadear las antorchas, suelta un anillo de `soul_fire_flame` y da un temblor de cámara |
| 12-16 s | La espiral | `soul` y `soul_fire_flame` suben en espiral sobre la tumba; relámpagos visuales lejanos (no sobre el círculo) con `setVisualOnly(true)` |
| 16-20 s | **Aparece La Muerte** | Se materializa de la niebla con fundido (3 s), de espaldas; los ojos se encienden en último lugar. Sonido grave `theslave:muerte_aparece`; aberración cromática |
| 20-24 s | Se gira | Silencio total, solo el latido. Subtítulo letra a letra: *"¿Quién osa llamar a quien yo me llevé?"* |
| 24 s en adelante | **Pide las ofrendas** | Extiende la mano. El clic derecho sobre ella abre el altar. Un rótulo tenue en la parte baja de la pantalla indica "Ofrece: contrato de servidumbre, manzana dorada", con lo entregado tachado en gris |

**Respuestas de La Muerte** (subtítulo, con su susurro):

- Contrato: *"Tu firma. La tinta no miente."*
- Manzana dorada: *"El oro de los vivos. Sea."*
- Un item que no pide: *"Eso no me sirve."* (no se gasta).

**Cuando están las dos**, y se pulsa **Sellar el pacto**, empieza el **sellado** (unos 10 segundos):

1. Cesa el latido; las campanas vuelven, ahora cada medio segundo.
2. La Muerte alza la guadaña. La tumba **se agrieta** (cambio de bloque a un estado `agrietada`, con partículas de
   `block` y sonido de piedra rota).
3. Un rayo **solo visual** cae sobre la tumba (`LightningBolt` con `setVisualOnly(true)`) y el cliente hace el destello.
4. El bloque se sustituye por tierra con una flor encima; el esclavo **emerge de la tierra** (animación de 2 s, con la
   niebla retirándose) y mira al dueño.
5. La Muerte, ya de espaldas, deja una última frase y se deshace en humo: *"Cuídalo mejor. La próxima vez no preguntaré."*
   Si es la segunda muerte o más: *"¿Otra vez? Ya tengo tu ficha."* Las barras salen, el HUD vuelve y la Oscuridad se
   levanta en 3 segundos.

**Cuando se interrumpe** (antorcha rota, amanecer, mob hostil en el círculo, dueño que se aleja, muere o se
desconecta, chunk descargado): La Muerte se detiene y su subtítulo dice según el motivo, *"No toleraré testigos."* /
*"Has tardado demasiado."* / *"Vuelve cuando estés preparado."*, **devuelve las ofrendas ya entregadas** (al inventario si caben; si no,
quedan recuperables en la `BlockEntity`, según 23.6), deshace el efecto Oscuridad y se desvanece. La tumba queda
intacta. Se puede volver a suplicar desde el principio. Esto no es un castigo: el ritual solo cuesta si se completa.

#### Estado, datos y rendimiento

Máquina de estados de la `BlockEntity`: `REPOSO → (pantalla de lápida, sin estado persistente) → CONJURO →
LA_MUERTE_PIDE → SELLADO → HECHO | INTERRUMPIDO → REPOSO`. Persistente en NBT: fase, contador de ticks, ofrendas ya
entregadas, UUID del dueño que lo inició y UUID de La Muerte.

- **Un solo ritual por tumba**, y como máximo uno por jugador a la vez. Máximo 4 escenas globales, sin cargar chunks adicionales. Si está lleno, el botón informa
  «La Muerte está atendiendo otra llamada» y no consume nada. El presupuesto incluye red y partículas.
- **Si el servidor se apaga o el chunk se descarga a mitad**, el ritual se interrumpe al cargar (La Muerte se reconcilia y elimina si quedó huérfana) y
  las ofrendas entregadas se devuelven. Nunca se pierde nada por un reinicio.
- **El coste de CPU es mínimo**: un contador, un barrido de hostiles en 16 bloques cada 10 ticks y partículas con
  alcance limitado. Nada de bucles pesados ni de cargar chunks.
- **Red**: `TumbaMenu` y `OfrendaMenu` son menús de contenedor vanilla (sincronizan huecos y datos con el mecanismo
  estándar), `RitualEscenaPayload` lleva el guion a la capa de pantalla. Partículas, sonidos y Oscuridad
  (`MobEffects.DARKNESS`, 40 ticks renovados cada 20) van por paquetes vanilla.

#### La Muerte (entidad y arte)

- Id `theslave:la_muerte`. Entidad no hostil, invulnerable, sin gravedad, inmóvil (no navega), 2,4 bloques de alto, no
  persistente (`isPersistenceRequired = false`), sin loot, sin colisión con jugadores.
- **Modelo propio**: figura alta y delgada con túnica andrajosa que no toca el suelo (flota 0,3 bloques), capucha con el
  rostro en sombra, manos esqueléticas y una guadaña sobre el hombro. Cuerpo base de 3 cajas más la túnica (cajas
  colgantes con UV de tela rota), brazos de huesos y la guadaña como `ModelPart` aparte.
- **Capa luminosa** (`RenderTypes.eyes`-style) con una textura de ojos brillantes y filamentos, independiente de la luz
  del bioma para que siga brillando en plena oscuridad.
- **Fundido** de aparición y desaparición con un valor sincronizado 0-1 (`EntityDataAccessor<Float> OPACIDAD`) que el
  renderer usa como alfa.
- **Voz**: `theslave:muerte_voz_1..6` (susurros graves), `muerte_aparece`, `muerte_se_va` y `ritual_campana`. Todas con
  subtítulo de accesibilidad de vanilla. Cada línea hablada sale además como subtítulo de cine en `RitualOverlay`,
  nunca en el chat.

#### Accesibilidad y ajustes

- `ritual.oscuridad` (servidor, true): activa el efecto Oscuridad. En false, la escena queda solo con partículas y sonido.
- Cada sonido con subtítulo para que el ritual se pueda seguir sin audio.
- `ritual.efectosCliente` (cada jugador, true): barras de cine, vignette, aberración y temblor. En false queda solo el
  subtítulo y el HUD no se oculta.
- Sin parpadeos rápidos: las antorchas parpadean como mucho 3 veces por segundo y los rayos son visuales y de un solo
  destello.

**Si pasan 7 días reales sin que el dueño conecte** (12.1), la tumba sigue ahí. Cualquiera que no tenga esclavo y complete
el ritual lo adopta como a un esclavo transferido: el nombre se conserva y se actualiza el UUID del legado.

**Para los operadores.** `/theslave tumba restaurar <jugador>` coloca una tumba nueva en la posición del dueño si la
  original se perdió (un `/fill` descuidado, un backup restaurado); `/theslave tumba resucitar <jugador>` se salta el
ritual para depurar; `/theslave tumba escena` lanza la escena sin requisitos para probar los efectos.

## 11. Qué NO puede hacer (alcance cerrado)

Esto es tan importante como lo que sí hace, para que el mod no se descontrole:

- No va al Nether ni al End. Si tú estás allí, no te sigue ni se asoma al portal: vuelve a casa y te espera (10.4).
  El contrato solo se usa en el Overworld: en el Nether o el End el pergamino no hace nada y el chat dice por qué.
- No entra en fortalezas, ciudades ancestrales ni estructuras con jefe.
- No comercia con aldeanos.
- No le da hierro lo que no puede minar. Sin pico de piedra no hay hierro, sin pico de hierro no hay diamante,
  sin pico de diamante no hay obsidiana. La escalera de herramientas vanilla es el techo del mod.
- No duplica items, no genera items, no busca en el creativo.
- **No abre cofres que no sean el cofre de suministros asignado.** Ni los tuyos de la base, ni los de nadie. Nunca,
  ni configurable. Si quiere material, lo saca del mundo.
- No rompe bloques que haya puesto un jugador.
- **No carga chunks parado.** Mientras trabaja mantiene cargado su trozo y los de alrededor para poder hacer su vida
  sin que haya nadie mirando (15.4). En casa, esperando o sin orden no mantiene nada: coste cero, igual que un cultivo
  en un trozo descargado.
- **No cruza lava ni fuego**, ni aunque sea el camino corto.
- **No hace parkour**: no salta huecos de más de un bloque ni se deja caer más de 3 sin agua debajo.
- **No pica hacia abajo en vertical**, nunca, ni un bloque.
- **No rompe el bloque que pisa**, ni el que sostiene arena, grava o agua.
- **No se rebela nunca.** Ni por hambre, ni por moral, ni por miedo, ni por aburrimiento. No protesta, no negocia, no
  miente, no sabotea y no se queda quieto a propósito. Es débil y es vulnerable, y lo sabe: por eso obedece. Lo único
  que puede pasar es que vaya más lento. Las únicas órdenes que no ejecuta son las imposibles (6.7) y las que rompen
  una regla de 6.6, y en esos casos lo dice antes de salir, pidiendo perdón por no llegar.
- **No corre más de ×3**, por muchos latigazos que le des, y el impulso se gasta en 24 bloques por latigazo (9.3).
  No hay forma de tener un esclavo a velocidad de cohete de forma sostenida.

## 12. Multijugador y propiedad

- Un esclavo por jugador (configurable hasta 3, `maxPorJugador`). Aparte, el servidor solo deja **4 esclavos
  trabajando a la vez** (`maxTrabajando`): el resto de órdenes esperan en cola, de pie y sin cargar nada (15.4, 15.12).
- Solo obedece a su dueño. Si le das un latigazo siendo ajeno, se queda quieto, baja la cabeza y no se mueve: no es
  suyo y no va a mirar siquiera lo que le pides.
- Si el dueño está desconectado, el esclavo termina lo que tenía entre manos —con su ticket de chunk, que para eso lo
  lleva (15.4)—, se va a casa y espera allí soltando el ticket. No empieza órdenes nuevas ni trabaja gratis.
- No se puede robar, pero sí heredar: tras 7 días reales sin conexión del dueño queda sin dueño y se adopta con un
  latigazo, conservando el nombre; si está muerto, se completa el ritual (12.1 y 23.5).

### 12.1 Transferir y caducar (qué hace el contrato sobre un esclavo ya adoptado)

- **Transferir.** El dueño usa un contrato con clic derecho sobre su esclavo quieto: el contrato se gasta, el esclavo
  suelta la carga a los pies del dueño y queda **sin dueño pero con nombre y etiqueta**, de pie, mirando al vacío. El
  primer jugador *sin esclavo* que le dé un latigazo lo adopta **sin pantalla de nombre**: el nombre es el que ya tenía.
  El legado conserva su `esclavoId`; cambia el dueño y actualiza ambos índices, sin borrar otros vínculos.
- **El límite se comprueba al adoptar**, contando vivos y tumbas del receptor. Liberar uno propio sí está permitido
  aunque el dueño esté en el límite. Exige estar ocioso, sin orden ni entrega pendiente; se desvinculan cofre y casa
  anterior para no transmitir acceso a suministros ajenos (23.5).
- **Caducar.** Si el dueño no conecta en 7 días **reales**, el esclavo pasa a sin dueño con su nombre (se cuenta con la
  hora del sistema guardada en el registro, no con el tiempo de juego, que se detiene con el servidor apagado). Mismo
  proceso que transferir: cualquiera con un látigo y sin esclavo lo adopta.
- **Un esclavo sin dueño no hace nada**: no sigue, no trabaja, no come, no gasta hambre. Es un maniquí hasta que alguien
  lo adopta.

## 13. Números de partida (para ajustar en pruebas)

| Cosa | Valor inicial |
|---|---|
| Radio de la visión de menas | Horizontal: 32 bloques, 48 en reintento; búsqueda vertical según 26 |
| Radio de reutilización de estaciones | 32 bloques |
| Límite de tareas por orden | 64 |
| Longitud del nombre | de 1 a 20 caracteres |
| Alcance del nombre | todos los jugadores, 64 bloques, a través de paredes |
| Probabilidad de cada versión | 1/3 exacto |
| Velocidad | 0.26 en hombre y mujer, 0.31 en duende |
| Vida | 16 en hombre y mujer, 12 en duende |
| Daño del puño | 2 en hombre y mujer, 1 en duende |
| Gasto de hambre | × 1 en hombre y mujer, × 0.5 en duende |
| Barra de hambre | 20 puntos, se llena comiendo |
| Gasto trabajando | 1 punto cada 90 s |
| Gasto ocioso | 1 punto cada 5 min |
| Gasto huyendo | 1 punto cada 40 s |
| Pan | 5 puntos |
| Velocidad a hambre 16-13 | 90 % |
| Velocidad a hambre 12-9 | 75 % |
| Velocidad a hambre 8-5 | 55 % |
| Velocidad a hambre 4-1 | 35 % |
| Velocidad a hambre 0 | 25 % y −1 de vida cada 4 s |
| Velocidad de vuelta a casa o al dueño | 100 % siempre, sin hambre ni moral |
| Casa del esclavo | el punto de adopción, o el cofre de suministros si se lo asignas |
| Radio de espera en casa | 8 bloques alrededor de la casa |
| Distancia del dueño para ir a por él | 128 bloques desde casa, más lejos espera |
| Distancia del esclavo para volver a casa por teletransporte | más de 256 bloques, o atasco sin ruta |
| Distancia para entregar al volver | a tu lado, 2 bloques |
| Forma de pedirle la carga | un latigazo sobre el esclavo |
| Hambre para comer solo | por debajo de 14 |
| Hambre que duplica la pausa por veta | por debajo de 8 |
| Aceleración por latigazo en camino | ×1.15 acumulativo |
| Tope de aceleración | ×3.00 |
| Duración del impulso | 24 bloques de recorrido por latigazo |
| Escala de render | 1.0 en hombre y mujer, 0.69 en duende |
| Skins disponibles por versión | todas las de su carpeta más las de `comun/` |
| Formato de skin | PNG de 64 × 64, o 64 × 32 antiguo convertido |
| Peso máximo de una skin | 32 KB |
| Skins que envía el servidor | una por skin distinta, no por esclavo |
| Cajas del modelo de mujer | 5 más que el de jugador (cintura, caderas y pecho) |
| Cajas del modelo de duende | 3 más que el de jugador (cadera, joroba y pierna partida en dos) |
| Inclinación de la espalda del duende | 20° hacia delante, fija en la geometría |
| Hitbox de la mujer | 0.6 × 1.8, igual que el hombre |
| Huecos de inventario | 12 en las tres versiones |
| Resurrección | solo con el ritual de la tumba; nunca automática |
| Ritual: antorchas de alma | 4, a 2 bloques de la tumba, en cruz |
| Ritual: ofrendas | 1 contrato de servidumbre + 1 manzana dorada |
| Ritual: hora | de noche, `dayTime` 13000-23000 |
| Ritual: gesto | "suplicar" = clic derecho con mano vacía sobre la tumba |
| Ritual: escena | 480 ticks de conjuro + hasta 1200 para ofrendas + 200 de sellado; 34-94 s a 20 TPS, dueño a ≤ 8 |
| Ritual: radio de interrupción por hostiles | 16 bloques (comprobación) / 3 bloques (durante la escena) |
| Ritual: efecto Oscuridad | a jugadores a ≤ 16 bloques, renovado cada 20 ticks (config `ritual.oscuridad`) |
| La Muerte | 2,4 bloques, flota 0,3, invulnerable, no persistente |
| Estado al resucitar | vida 50 %, hambre 6, moral 50 %, sin carga |
| Entierro definitivo | dueño agachado con pico, confirmación en 5 s |
| Visibilidad del nombre de la tumba | 32 bloques |
| Cadencia del chequeo de peligro | cada 10 ticks |
| Radio de búsqueda de agua al arder | 6 bloques |
| Caída máxima sin agua debajo | 3 bloques |
| Antorcha | 1 cada 8 bloques excavados |
| Bloques sólidos mínimos para hacer pilar | 8 |
| Rastro de vuelta | 256 posiciones, 2 KB por esclavo |
| Detección de atasco | 100 ticks sin moverse medio bloque |
| Escalón máximo al volver por lo excavado | 1 bloque |
| Vida útil de pico | madera 59, piedra 131, hierro 250, diamante 1561 bloques |
| Margen antes de cambiar de pico | 10 usos |
| Enfriamiento del látigo | 1 s |
| Durabilidad del látigo | 64; cobrar es gratis |
| Ventana para el primer latigazo | 5 s tras entregar el objeto |
| Silencio que confirma la orden | 1,5 s tras el último latigazo |
| Techo por latigazos | 1 = 10, 2 = 1 stack, 3 = sin techo (no apilables: 1 / 4 / 8) |
| Profundidad máxima de receta | 3 transformaciones del producto; 6 niveles totales con infraestructura |
| Presupuesto de tiempo por orden | 30 minutos reales |
| Veta completa | menas del mismo tipo a menos de 8 bloques |
| Pausa por veta | 10 s carbón/cobre/hierro, 40 s oro/lapis/esmeralda, 2 min diamante/redstone |
| Acumulación de fatiga | +50 % por veta siguiente |
| Tope por orden | 64 en carbón, cobre e hierro; 32 en el resto |
| Coste de moral por latigazo en trabajo | −10 %, tope −50 % |
| Recuperación de moral | +25 % por día |
| Recuperación de hambre | +5 por pan, menos por cualquier otra comida |
| Tono de todas sus líneas | servil, en primera persona, con disculpa o agradecimiento, sin una sola queja |
| Rebeldía, mentira o sabotaje | ninguno, en ninguna circunstancia (decisión 16) |
| Chunks mientras trabaja | Vecindario operativo 3 × 3; niveles mínimos por actividad, compartidos; medir también halo real (24) |
| Chunks cargados parado, en casa o esperando | 0 |
| Esclavos con ticket a la vez | 4 por servidor (`maxTrabajando`), una orden activa por jugador, cola FIFO |
| Esclavos por jugador | 1 (`maxPorJugador`, hasta 3) |

## 14. Fuera de la v1

Dejar fuera a propósito para poder terminarlo:

- Expediciones de cientos de bloques cargando mundo a su paso: su radio de trabajo es fijo (32 bloques; 48 en reintento), con ventana móvil de 15.4. Si el objetivo
  queda fuera del radio, vuelve y lo dice. El radio no se confunde con la huella simultánea en RAM.
- Aldeanos, comercio y granjas automáticas.
- Transferencia por libro, contratos hereditarios, testamentos.
- Elegir versión desde la interfaz (se sortea al usar el contrato y no se cambia). Lo que sí se cambia sin tocar el mod
  es la skin: se sueltan PNG en las carpetas de 3.2 y entran en el sorteo del siguiente contrato.
- Sincronización bonita de animaciones en red (la v1 usa el sistema de estados vanilla).

## 15. Plan técnico

### 15.1 Estructura

```
src/main/java/com/theslave/
  ElTheSlaveMod.java              registro de todo
  entity/
    EsclavoEntity.java           PathfinderMob con inventario, orden y cerebro
    EsclavoInventory.java        Container de 12 + referencia + mano
    OrderState.java              enum de estados
    Variante.java                DUENDE, HOMBRE, MUJER: tamaño, vida, velocidad, modelo, skins y tono
    Hunger.java                  barra de 20 puntos, gasto por actividad y comer solo (9.2)
    SpeedModifier.java           combina hambre, moral e impulso del látigo en un multiplicador (9)
    WhipBoost.java               multiplicador acumulativo con tope ×3 y gasto por bloques (9.3)
    HomePoint.java               la casa: punto de adopción o cofre de suministros (10.4)
    ChunkKeeper.java             concesión compartida de carga mínima viable; reposo sin tickets (15.4, 24)
    ai/
      FollowOwnerGoal.java
      LookAtOwnerGoal.java
      EatWhenHungryGoal.java      come lo que lleve al bajar de 14
      FleeToOwnerGoal.java
      DangerEscapeGoal.java         prioridad 0: fuego, lava, ahogo (sección 10.1)
      StuckEscapeGoal.java          escalera de desatasco de 10.3
      WorkGoal.java              empuja la cola de tareas un paso por tick
    nav/
      SafeWalkNodeEvaluator.java  lava y fuego como nodos intransitables (capa 1)
      Trail.java                  rastro circular de posiciones para volver (10.3)
    brain/
      Planner.java               el algoritmo de la sección 6.2
      ResourceScanner.java       barrido por paletas de sección (6.1)
      OreBudget.java             reloj, fatiga de veta y topes (6.8)
      StationMemory.java         mapa de estaciones persistido en NBT
      RecipeIndex.java           recetas por item de salida
      WorldKnowledge.java        tablas de menas, animales y herramientas (6.5)
      MiningRules.java           reglas de jugador al picar (6.6)
      HazardSense.java           capas 2 y 3 de la sección 10.1
      Task.java                  interfaz: isDone, tick, describe
      tasks/                     WalkToTask, MineTask, ChopTask, HuntTask,
                                 CraftTask, SmeltTask, PlaceStationTask,
                                 PickupTask, ReturnTask, ReturnHomeTask,
                                 WaitAtHomeTask
  item/
    ContratoItem.java
    LatigoItem.java            órdenes, techo, acelerón y **adopción con nombre** (3)
  net/
    NombrePayload.java         petición y respuesta del nombre al adoptar (S2C / C2S)
    SkinPayload.java           petición y envío de los píxeles de una skin (S2C / C2S)
  skin/
    SkinLibrary.java           lee la carpeta, valida los PNG y sortea la skin del contrato (3.2)
    SkinCache.java             skins conocidas por hash, en el servidor y en el cliente
  data/
    EsclavoRegistro.java         SavedData: legado por esclavoId e índice por dueño (10.5, 12.1)
    EsclavoLegado.java           record con codec versionado
  block/
    TumbaBlock.java              lápida indestructible salvo para el dueño (10.6)
    TumbaBlockEntity.java        dueño, nombre, variante, skin, fase del ritual y ofrendas entregadas
    RitualCheck.java             requisitos que muestra la lápida e interrupciones
    RitualEscena.java            guion por ticks: oscuridad, niebla, campanas, rayo visual y subtítulos
    TumbaMenu.java               menú de la lápida (requisitos sincronizados, botón Suplicar)
    OfrendaMenu.java             menú del altar: dos huecos filtrados y botón Sellar el pacto
  entity/
    LaMuerteEntity.java          figura del ritual: inmóvil, invulnerable, `OPACIDAD` sincronizada
  client/
    LaMuerteModel.java           túnica, capucha, manos de hueso y guadaña
    LaMuerteRenderer.java        alfa por `OPACIDAD` y capa luminosa de ojos
    TumbaScreen.java             lápida dibujada por código: grabado, halo azul alma, botón Suplicar
    OfrendaScreen.java           altar con la mano de La Muerte, huecos y animación de llama
    RitualOverlay.java           barras de cine, subtítulos letra a letra, vignette, temblor y destello
  net/
    RitualEscenaPayload.java     fase, tick, texto e intensidad del guion hacia el cliente
  menu/
    EsclavoMenu.java             pantalla de equipo (15.14): armadura, mano y bolsa de solo lectura
  msg/
    EsclavoChat.java             categorías, límite de ritmo y claves de lang (15.10)
  protect/
    BreakGuard.java              qué bloques puede romper y quién los protege (15.7, 15.13)
    FakeEsclavo.java             contexto de jugador automatizado aislado por dueño y nivel
  registry/
    ModEntities.java  ModItems.java  ModSounds.java  ModTickets.java
  client/
    EsclavoRenderer.java         renderiza el humanoide y el item de la mano
    TheSlaveModel.java            base común: esqueleto, UV de skin de jugador y animaciones
    JugadorModel.java            el cuerpo de jugador de toda la vida, sin cambios
    DuendeModel.java             espalda curvada, joroba y piernas cortas (15.2)
    MujerModel.java              las cinco cajas del cuerpo de mujer (15.2)
    EsclavoRenderState.java      estado de render (variante, skin, pose, item, armadura, negar)
    NombreScreen.java            casilla de texto "¿Cómo se llama?" y su paquete
    ElTheSlaveClient.java
  command/
    EsclavoCommand.java          /theslave simular <item> — imprime el plan sin ejecutarlo
```

### 15.2 Render del humanoide

**Tres modelos, muchas texturas.** El de jugador (hombre), el del duende, encorvado, y el de la mujer. Los tres
comparten esqueleto, nombres de partes y animaciones base: `head`, `body`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg`.
Lo que cambia son las proporciones, la postura y la forma de las cajas. El renderer elige modelo, textura y escala antes
de dibujar, con la variante del render state.

La textura **no sale de `assets/`**: sale de la carpeta del servidor (3.2) y llega por `SkinPayload`. Dentro del jar solo
hay tres PNG de reserva, uno por versión, que se usan si la carpeta está vacía o el archivo elegido está roto:

```
assets/theslave/textures/entity/esclavo_duende.png     por defecto
assets/theslave/textures/entity/esclavo_hombre.png     por defecto
assets/theslave/textures/entity/esclavo_mujer.png      por defecto
```

Las tres en formato de skin de 64 × 64. El render state lleva la variante, la skin elegida y la escala, y el renderer
resuelve todo antes de dibujar:

```java
float escala = switch (state.variante) {
    case DUENDE -> 0.69f;
    case HOMBRE, MUJER -> 1.0f;
};
TheSlaveModel modelo = switch (state.variante) {
    case DUENDE -> modeloDuende;
    case MUJER -> modeloMujer;
    case HOMBRE -> modeloJugador;
};
poseStack.scale(escala, escala, escala);
```

Como la escala se aplica a toda la matriz de la entidad, la armadura y el item de la mano encogen con el cuerpo sin
trabajo extra. El `EntityDimensions` de la sección 3 está calculado para que el modelo escalado quepa en su caja, y hay
que llamar a `refreshDimensions()` al cargar la entidad y al asignar la variante.

**Las cajas del modelo de duende.** Aquí no se cambian las proporciones del cuerpo, se cambia la postura. Todo medido en
píxeles de textura, sobre el esqueleto de jugador:

| Parte | Jugador | Duende |
|---|---|---|
| Cuerpo | caja única de 8 × 12 × 4, vertical | dos cajas: espalda 8 × 7 × 5 inclinada 20° hacia delante y cadera 7 × 5 × 4 en la base |
| Joroba | — | caja de 5 × 3 × 3 encima de la espalda, en la zona de los hombros |
| Cabeza | 8 × 8 × 8 sentada encima del cuerpo | igual, pero adelantada 2 px y bajada 1 px, con el cuello saliendo de delante del pecho |
| Brazos | 4 × 12 × 4 al hombro | 4 × 14 × 4, más largos que el muslo, colgando por delante de la rodilla |
| Piernas | 4 × 12 × 4 | dos tramos: muslo 4 × 6 × 4 con la rodilla doblada 15° y espinilla 4 × 7 × 4 |
| Altura | 32 px | 22 px del suelo a lo alto de la cabeza |

Son **tres cajas más** que el modelo de jugador: la cadera, la joroba y el tramo de pierna que se parte en dos. La
inclinación no se hace en tiempo real girando el cuerpo en cada frame, que pelea con las animaciones y con la capa de
armadura: **va en la geometría**. La espalda nace torcida, la cabeza y los brazos se colocan respecto a ella, y al caminar
solo hay que mover piernas y brazos como siempre, con un bamboleo más marcado.

Con 22 px de alto, la escala inicial de 0.69 da `22 / 16 × 0.69 = 0.94875` bloques. Se mide la geometría
final, incluidas poses y capa exterior, antes de cerrar el valor: debe encajar visualmente en su hitbox de 0.95
y en un túnel de un bloque. No se deduce solo de la escala del modelo de jugador.

Ojo con darle al duende la escala del jugador: a 1.0 esos 22 px medirían 1.375 bloques y ya no entraría en las galerías que él mismo
cava.

**Las cajas del modelo de mujer.** Cambios pequeños, todos medidos en píxeles de textura, sobre el esqueleto de jugador:

| Parte | Jugador | Mujer |
|---|---|---|
| Torso | caja única de 8 × 12 × 4 | dos cajas: pecho 9 × 5 × 4.5 arriba y cintura 7 × 4 × 3.5 en medio |
| Caderas | incluidas en el torso | caja propia de 10 × 4 × 4.5 al final del torso, más ancha que los hombros |
| Pecho | — | volumen suave de 8 × 3 × 2 delante del pecho, sin separación y sin marcar nada |
| Hombros y brazos | 4 × 12 × 4 | 3.5 × 12 × 3.5, un poco más estrechos |
| Piernas | 4 × 12 × 4 | 4 × 12 × 4 con la parte alta algo más ancha y el tobillo más fino |
| Cabeza | 8 × 8 × 8 | igual |

Todo eso son **cinco cajas más** que el modelo de jugador, ni una más. El volumen del pecho es una caja achatada, no
dos esferas: se ve la silueta de una mujer vestida, que es lo que hay que ver. La ropa de la skin es la misma que la del
hombre, y por eso se lee como una trabajadora y no como un maniquí.

**UV compartido.** Las cajas nuevas no piden textura nueva: usan `texOffs` sobre la zona del torso de la skin estándar,
repitiendo el mismo recorte que el `body` de jugador. Así el color y el dibujo del torso se estiran sobre la cintura y
las caderas sin tocar el PNG y cualquier skin de jugador vale igual. La caja del pecho usa el mismo frente del torso, así
que hereda el color de la camisa que lleve dibujada.

El duende hace lo mismo con lo suyo: la cadera repite la mitad baja del torso, la joroba usa la cara de atrás y la pierna
partida usa mitades del mismo recorte de pierna de jugador. La ropa se lee continua de la nuca a los talones aunque esté
torcido. Es la razón de que un modelo encorvado siga aceptando cualquier skin descargada.

**La armadura tiene que seguirle.** La capa de armadura vanilla son cajas de jugador con un píxel de holgura: sobre un
cuerpo con cintura y caderas, el peto flota en la cintura y las grebas se clavan en la cadera. Sobre una espalda torcida,
el peto se queda recto y flota por detrás. Para la mujer y para el duende se registran **dos juegos de modelos de armadura
propios**, copiados del humanoid vanilla y con las mismas cajas de cada cuerpo. Son cuatro capas por juego (casco, peto,
grebas, botas) y el trabajo de una tarde por cada uno. Si se deja para después, la v1 permite la armadura igual, con la
holgura torcida y un aviso en el README: funciona, se ve regular.

Tres cosas a comprobar en las fuentes de Loom antes de escribir el renderer, porque en 26.3 los modelos son genéricos
sobre su render state:

1. Si `PlayerModel` acepta un render state propio o si obliga a uno de jugador. Si obliga, se copia la geometría del
   modelo del jugador a un `TheSlaveModel` propio (son cuatro cajas y una capa de ropa) en vez de heredar. Los de duende y
   mujer son modelos aparte en cualquier caso, porque cambian la geometría.
2. Cómo se dibuja el item en la mano: o se delega en el renderer de items con la matriz del brazo derecho, o se sigue el
   mismo `submitCustomGeometry` que ya usa `GoalRenderer` en TheBall.
3. Cómo se registra un juego de modelos de armadura propio en 26.3 (`ArmorModelSet` y sus capas) y si hace falta
   registrarlo en el cliente para cada material o basta con uno genérico.

Las ranuras de armadura se renderizan con la capa de armadura normal en el hombre, y con el juego propio en el duende y
en la mujer (si no se hace, la v1 usa el normal para los tres). El nametag va con el sistema de nombres vanilla
(`setCustomNameVisible(true)`, sin `setCustomName` hasta que lo adopten). Como el nombre es obligatorio al adoptar, el
nametag **no tiene texto por defecto**: antes de la adopción no hay etiqueta y el esclavo pasa por un jugador más.

### 15.3 Persistencia

Todo en NBT de la entidad: dueño, **nombre**, **variante**, moral, hambre, inventario, referencia, **cantidad pedida**,
armadura, estaciones recordadas, **casa**, radio actual, número de intentos y si está en `ESPERANDO`. El nombre se
escribe una vez y no se toca más. La variante se escribe una vez y no se vuelve a tirar nunca, ni al morir. La casa se
escribe al adoptarlo y solo cambia si le mueves el cofre de suministros (10.4). El legado conserva identidad, nombre,
variante, skin, casa, memoria y última orden (10.5); el inventario cae y la orden activa se cancela.

El **ticket de chunk no se guarda en NBT**: se vuelve a pedir al leer la entidad, si sigue teniendo orden. Un ticket
viejo guardado en el save es un chunk forzado para siempre, y eso sí que no se perdona (15.4).

### 15.4 Chunks: autonomía completa con la menor carga viable

**Requisito prioritario: optimizar sin reducir funcionalidad.** El esclavo sigue moviéndose, excavando, buscando menas,
cazando, viendo animales cercanos, usando estaciones y volviendo aunque no haya jugadores. Al acercarse cualquiera
se ve la misma entidad en acción. No se reemplaza trabajo físico por producción calculada fuera del mundo.

**Base de corrección:** vecindario operativo de **3 × 3 chunks**, móvil, para navegación, bloques y entidades de la
actividad. Primero se demuestra que funciona con ese vecindario; después se reduce su coste por niveles de ticket,
compartición y reutilización. La optimización solo sustituye la base si pasa la misma batería funcional (24).
No se impone «un chunk total en RAM», porque el motor puede necesitar un halo para simular incluso el chunk central.

- **Reutiliza carga existente.** Si jugadores u otras fuentes ya aportan los niveles requeridos, no añade tickets.
  Distingue chunks visibles, block ticking y entity ticking; verse en pantalla no demuestra que todo esté simulado.
- **Concesión lógica por orden.** Identifica los requerimientos de entidad, ruta próxima, presa y estación. El gestor
  combina requerimientos coincidentes y mantiene el nivel mínimo que de verdad satisface cada uno. No mantiene nueve
  chunks con el nivel más caro por costumbre, pero tampoco deja sin ticking a la presa o al horno para ahorrar.
- **Huella verificable.** La relación entre ticket central, radio, nivel y halo se mide en M0. Si para una actividad
  concreta la versión exige el 3 × 3 completo a nivel de entidades, se conserva. El ahorro no puede cambiar el alcance
  de percepción, la capacidad de abrir camino, la continuidad del trabajo ni la visualización al acercarse.
- **Avance gradual.** Prepara el siguiente tramo antes de cruzarlo, sin barridos de carga o generación masiva. Una
  petición de preparación en vuelo por trabajador; petición duplicada se comparte. Generar terreno, si la ruta lo
  requiere, respeta el radio fijo de la orden y el planificador de carga del servidor. No se recortan fuentes por
  estar temporalmente descargadas: explora físicamente hasta poder observarlas.
- **Regreso incluido.** Cancelar o agotar tiempo inicia vuelta; mantiene lo necesario hasta destino seguro, luego
  libera su concesión. Reposo, entrega quieto y espera no fuerzan chunks. Reinicios usan la misma cuota global.
- **Observación continua.** Al acercarse un jugador, seguimiento y render normales de una entidad. Se sincronizan fase,
  herramienta, objetivo, animación y progreso reales, sin clonar ni reiniciar tareas. Si el jugador aporta simulación
  suficiente, se retira solo la carga propia redundante, con relevo comprobado para evitar un tick de congelación.

La zona de trabajo es fija: 32 bloques desde el origen, 48 en reintento. La ventana operativa se mueve dentro de ella;
ningún movimiento amplía el territorio autorizado. Se mantienen hasta **4 órdenes activas por servidor**, una por dueño;
no se añade una cuota de «un solo autónomo» que reduzca el funcionamiento decidido. Se escala primero el coste de
búsqueda y mantenimiento, nunca se omite una interacción física o una comprobación de seguridad.

El coordinador conserva solicitudes y posiciones para recuperar órdenes sin depender del tick de una entidad descargada.
Solo libera concesiones propias, nunca forceload de administradores ni tickets de otros mods. No usa tickets de jugador,
perfiles conectados ficticios o forceload persistente como atajo. Mide chunks exclusivos, compartidos y halo real.
Los random ticks de cultivos se verifican aparte en la versión objetivo; el mod no acelera ni fabrica crecimiento.

### 15.5 Mixins

Se prioriza API pública. M0 debe confirmar si los eventos de botín, colocación completada y protección cubren todos los
casos. Si falta un punto imprescindible, se documenta un mixin acotado y su prueba; no se promete cero mixins antes
de comprobarlo. Llamar directamente a `setBlock` o calcular drops no equivale a una interacción de jugador autorizada.

### 15.6 Comando de desarrollo

`/theslave simular minecraft:iron_ingot 3` imprime el plan que generaría, sin ejecutarlo y sin mover al esclavo.
Es la herramienta que va a hacer que este mod sea depurable en vez de un misterio.

`/summon theslave:esclavo ~ ~ ~ {Variante:"duende"}` fuerza una versión concreta sin tirar el dado, para probar las
tres sin gastar contratos. Sin `Nombre` sale sin dueño y sin etiqueta, listo para probar la adopción a latigazos.

`/theslave nombre <nombre>` es solo para operadores y para depurar: renombra a un esclavo ya adoptado sin pasar por la
pantalla. En partida normal no existe, porque el nombre es para siempre.

`/theslave skins recargar` relee la carpeta de 3.2 sin reiniciar el servidor, y `/theslave skins listar` imprime cuántas
skins hay en cada carpeta y cuáles se han ignorado por tamaño o por formato. Son los dos comandos que hacen falta para
saber por qué "la skin que he metido no sale".

`/theslave peligro` imprime en el chat el estado de las tres capas: si está ardiendo, si está en lava, el siguiente
nodo de su ruta y la caída que tiene debajo. Es lo que permite depurar "por qué se ha quedado parado" sin adivinar.

`/theslave saber minecraft:diamond` imprime lo que sabe del mundo sobre ese item: en qué capas lo busca, qué pico
necesita, qué animal lo da si es comida y cuántos picos calcula que va a gastar. Es la forma de ver si una tabla de 6.5
está mal sin esperar a que el esclavo se muera para descubrirlo.

### 15.7 ¿Qué es "natural"? El problema más duro del diseño

La sección 7 dice "no rompe bloques puestos por jugadores". **Minecraft no marca quién puso qué bloque**: una pared de
piedra de tu base y una pared de piedra de una cueva son el mismo `BlockState`. Hay que decidirlo con lo que sí se sabe,
en tres capas. Se precalculan al planificar y se **revalidan inmediatamente antes de cada modificación**:

1. **Lista blanca por etiqueta.** Solo rompe lo que está en `theslave:rompibles`: piedra y variantes naturales,
   deepslate, tierra, arena, grava, menas (`#minecraft:*_ores` y `#c:ores`), troncos y hojas, cultivos maduros. Cualquier
   cosa fuera de la etiqueta es intocable. Por defecto no están piedra tallada, ladrillos, tablones, cristal, lana,
   nada con `BlockEntity` y nada de `theslave:no_romper` (spawner, bedrock, portales).
2. **Zona de base.** Un **marcador de base** es cualquier bloque que casi nunca es natural: mesa de crafteo, horno,
   cofre, cama, puerta, cartel, estandarte, lectern, barril. Una antorcha en pared de construcción refuerza esas señales;
   una antorcha aislada en corredor minero no crea una zona de base por sí sola (26.2). Alrededor de cada
   marcador hay una **zona protegida de 10 bloques** donde el esclavo no rompe nada de la lista blanca. Se calcula una vez
   por barrido y se guarda en un `LongSet` de secciones (cuesta una consulta por chunk, no por bloque).
   - **Excepción: estructuras generadas.** Dentro de la caja de unas minas abandonadas sí trabaja (los raíles y las mesas
     de ahí no cuentan como base), con `StructureManager`; aldeas y cualquier otra estructura son zona protegida entera.
3. **Árboles de verdad.** Un tronco solo es talable si forma un árbol: al menos una `leaves` con `persistent=false`
   (las que genera el mundo; las que coloca un jugador son persistentes) en un radio de 3 bloques del cluster. Un
   chiringuito de troncos no se tala.

**Registro de colocaciones, opcional (v1.1).** Con la API de Data Attachments de Fabric se puede guardar por chunk un bitset de
"bloques puestos por un jugador" y consultarlo al romper. Debe registrar colocaciones **completadas**, no el intento
de uso de `UseBlockCallback`; se limpia al romperse y necesita tratar pistones, dispensadores y cambios de otros mods.
No protege construcciones anteriores a instalarlo ni demuestra origen natural. Sin esos casos cubiertos no se llama
exacto ni se promete un porcentaje de garantía. La heurística de v1 se complementa con permisos y exclusiones (23.5).

**Qué hace cuando el único camino cruza zona protegida:** lo mismo que en 6.6 regla 8: no entra y lo dice (*"Para
llegar tendría que pasar por tu base. No me atrevo."*).

### 15.8 Romper, recoger y fabricar "de verdad" (API)

El invariante de la sección 2 se audita desde una capa única de operaciones:

```java
public enum Fuente { DROP_BLOQUE, BOTIN_MOB, RECETA, ENTREGA_DUENO, COFRE_ASIGNADO }
// EsclavoInventory no tiene add(ItemStack): solo insertar(ItemStack, Fuente)
```

Ninguna otra clase puede mutar el `Container` interno. La revisión estática comprueba esa encapsulación; JUnit y
GameTests comprueban la conservación de objetos ejecutando operaciones reales. Pasar un enum válido no demuestra
procedencia: cada inserción referencia la extracción, rotura, muerte o receta que la produjo (23.4).

**Romper bloques.**

- Se usa un `FakePlayer` de Fabric API (`FakeEsclavo`, contexto aislado por dueño y nivel) equipado con la herramienta de la mano. Sirve para
  tres cosas: calcular el tiempo con `state.getDestroyProgress(fake, level, pos)`, llamar a `Block.getDrops(state, level,
  pos, blockEntity, fake, tool)` con encantamientos reales (fortuna, toque de seda) y disparar `PlayerBlockBreakEvents`
  para que los mods de protección puedan vetar (15.13).
- **Tiempo por bloque** = `ceil(1 / destroyProgress)` ticks, dividido por el multiplicador de 9.4. Un pico de madera
  en piedra tarda lo mismo que tú, no instantáneo. Mientras pica, mira al bloque y hace la animación de golpe
  (`swing`) cada 5 ticks, con partículas y sonido de rotura vanilla.
- **No se usa `destroyBlock(pos, true)`**, que suelta los drops al suelo. Se usa `destroyBlock(pos, false)` (partículas y
  sonido, sin drops) y los drops que dio `getDrops` se insertan **directamente** en su inventario con `Fuente.DROP_BLOQUE`.
  No hay `ItemEntity` que se pierda en la lava ni que recoja otro jugador, y no se cuenta ninguna entidad más.
- **Desgaste**: `tool.hurtAndBreak(1, level, fake, ...)` por bloque. Si se rompe, sigue 6.5 (repuesto).
- **XP**: los bloques que dan experiencia (carbón, diamante, redstone, lapis) y las recetas de horno acumulan en
  `xpPendiente`. Se entrega al cobrar, como orbes junto al dueño (`ExperienceOrb.award`). Configurable (`xpAlDueno`).

**Matar mobs.** Ataca por la ruta de daño real de la versión objetivo. El botín se atribuye a la muerte mediante un
contexto de loot/evento y los UUID de las `ItemEntity` que produjo, nunca solo por cercanía. La ventana de **4 bloques
y 5 segundos** limita la recogida de esos UUID, pero no prueba quién los generó. Nunca recoge objetos tirados por un
jugador ni los de otra muerte simultánea. Si no hay un hook fiable, se limita la caza hasta implementarlo y probarlo.
`setCanPickUpLoot(false)` evita la recogida vanilla indiscriminada.

**Craftear.** No abre una pantalla: busca la receta en `RecipeManager`, comprueba que tiene los ingredientes, los
consume del inventario y deja el resultado con `Fuente.RECETA`. Para recetas 3×3 exige estar a ≤ 3 bloques de una
mesa de crafteo (la suya, la tuya o la que coloque). Tarda 20 ticks de animación por receta.

**Basura y huecos llenos.** 12 huecos se llenan con piedra y tierra en un minuto. Regla: los items de
`theslave:descartables` (tierra, piedra, grava, andesita...) se sueltan al suelo en cuanto el hueco falte y no sean
parte del encargo, a 3 bloques de la galería. Nunca se tiran: herramientas, armadura, comida, antorchas, combustible,
el item del encargo ni los ingredientes del plan. Si no queda hueco y no hay nada descartable, **da la orden por
terminada** y vuelve con lo que tiene (*"Voy lleno, jefe. Vuelvo."*).

### 15.9 Hornos, combustible y tiempo (para estimar de verdad)

El planificador necesita saber cuánto tarda un plan antes de empezar (para avisarte y para no cometer el error de 6.7
de "bloque de hierro"). Números de vanilla, que son los que usa la estimación:

| Cosa | Valor |
|---|---|
| Fundir un item (horno) | 200 ticks (10 s) |
| Fundir un item (alto horno / ahumador) | 100 ticks (5 s), solo menas / comida |
| Carbón o carbón vegetal | 1600 ticks = 8 items fundidos |
| Tronco, tablón | 300 ticks = 1,5 items |
| Palo | 100 ticks = 0,5 items |
| Hierro × 20 | 4000 ticks (3 min 20 s) con 3 carbones |

- **Combustible**: `FuelValues` del servidor (incluye el de otros mods). Elige el que menos sirve para otra cosa:
  primero lo que esté en `theslave:combustibles_baratos` (palos, tablones sobrantes), luego carbón vegetal, luego carbón.
  Nunca usa un item del encargo, y nunca madera que necesite para una herramienta del plan.
- **Hornos en paralelo**: si el lote supera 16 items y hay piedra de sobra (8 por horno), coloca hasta 3 hornos. Una
  hornada de 64 de hierro baja de 10 a 4 minutos. La estimación del plan lo tiene en cuenta.
- **El horno exige block-entity ticking efectivo.** No se deduce de estar a menos de 24 bloques. El gestor incluye
  la estación mientras la hornada propia esté activa; se coloca cerca del tajo para compartir carga. Antes de irse
  lejos, completa o recoge lo suyo. Optimizar tickets no congela la hornada ni inventa su progreso.
- **Tareas no bloqueantes**: `SmeltStart` (carga el horno y sigue) y `SmeltCollect` (lo recoge cuando acaba). Mientras
  el horno trabaja, el esclavo sigue picando dentro de la zona del ticket. El reloj de 6.8 sigue corriendo.
- **Hornos ajenos**: usa un horno de tu base **solo si lo encuentra vacío** (los tres huecos). Mete el combustible que
  lleva él, recoge lo suyo y lo deja vacío. Si hay algo dentro, no lo toca: coloca el suyo. Nunca saca items de un
  horno que no sean los que él cocinó (comparando contra una lista de lo que metió).

### 15.10 Mensajes, idiomas y límite de ritmo

Hoy las líneas del documento son strings sueltos. En código hay que tratarlas como un sistema:

- **Claves de idioma, no strings**: `theslave.msg.<categoria>.<clave>.<variante>.<n>` en `lang/es_es.json` (idioma del
  mod) con `en_us.json` de reserva. Minecraft no sortea entre varias líneas; se hace en código con un contador
  (`n = random(1..N)`, N como constante por clave) para que cada variante tenga 2-3 formas de decir lo mismo.
- **Categorías y prioridad**:
  - `CRITICA` (informe de fallo, muerte, hambre 0, atascado): se envía siempre.
  - `INFO` (plan, progreso, llegada): máximo **6 por minuto** por esclavo; la séptima se descarta.
  - `AMBIENTE` (hambre, látigo, tararear): cada clave tiene su propio **enfriamiento de 30 s**, para que a ×3 no escupa
    el mismo "¡Voy!" doce veces.
- **A quién llega**: solo al dueño (`sendSystemMessage`), con el formato `<Nombre> texto`. Ningún otro jugador ve el
  chat de un esclavo ajeno. Lo que sí ven todos es la etiqueta del nombre (3).
- **Progreso**: cada 5 minutos de orden, y siempre al encontrar una veta o terminar una fase del plan: *"Llevo doce de
  veinte, jefe."* Es la única información de "¿qué está haciendo?" que el diseño no tenía, además de la action bar (15.14).
- **Rotación de voces**: la variante (duende/hombre/mujer) selecciona el set de líneas; el set se resuelve al renderizar
  la clave, no al enviarla, así que cambiar de variante no afecta a los mensajes ya enviados.

### 15.11 Persistencia robusta

- **Versión de datos**: cada esclavo y el `EsclavoRegistro` escriben `DataVersion` (entero propio del mod, empieza en 1).
  Al leer, un `if (version < N)` por migración. Sin esto, el primer cambio de formato rompe los mundos de pruebas.
- **Topes para que el NBT no crezca**: estaciones recordadas ≤ 64, rastro ≤ 256, mensajes recientes ≤ 8, inventario ≤ 12 +
  referencia + mano + 4 de armadura. Lo que sobra se descarta por antigüedad.
- **Estado diferido**: un esclavo en chunk descargado no se tickea (15.4), pero su moral debe recuperarse. Al cargar
  se aplica `gameTime - ultimoTick` como una sola actualización: la moral sube lo que toque (máx. +100 %) y la
  hambre **no baja** (decisión: el hambre se congela con el chunk descargado, no se castiga por estar apagado).
- **UUID y registro**: `esclavoId` identifica el vínculo; `entityUUID` la instancia viva. No ver una entidad descargada
  no demuestra que haya desaparecido. Antes de recrearla se carga su último chunk y se reconcilian registro y entidad
  (23.6); una discrepancia se bloquea para recuperación, nunca crea otra copia con el inventario anterior.

### 15.12 Presupuesto de CPU y cola global

Lo que más puede hacer daño al TPS son el barrido de menas y el planificador. Tope duro:

| Cosa | Tope |
|---|---|
| Barrido de menas | ≤ 2 ms por tick entre todos los esclavos, repartido en lotes; continúa el tick siguiente |
| Planificador (6.2) | ≤ 1 ms por tick global; plan de 64 tareas, profundidad total 6 y candidatos limitados |
| Pathfinding | el vanilla (`maxVisitedNodes` por defecto); ruta nueva como máximo cada 10 ticks |
| `getEntities` de la espalda | cada 160 ticks, radio 8 |
| Chequeo de peligro | cada 10 ticks, 6 lecturas |
| Esclavos trabajando a la vez | 4 por servidor (`maxTrabajando`) |

- **Cola FIFO**: si hay 4 trabajando, la orden nueva se acepta, el esclavo avisa (*"Hay cola, jefe. Espero mi turno."*)
  y su reloj de 30 minutos **no empieza** hasta que le toque el ticket. No pierde la orden.
- **Barrido sobre chunks cargados únicamente.** El barrido de 6.1 usa `ServerChunkCache#getChunkNow` y nunca carga
  chunks por sí mismo. Con el esclavo trabajando la base operativa es 3 × 3 y el alcance observable es
  variable según dónde esté dentro del chunk, no un círculo garantizado de 32. Cuando la orden empieza en la base,
  los chunks del dueño pueden ampliar lo observable. **El reintento no pide un ticket 7 × 7**: explora por tramos con
  la misma ventana 3 × 3 y hasta 48 bloques del origen. Amplía territorio, no la huella simultánea de carga.
- **Desplazamientos largos en tramos**: el pathfinding no cruza chunks sin cargar. `WalkToTask` parte el viaje en
  tramos de ≤ 12 bloques y mueve el ticket al avanzar; al llegar al final de un tramo, el siguiente ya está cargado.
- **`/theslave perfil`** (operadores) imprime milisegundos medios del último minuto por categoría: barrido,
  planificador, pathfinding y tareas. Es el instrumento para detectar un esclavo que está haciendo trabajo caro.

### 15.13 Compatibilidad, protección y datapacks

- **Protección de zonas**: `BreakGuard` valida romper, colocar, atacar y acceder a contenedores por separado. El
  `FakePlayer` aporta contexto; no dispara automáticamente todos los callbacks al modificar el mundo. Se verifican
  los eventos invocados y se añaden adaptadores solo para mods de claims compatibles con esta versión de Fabric.
  Un veto siempre se respeta. Si el adaptador no puede decidir, la operación se rechaza y se informa; no se ofrece
  desactivar protecciones como solución a una incompatibilidad (23.5).
- **Etiquetas de datapack** (todas en `data/theslave/tags/`), para que un servidor ajuste sin recompilar:
  - `block/rompibles`, `block/no_romper`, `block/menas_extra` (6.5: menas de otros mods sin etiqueta estándar).
  - `item/descartables`, `item/no_pedible` (bedrock, huevos, bloques de comando, items de creativo),
    `item/combustibles_baratos`.
  - `entity_type/cazables` (qué mobs cuenta como comida o botín), `entity_type/evitar` (creeper, warden...).
- **Recetas de otros mods**: el `RecipeIndex` ignora los `RecipeType` que no sean los de 6.1. Una receta cuyo ingrediente
  sea de un mod pero cuya fuente sea alcanzable pasa la criba (6.7). Receta cuya salida esté en `no_pedible`, no.
- **Cliente y servidor**: el mod es obligatorio en los dos (entidad propia, renderer, pantalla). Un cliente vanilla no
  puede conectar; se indica en el README.
- **PvP**: el dueño no puede dañar a su esclavo con armas ni con el látigo (el látigo no hace daño). Otros jugadores sí
  pueden, si el servidor tiene PvP activado; el esclavo es "débil y vulnerable".

### 15.14 Interfaz y sensación de juego

- **Pantalla de equipo** (`EsclavoMenu`, agachado + mano vacía): 4 ranuras de armadura, mano, y la bolsa de 12 huecos
  **en solo lectura** (se ve, no se saca: sacar la carga es un latigazo, 5). Un botón "Quieto / Sígueme" (la única
  manera de sentarlo en la base) y su estado: hambre, moral, orden actual y tiempo restante. Es la solución al "darle
  armadura a clics sueltos" del diseño original, que es incómodo y peligroso.
- **Action bar con mano vacía**: *"Paco · Picando hierro · 18:42 restantes · Hambre 14 · Moral 80 %"*. No satura el chat.
- **Indicadores en el mundo**: la referencia flota sobre su cabeza (ya); un reloj de arena pequeño si está en la cola
  (15.12); gotas de sudor (partículas `splash`) si la moral está por debajo del 50 %; con el impulso del látigo, estela
  de partículas `crit` y animación de correr con brazos exagerados. Todo con distancia de render corta (32 bloques).
- **Sonidos con subtítulos**: `theslave.latigo_chasquido`, `theslave.esclavo_negando`, y un par más —aceptar, jadear—
  con subtítulo en `lang`, para que el juego sea accesible sin audio.
- **Voz**: los gruñidos de aceptar/negar usan `pitch` por variante (duende 1.5, mujer 1.2, hombre 1.0, ya definido) con
  una variación aleatoria de ±5 %, para que dos esclavos juntos no suenen idénticos.
- **Animaciones**: caminar, picar, talar, negar, saludar, y una **de arrastre** cuando la hambre está por debajo de 5
  (los brazos cuelgan, la cabeza baja). Cada una es una pose, no una animación nueva.

### 15.15 Casos límite que hay que resolver antes de dar el mod por bueno

| Caso | Qué debe pasar |
|---|---|
| El dueño muere con el esclavo trabajando | Sigue; al terminar vuelve a casa y espera (10.4) |
| Servidor se apaga a mitad de una orden | Al arrancar, retoma la orden si tenía ticket; si no hay plan válido, vuelve a casa |
| Chunk de trabajo regenerado o borrado | Pierde el plan, lo dice y vuelve a casa por el ancla |
| Esclavo cae al vacío / se queda bajo bedrock | Muere normal (10); la tumba va a su última posición segura o a casa (10.6) |
| Ritual a medias y se hace de día / se rompe una antorcha | La Muerte se retira y devuelve las ofrendas ya entregadas (10.6) |
| Un mob entra en el círculo durante el ritual | Se cancela; las ofrendas entregadas vuelven al dueño (10.6) |
| Servidor se apaga o chunk se descarga durante la escena | Al cargar se interrumpe y se devuelven las ofrendas (10.6) |
| Suplicar de día o sin materiales | La lápida muestra qué falta y el botón queda apagado; no cuesta nada (10.6) |
| Dar a La Muerte un item que no pide | *"Eso no me sirve."* y no se gasta (10.6) |
| Tumba destruida por un `/fill` o un backup | `/theslave tumba restaurar <jugador>` (10.6) |
| El dueño muere durante el ritual | Se interrumpe; la tumba y las ofrendas siguen |
| Dueño cambia de dimensión mientras trabaja | Sigue; al terminar espera en casa |
| Dueño borra el cofre de suministros | La casa vuelve al punto de adopción |
| Dueño borra el horno que él colocó | El plan se replanifica; si no puede, informa del fallo |
| Dos jugadores le dan un latigazo a la vez | Solo vale el del dueño; el otro recibe "ajeno" |
| Mob hostil lo ataca con la carga encima | Se defiende o huye al dueño (7); si muere, todo cae al suelo |
| Se llena el inventario del dueño al cobrar | Lo que no cabe cae a sus pies y se avisa (5) |
| Llega 0 de hambre en mitad de la mina | Sigue despacio (9.2); si muere, queda su tumba (10.6) |
| Reinicio con 4 tickets pedidos | Se vuelven a pedir en orden de llegada; si faltan, los demás esperan |
| Mod de protección veta una rotura | Esa rotura se trata como bloque protegido y se replanifica |
| Un cliente sin la skin conectado | Dibuja la skin de reserva del jar hasta que le llegue el paquete (15.16) |

### 15.16 Skins: normalización y red (técnico)

El apartado 3.2 dice qué se ve; esto es cómo se hace para que no falle.

**Normalización en el servidor, una sola vez, al cargar la carpeta:**

1. Se lee la cabecera del PNG (`IHDR`) **antes de decodificar**: si no es 64 × 64 o 64 × 32, se descarta sin tocar los
   píxeles. Con el límite de 32 KB se evita cualquier PNG-bomba.
2. Se decodifica y se convierte a RGBA de 64 × 64 (en 64 × 32 se copian las extremidades derechas a las izquierdas con
   espejo, como hace el cliente vanilla).
3. **Brazos finos (slim)**: las skins estilo Alex tienen los brazos de 3 px. Se detecta mirando las columnas 54-55,
   filas 20-31: si son transparentes, es una skin slim. Como la geometría del duende y la mujer es fija, **se expande
   a brazo ancho duplicando la columna central del brazo** al normalizar. El hombre acepta ambos (el modelo de jugador
   de vanilla ya tiene las dos geometrías). Sin esto, una skin slim sobre el modelo ancho sale con una franja rota.
4. Se calcula el hash **de los píxeles normalizados** (SHA-256 completo: 32 bytes, 64 caracteres hexadecimales) y ese es el id. Dos archivos distintos que
   normalizan igual comparten textura. (El 3.2 decía "hash del archivo": mejor del contenido normalizado.)

**Red**: el id viaja en un `EntityDataAccessor<String>` del esclavo (así lo sabe cualquier cliente que lo ve aparecer, no
solo el que estaba conectado). El cliente, si no tiene esa textura, manda un `SkinSolicitud(id)` y el servidor responde
con un `SkinDatos(id, byte[16384])` (RGBA crudo, no PNG: 16 KB, sin decodificar en el cliente). Se registra como
`DynamicTexture` bajo `theslave:skin/<hash>`. Mientras llega, se dibuja la skin de reserva de la variante. La caché
del cliente se limpia al desconectar y tiene un tope de 64 texturas (LRU).

**Capa exterior (ropa por encima).** Una skin de jugador de 64 × 64 trae una segunda capa (chaqueta, mangas, pantalones,
sombrero). Los modelos del duende y la mujer son geometría propia, así que cada caja nueva necesita **su par exterior**
(mismo UV de la capa exterior, inflado +0,25 px). El modelo de jugador (hombre) ya la trae. Sin los pares, la ropa de
la capa exterior de cualquier skin descargada desaparece. Las cajas "5 más" y "3 más" de la sección 13 son del cuerpo
base; con la capa exterior son el doble.

## 16. Recetas e ids del mod

| Id | Qué es |
|---|---|
| `theslave:contrato` | Item. Trae un esclavo sin dueño. |
| `theslave:latigo` | Item. Adopta, confirma órdenes, cancela, acelera y asigna cofre. |
| `theslave:esclavo` | Entidad. |
| `theslave:tumba` | Bloque con `BlockEntity`. Lo coloca el mod al morir un esclavo; no se craftea ni está en creativo. |
| `theslave:la_muerte` | Entidad. Aparece solo durante el ritual (10.6); no persiste, sin spawn egg. |
| `theslave:ritual_campana` | Sonido de las campanadas del ritual. |
| `theslave:muerte_aparece`, `theslave:muerte_se_va` | Sonidos graves de aparición y retirada de La Muerte. |
| `theslave:muerte_voz_1` … `muerte_voz_6` | Susurros graves que acompañan cada línea que dice. |
| `theslave:esclavo_negando` | Sonido de negación con la cabeza. |
| `theslave:latigo_chasquido` | Sonido del látigo. |

Texturas de reserva dentro del jar: `assets/theslave/textures/entity/esclavo_duende.png`, `esclavo_hombre.png` y
`esclavo_mujer.png`. Se usan solo si la carpeta de 3.2 está vacía o el archivo elegido está roto. Las skins de verdad
son las que hay en `config/theslave/skins/<version>/`, en PNG de jugador de 64 × 64 o 64 × 32. Los sonidos son los
mismos tres para todas. El tono cambia con la variante: duende 1.5, mujer 1.2, hombre 1.0.

Recetas, en texto para el README cuando toque:

- **Contrato**: fila de 3 papel, con saco de tinta encima del centro y pluma debajo. Sale 1.
- **Látigo**: palo arriba a la izquierda, 2 cuero en diagonal, 1 hilo. Sale 1.

## 17. Decisiones y dudas

### 17.1 Cerradas

1. **Cofres ajenos: no.** Solo el cofre de suministros que le asignes. Ni los tuyos de la base, ni los de nadie, ni
   configurable. Aplicado en 11 y 6.4.
2. **Los latigazos marcan el techo de cantidad**, no un comando aparte. Los tres tienen el mismo límite de tiempo: lo
   que cambia es el tope. Uno hasta 10, dos hasta un stack, tres sin tope (revisado: más latigazos, más trabajo; ver 18). Con un item que no apila, 1, 4 y 8.
   Aplicado en 4, 5, 6.8 y 13.
3. **Conoce todas las recetas del servidor**, incluidas las de otros mods, pero solo ejecuta cadenas de tres saltos en
   mesa, horno, alto horno o cortapiedras. Aplicado en 6.1 y 6.7.
4. **La tirada de 1/3 es fija**, en código, sin ajuste por servidor. Aplicado en 3.1.
5. **Aunque ya tengas el item, lo consigue igual.** El objetivo es quitarte el trabajo, no completarte el inventario.
   Aplicado en 5.
6. **Los dos humanoides abren galerías propias**, no solo el duende. Pero no rompen bloques construidos por jugadores,
   ni entran en casas, ni abren cofres que no sean el suyo. Aplicado en 6.6 regla 8, 7 y 11.
7. **Las menas se reconocen por etiqueta**, no por lista de items escrita a mano, y el barrido lee paletas de sección,
   así que un mod que añada titanio puede aportar candidatos sin recompilar; interpretar sus drops exige 23.3. Ver a
   través de bloques da el **dónde**, no el **cómo llegar**: elige acceso en 26 y desde un frente seguro orienta la
   galería a la veta, nunca perfora directamente desde la superficie donde está.
   Aplicado en 6.1, 6.5 y 6.6 regla 8.
8. **Presupuesto de 30 minutos, fatiga por veta y tope duro de 64/32 con cualquier número de latigazos.**
   Aplicado en 6.8.
9. **El látigo acelera mientras está en camino, acumulativo y con tope ×3**, y el impulso se gasta por distancia, no
   por tiempo (24 bloques por latigazo). Del noveno en adelante solo gasta cuero y moral. Aplicado en 4, 5, 9.3 y 13.
10. **Se adopta con un latigazo y un nombre obligatorio**, visible para todos como la etiqueta de un animal, y el nombre
    no se puede cambiar nunca. El contrato solo trae al esclavo sin dueño. Aplicado en 3, 4, 5, 15 y 16.
11. **La mujer lleva modelo propio**, con figura femenina adulta normal: cintura, caderas más anchas que los hombros y
    pecho con volumen suave, sin nada sexualizado, con la misma ropa y las mismas animaciones que el hombre. Mismas
    estadísticas y mismo hitbox para hombre y mujer; el duende conserva sus diferencias de 3.1. Aplicado en 3, 3.1, 13 y 15.2.
12. **El duende también lleva modelo propio, encorvado.** Espalda curvada 20°, joroba, cabeza adelantada, brazos más
    largos que las piernas y rodillas dobladas, con la geometría ya torcida en vez de girar el cuerpo en cada frame. La
    skin sigue siendo una skin de jugador de 64 × 64, y el UV de la cadera y la joroba sale del torso estándar, así que
    cualquier PNG descargado le vale. Solo el hombre se queda con el modelo de jugador. Aplicado en 3, 3.1, 13 y 15.2.
13. **Las skins son archivos sueltos en una carpeta, sin fichero de configuración.** Todo PNG de la carpeta de cada
    versión entra en el sorteo, incluidos los 64 × 32 antiguos, y `comun/` vale para las tres. Copiar un archivo con otro
    nombre lo pondera. El servidor manda los píxeles al cliente por paquete y nunca se llama a una web. Aplicado en 3,
    3.1, 13, 15 y 16.
14. **La vuelta no paga hambre y, si no estás o estás lejos, espera en casa.** El castigo de velocidad de las dos
    barras solo existe mientras busca y trabaja: volver al dueño o a casa va siempre al 100 % de la velocidad base, con
    el hambre que sea. Si el dueño no está en el mundo, está en otra dimensión o está a más de 128 bloques de casa, el
    esclavo no se queda donde terminó ni cruza el mundo ni se asoma a un portal: se va a casa, que es el punto donde lo
    adoptaste o su cofre de suministros, y espera allí en `ESPERANDO` hasta que vuelvas o te acerques. Aplicado en 5,
    9.2, 9.3, 10.4, 11, 12, 13, 15.1 y 15.3.
15. **La carga se cobra con el látigo.** El esclavo no suelta nada por las buenas: vuelve, se planta a dos bloques con
    lo que trae y espera. Un latigazo sobre él y todo pasa a tu inventario, la referencia incluida. Un solo gesto para
    adoptar, pedir, acelerar y cobrar, sin menú, sin tecla y sin comando. Si tiene cofre de suministros asignado, la
    mete ahí sin que se lo pidas. Aplicado en 4, 5, 8, 10.4 y 13.
16. **El esclavo no se rebela nunca.** Es débil, vulnerable y servicial: obedece siempre, no protesta, no negocia, no
    miente y no sabotea. El hambre y la moral solo lo frenan. Se elimina la mentira del 30 % con la moral baja, y todas
    sus líneas de chat son de disculpa o de agradecimiento, nunca de desprecio ni de queja. Aplicado en la cabecera, 3,
    3.1, 5, 7, 8, 9, 9.1, 9.2, 9.3, 10, 10.1, 10.2, 10.4, 11 y 12.
17. **El esclavo es independiente del jugador y gratis cuando descansa.** Mientras trabaja mantiene una concesión
    con vecindario operativo 3 × 3 y niveles mínimos verificados por actividad: entidad, horno, ruta y presas siguen
    funcionando sin jugadores. El crecimiento se comprueba con las reglas reales del motor. Parado, en casa o esperando no mantiene ninguno: coste cero, como un cultivo en un trozo descargado. Tope de
    4 órdenes activas por servidor, con carga compartida y medida (24). Aplicado en 10.4, 11, 13, 14 y 15.4.

18. **Los techos de los latigazos ahora crecen con los latigazos**: 1 = 10, 2 = un stack, 3 = sin techo (no apilables:
    1 / 4 / 8). Antes estaban al revés. Aplicado en 4, 5, 6.8, 13 y 17.1 (decisiones 2 y 8).
19. **Durabilidad del látigo: 64, cada latigazo gasta 1, cobrar es gratis.** Zurrarle en camino gasta igual, hasta el tope
    de 9.3. Reparable con cuero. Aplicado en 4 y 13.
20. **La referencia es una sola unidad**, no el montón entero. Solo cuenta el tipo de item; los encantados o con
    componentes valiosos se rechazan. Aplicado en 5.
21. **La orden se confirma con un silencio de 1,5 s** tras el último latigazo, con una ventana de 5 s para el primero.
    Aplicado en 5.1 y 13.
22. **Hay una tabla de gestos única** (5.2) y un gesto nuevo, "repetir la última orden" (agachado + látigo, 5.3).
    También una pantalla de equipo (agachado + mano vacía, 15.14) en la que la bolsa es solo de lectura.
23. **Muerte = orden cancelada, vínculo intacto, y tumba.** El legado vive en un `SavedData` por `esclavoId`, con índice por dueño. **No reaparece
    solo**: se levanta una tumba y hay que hacer un ritual (cuatro antorchas de alma en cruz, de noche; contrato y
    manzana dorada entregados a La Muerte) para traerlo con vida al 50 % y hambre 6. Sustituye a la reaparición al
    amanecer y al freno de salud máxima. Aplicado en 10, 10.5, 10.6 y 15.11.
    **Se queda la antorcha de alma** (decisión del usuario): obliga al dueño a ir al Nether aunque el esclavo no vaya;
    es parte del coste de la inmortalidad.
24. **"Natural" se decide por lista blanca + zona de base + árbol con hojas generadas**, no por un registro exacto de
    quién puso cada bloque (Minecraft no lo guarda). El registro exacto queda para v1.1. Aplicado en 7, 11 y 15.7.
25. **El inventario solo tiene `insertar(item, Fuente)`**, con cinco fuentes y operaciones de extracción verificadas.
    Las pruebas comprueban conservación y procedencia; el enum por sí solo no las garantiza. Aplicado en 2 y 15.8.
26. **Los drops de bloques van directos al inventario**, sin `ItemEntity`; los de mobs se identifican por su muerte
    de origen, además de limitar recogida a 4 bloques y 5 segundos. Aplicado en 15.8.
27. **Skins: se normalizan en el servidor** (64 × 32 → 64 × 64, slim → ancho en duende y mujer), se hashea el contenido
    normalizado y viaja el RGBA crudo de 16 KB. Los modelos propios necesitan su capa exterior. Aplicado en 3.2, 15.2
    y 15.16.
28. **Transferir = soltar sin dueño y conservar nombre**; caducar = 7 días reales sin conectar el dueño. Aplicado en 12.1.
29. **Un esclavo por jugador (hasta 3 en config) y 4 trabajando a la vez en todo el servidor**, con cola FIFO. El reloj
    no empieza hasta que le toca el ticket. Aplicado en 12, 13 y 15.12.
35. **El ritual es una escena interactiva y oscura, sin chat, sin iconos y sin emojis.** "Suplicar" (mano vacía sobre
    la tumba) abre una pantalla con forma de lápida que lista los requisitos en texto grabado; con todo cumplido su
    botón lanza el conjuro y aparece La Muerte, cuyo altar es otra pantalla con dos huecos para las ofrendas. Los
    textos de la escena salen en una capa de cine (`RitualOverlay`). Esto exige código de cliente (pantallas, capa y
    renderer de La Muerte). Aplicado en 10.6, 13, 15.1 y 16.

### 17.2 Decisiones provisionales y validaciones pendientes

30. **XP:** v1 entrega 100 % por defecto, configurable; al morir se cancela (23.4). Ajustar equilibrio con pruebas.
31. **Capa exterior:** incluida en v1 para los tres modelos; comprobación visual de M6.
32. **Registro de colocaciones:** v1.1; no promete exactitud para mundos anteriores, pistones u otros mods (15.7).
33. **Varias órdenes por esclavo:** fuera de v1. La FIFO global de trabajadores no equivale a una lista de encargos.
34. **Estimación:** incluida en v1 como intervalo orientativo o «duración incierta» si faltan datos (23.1).
36. **Carga optimizada:** autonomía completa; reducir niveles solo tras equivalencia funcional (24).
37. **Mapas:** nativo e integraciones opcionales como requisito v1; fijar versiones/API en M0 y probar (25).
38. **Acceso minero:** cuevas con antorchas prioritarias dentro de margen; ajustar 25 %/60 s con escenarios de 26.

## 18. Configuración del servidor

Un solo fichero, `config/theslave/config.json`, junto a la carpeta de skins de 3.2. Se crea con los valores por defecto
la primera vez y se relee con `/theslave recargar` (operadores). Solo lo que de verdad puede tocar un administrador;
la tirada de 1/3 de variante **no está** aquí a propósito (decisión 4).

| Clave | Por defecto | Qué hace |
|---|---|---|
| `maxPorJugador` | 1 | Esclavos por jugador (1-3) |
| `maxTrabajando` | 4 | Órdenes activas globales, también cuando reutilizan chunks de jugadores |
| `presupuestoMinutos` | 30 | Reloj de cada orden (6.8) |
| `caducidadDias` | 7 | Días reales sin conectar el dueño antes de quedar sin dueño (12.1) |
| `radioMenas` | 32 | Radio del barrido (6.1); el segundo intento usa 1,5× |
| `respetarProtecciones` | true, obligatorio en v1 | Comprueba permisos de cada operación; un veto o permiso indeterminado bloquea la acción (15.13) |
| `xpAlDueno` | true | Entrega la XP acumulada al cobrar (15.8) |
| `xpFactor` | 1.0 | Fracción de esa XP |
| `chat.maxInfoPorMinuto` | 6 | Tope de mensajes `INFO` por esclavo (15.10) |
| `chat.progresoCadaMinutos` | 5 | Cadencia del mensaje de progreso |
| `skins.carpeta` | `config/theslave/skins` | Carpeta de skins (3.2) |
| `debug` | false | Diagnósticos propios sin ser operador; nunca acceso a entidades ajenas |
| `ritual.oscuridad` | true | Oscuridad de la escena; efectos visuales de cliente se ajustan por jugador |
| `ritual.maxSimultaneos` | 4 | Escenas simultáneas sin cargar chunks adicionales |
| `mapas.actualizacionTicks` | 20 | Cadencia mínima de marcadores remotos por cambio; no altera tracking cercano |
| `saludo.enfriamientoSegundos` | 60 | Intervalo de saludo al dueño; entrada/rearme de 25.3 |

**Recarga atómica:** valida tipos, rangos y claves antes de aplicar. Un JSON inválido conserva la configuración
anterior e informa archivo/campo. Concurrencia afecta nuevas admisiones, sin congelar órdenes en curso; no cambia
límites ya confirmados a mitad de encargo. Las zonas excluidas se guardan como datos de mundo.

Los tags de datapack de 15.13 complementan este fichero: lo numérico va en el JSON, lo que es lista de bloques o items va
en `data/theslave/tags/`.

## 19. Hoja de ruta de implementación

Orden pensado para tener algo jugable en cada hito y dejar lo vistoso para cuando lo caro ya funciona. Cada hito termina
con una prueba concreta que se puede ver en el juego.

| Hito | Contenido | Se considera terminado cuando... |
|---|---|---|
| **M0 Esqueleto** | Proyecto Gradle (copiado de TheBall), registro de entidad, items y sonidos; `EsclavoRenderer` con el modelo de jugador y la skin de reserva; contrato → aparece sin dueño | `/give` del contrato, clic derecho en el suelo: aparece un jugador quieto y sin etiqueta |
| **M1 Adopción y gestos** | `LatigoItem`, `NombreScreen` + payload, dueño y nombre, etiqueta pública, tabla de gestos (5.2), `EsclavoMenu`, seguir al dueño | Latigazo → nombre → te sigue; un segundo jugador no puede darle órdenes |
| **M2 Supervivencia** | Hambre, moral, `SpeedModifier`, capas 1-3 de peligro, `Trail`, escalera de desatasco | Se le acerca a una charca de lava y no entra; sin pan va más lento |
| **M3 Cerebro en seco** | `RecipeIndex`, `Planner`, `WorldKnowledge`, `/theslave simular` y `saber` | El plan de 3 lingotes de 6.3 sale impreso sin mover al esclavo (con y sin cofre) |
| **M4 Ejecutor mínimo** | Tareas de caminar, picar, talar, craftear, colocar, fundir, volver; `FakeEsclavo`; `EsclavoInventory.insertar` | Pide un palo (talar + craftear), luego un pico de piedra, luego un lingote de hierro, solo |
| **M5 Casa, espera y chunks** | `HomePoint`, `ESPERANDO`, `ChunkKeeper`, cola FIFO, cobro con látigo, reintento con lección | Te desconectas, sigue trabajando con el ticket, vuelve a casa y espera hasta que entres |
| **M6 Variantes y skins** | `DuendeModel`, `MujerModel`, capa exterior, armadura propia, `SkinLibrary` + red (15.16) | Las tres variantes se ven bien con tres skins descargadas, incluida una slim y una 64 × 32 |
| **M7 Muerte, tumba y multijugador** | `EsclavoRegistro`, `TumbaBlock` y su ritual, caducidad, transferencia | Mata a tu esclavo, aparece la tumba, haz el ritual de noche y vuelve igual con el mismo nombre |
| **M8 Pulido** | Chat con límite de ritmo, i18n, sonidos y partículas, equilibrio, README con crafteos (`tools/generate_recipe_images.py` adaptado), `.jar` compilado en `jar/TheSlave/`, fila en el README raíz | `./gradlew build` en local sin errores y la tabla del README raíz actualizada |

M4 demuestra el núcleo con un lingote obtenido solo. M5 aún debe demostrar autonomía eficiente y M8 los mapas;
conseguir hierro no da por resueltos tickets, recuperación, memoria o compatibilidad. M0 incluye las pruebas de
viabilidad de 23.8 y mapas de 25; M3/M4 el acceso minero de 26; M5 la equivalencia y saludo de 24-25. Conviene
no empezar M6 (lo vistoso) antes de que M4 funcione.

## 20. Plan de pruebas

**Pruebas de lógica pura (JUnit, sin Minecraft):** `SpeedModifierTest` (9.4), `WhipBoostTest` (acumulado y tope),
`HungerTest` (gasto por actividad), `OreBudgetTest` (fatiga +50 %, topes), `TechosTest` (1/2/3 latigazos con apilables y
no apilables), `SkinNormalizerTest` (64 × 32, slim, límite de 32 KB, cabecera falsa).

**GameTests (Fabric Gametest API, mundo real de prueba):**

| Prueba | Comprueba |
|---|---|
| `planner_hierro_desde_cero` | Plan de 6.3: tronco → mesa → pico de piedra → mena → horno → combustible |
| `planner_rechaza_encantamiento` | Pedir una poción o un libro encantado devuelve "no sé hacerlo" (6.7) |
| `planner_usa_cofre` | Con lingotes en el cofre asignado, el plan sale vacío y los coge de ahí |
| `lava_sin_ruta` | Un pasillo cortado por lava no tiene camino y el esclavo vuelve avisando |
| `caida_cuatro_bloques` | Un nodo con 4 bloques de caída sin agua es inválido |
| `no_pica_hacia_abajo` | Ante una mena justo debajo, baja en escalera, nunca en vertical |
| `no_rompe_zona_base` | Con un horno puesto por un jugador al lado, no rompe la piedra de alrededor (15.7) |
| `invariante_fuente` | Operaciones reales conservan entradas, salidas y custodia sin doble entrega (23.4) |
| `horno_ajeno_vacio` | Usa un horno vacío; si tiene algo dentro, coloca el suyo y no toca lo ajeno |
| `techo_latigazos` | 1 latigazo = 10, 2 = stack, 3 = sin techo, y para en el tope duro |
| `entrega_con_latigo` | La carga no se suelta sola y un latigazo la pasa al inventario; si no cabe, cae a los pies |
| `tumba_al_morir` | Al morir aparece la tumba (o en posición segura si cae en lava) y el legado queda en `MUERTO` |
| `ritual_completo` | Con círculo, ofrendas y de noche, tras conjuro, ofrendas y sellado (23.6) vuelve con misma identidad y memoria |
| `ritual_interrumpido` | Antorcha rota, amanecer, dueño lejos o mob en el círculo: se cancela y se devuelven las ofrendas entregadas |
| `suplicar_lista` | Sin materiales o de día, la lápida informa, el botón queda apagado y no se gasta nada |
| `muerte_ofrendas` | La Muerte acepta contrato y manzana dorada en cualquier orden y rechaza otro item |
| `tumba_protegida` | Un tercero, creeper o pistón no pueden romperla; el dueño solo con pico y confirmación |
| `ticket_se_suelta` | Cancelar/agotar reloj conserva carga durante vuelta; en destino o muerte libera solo concesiones propias |
| `receta_lotes_y_restantes` | Lotes, recipientes y falta de espacio no pierden ni duplican objetos |
| `dos_duenos_y_mismo_loot` | Herramienta y drops simultáneos permanecen separados |
| `reinicio_orden_descargada` | Recupera entidad sin esperar a un jugador y sin duplicarla |
| `saludo_solo_dueno` | Saluda al dueño con cooldown; a terceros no; minado conserva progreso |
| `mina_con_antorchas` | Reutiliza acceso explorado dentro del margen de coste (26) |
| `mina_rodeo_excesivo` | Prefiere bocamina segura a una mina iluminada demasiado lejana |
| `antorcha_no_es_permiso` | Antorcha de fachada o tras pared no autoriza atravesar una base |
| `veta_superficial` | No baja a una mina para una mena expuesta accesible |
| `mina_agotada` | Invalida veta extraída por otro jugador y conserva acceso útil |
| `recarga_invalida` | Config inválida no aplica valores parciales ni borra inventario |

**Lista manual antes de publicar** (no se puede automatizar bien): mirar a los tres modelos de cerca con armadura de
hierro puesta, la animación de caminar y la de negar; el nametag a través de paredes; una skin descargada de verdad con
chaqueta y mangas; un servidor con dos jugadores y un tercero que intenta mandarlo; el `/theslave perfil` con cuatro
esclavos trabajando a la vez; equivalencia/memoria de 24; mapas nativo/JourneyMap/Xaero y presencia continua de 25;
estrategia minera de 26. Las integraciones gráficas requieren cliente, no se verifican solo con GameTests del servidor.

## 21. Riesgos técnicos y planes B

| # | Riesgo | Cómo se detecta | Plan B |
|---|---|---|---|
| R1 | Nivel de tickets o halo de 26.3 distinto del supuesto | Spike de M0 y medición de chunks reales | Mantener la base funcional 3 × 3 mientras se corrige el gestor; no usar forceload persistente |
| R2 | `PlayerModel` no admite un render state propio (15.2) | Al empezar M0 | Copiar la geometría del jugador a un `TheSlaveModel` propio |
| R3 | Registrar un juego propio de modelos de armadura es costoso (15.2) | Al empezar M6 | Usar la armadura vanilla en los tres (aviso en el README) |
| R4 | La heurística de "natural" (15.7) deja pasar bases o se pasa de protectora | Pruebas de M4 y M7 | Subir el radio de zona de base; adelantar el registro exacto de v1.1 |
| R5 | `getDrops` con `FakePlayer` cambió de firma (15.8) | M4 | Llamar a `Block.getDrops` con `LootParams` construidos a mano |
| R6 | El horno se para cuando el esclavo se aleja (15.9) | Prueba `horno_ajeno_vacio` ampliada | Cocinar siempre en el sitio y esperar, sin paralelizar |
| R7 | El barrido de menas se come el TPS (15.12) | `/theslave perfil` | Menor radio por defecto; barrer una sección por tick |
| R8 | La skin tarda en llegar y el esclavo sale con la de reserva (15.16) | Con latencia simulada | Mantener la reserva y cambiar al llegar la textura |
| R9 | Un mod de claims no admite la interacción automatizada (15.13) | Pruebas con una versión compatible del mod de claims | Adaptador explícito; hasta entonces rechazar esa operación en la zona protegida |
| R10 | No encuentra mena porque el barrido solo ve chunks ya cargados (15.12) | Orden sin mena visible | Explorar por tramos dentro del radio, conservando la ventana de carga |
| R11 | El efecto Oscuridad o el modelo de La Muerte resultan molestos o no renderizan con shaders (10.6) | Pruebas con y sin shaders | Config `ritual.oscuridad=false` y modelo simple sin capa luminosa |
| R12 | Las antorchas de alma obligan al Nether y frustran al que no puede ir (10.6) | Feedback de jugadores | Tag de datapack `theslave:antorchas_ritual` para admitir también antorchas normales |

## 22. Cambios de esta revisión

Resumen de lo que se ha tocado, para poder revisarlo sin releer todo:

- **Incoherencias corregidas**: los techos de latigazo estaban al revés (decisión 18); el "octavo/noveno" latigazo del
  tope ×3 no coincidía entre 9.3 y la decisión 9; "contrato roto" al morir chocaba con "reaparece al día siguiente";
  "un esclavo por jugador, hasta 4 por mundo" mezclaba dos límites distintos; la transferencia estaba definida en 3 y
  fuera de v1 en 14 a la vez; faltaba definir qué hace una mano vacía y el látigo sin referencia.
- **Huecos funcionales rellenados**: confirmación de la orden (5.1), tabla única de gestos (5.2), repetir la última
  orden (5.3), pantalla de equipo (15.14), mensaje de progreso (15.10), durabilidad del látigo (4), transferencia y
  caducidad (12.1), muerte y reaparición (10.5), cola y presupuesto de CPU (15.12), configuración (18).
- **Huecos técnicos rellenados**: fórmula de velocidad (9.4), cómo saber qué es natural (15.7), invariante hecho
  verificable con el tipo `Fuente` (15.8), tiempo y combustible de hornos (15.9), persistencia versionada (15.11),
  normalización y red de skins con capa exterior y brazos slim (15.16), compatibilidad con mods de protección y
  datapacks (15.13), casos límite (15.15).
- **Tumba y ritual (segunda revisión)**: la reaparición automática al amanecer se sustituye por una tumba y un ritual
  nocturno (10.6), con sus gestos, números, `BlockEntity`, casos límite, comandos de operador y pruebas.
- **Ritual interactivo (tercera revisión)**: el ritual pasa a ser una escena oscura: pantalla de lápida con los
  requisitos, conjuro, La Muerte (entidad `theslave:la_muerte`) con su altar de ofrendas, capa de cine para los
  textos, guion plano a plano con Oscuridad, niebla, campanas y rayo visual, sonidos propios, accesibilidad y riesgos
  R11-R12. Se mantienen las antorchas de alma. Sin chat, iconos ni emojis.
- **Proceso**: hoja de ruta por hitos (19), plan de pruebas (20) y riesgos con plan B (21).

## 23. Especificación de experiencia y ejecución fiable

Esta revisión conserva el contrato, el látigo, las tres variantes, el trabajo físico y el ritual. Concreta cómo se
juegan y se recuperan los casos incompletos. Las cifras nuevas son valores iniciales de diseño que se ajustarán con
las pruebas; no son resultados medidos ni funcionalidades implementadas.

### 23.1 Primeros cinco minutos y feedback de cada gesto

El jugador debe entender qué ha pasado sin leer todo este documento. No se añade un menú de encargos: la ayuda se
integra en tooltips, action bar y la pantalla de equipo existente.

| Momento | Qué ve el dueño | Comportamiento verificable |
|---|---|---|
| Contrato en mano | Tooltip: «Invoca sin dueño en el Overworld. Adopta con látigo» | Si no hay suelo, espacio o permiso, informa y no gasta el contrato |
| Primera adopción | Nombre obligatorio y aviso «Este nombre será permanente» | Cancelar no vincula; confirmar comprueba límite y disponibilidad otra vez |
| Primera referencia | «Hierro · 1 unidad en custodia · cambia al látigo · 5 s» | El stack del jugador pierde una sola unidad; no desaparece por cerrar una pantalla |
| Cada latigazo de confirmación | «2/3 · hasta 32 diamantes · confirma en 1,5 s» | Refleja cantidad efectiva, no promete 64 si existe un tope de 32 |
| Orden confirmada | «Buscaré hierro. Necesito mesa, pico y horno» | Una sola confirmación; los clics y paquetes repetidos no crean otra orden |
| Plan terminado | «Primero preparo herramientas; después mino. Aproximadamente 8-12 min» | Estimación orientativa por intervalo; si faltan datos, dice «duración incierta» |
| Sin recursos observados | «Aún no he visto hierro. Buscaré dentro de 32 bloques» | Distingue falta de observación de imposibilidad de obtener el recurso |
| En cola | «Esperando turno · posición 2 · el tiempo todavía no cuenta» | Si cambia la posición, actualiza al consultar; no envía chat por cada cambio |
| Vuelta con resultado | «24 lingotes nuevos · referencia 1 · listos para cobrar» | Distingue producción, materiales del cofre y referencia |
| Cofre lleno | «He guardado 16. Me quedan 8 y tu referencia» | Inserta lo que cabe y conserva el resto, sin tirarlo ni reintentarlo cada tick |

**Ayuda contextual.** Los tooltips del látigo describen confirmar, acelerar y cobrar. La pantalla de equipo incluye un
texto breve: «Comida agachado; objeto normal para pedir; látigo al aire para cancelar». La primera vez que el jugador
usa un gesto mal, recibe una explicación de una línea; se agrupa con el límite de mensajes de 15.10.

**Durante el trabajo.** Consultar con mano vacía muestra fase, cantidad obtenida/cantidad efectiva, tiempo restante y
motivo de espera. La bolsa de solo lectura identifica herramientas retenidas, suministros, carga entregable y
referencia mediante texto y tooltip. No usa solo color. «Quieto/Sígueme» solo cambia el modo de reposo; durante una
orden se desactiva con la explicación «Cancela primero». Editar equipo exige reposo y ausencia de una operación activa.

**Informe útil, sin ruido.** Al cobrar: cantidad nueva, referencia devuelta, suministros usados y causa de parada.
Ejemplo: «Traigo 8 lingotes y tu muestra. He usado 1 carbón del cofre. No había más hierro accesible. Perdona».
Los límites, cancelaciones e inventarios llenos no se presentan como éxito completo. La estimación se actualiza solo
al cambiar de fase o replanificar; nunca promete un tiempo exacto para encontrar recursos.

### 23.2 Estados, relojes y cancelación

Una orden tiene `orderId`, objetivo, techo solicitado, límite efectivo, origen fijo, intento y resultado. Una entidad
tiene `esclavoId` estable y una sola orden actual. La vida (`VIVO`, `MUERTO`, `SIN_DUENO`) se guarda aparte de la fase.

| Fase | Entrada y salida | Concesión de chunks | Reloj de trabajo |
|---|---|---|---|
| `OCIOSO` | Sin entrega pendiente; admite referencia | No | Parado |
| `RECIBIENDO` | Custodia de referencia; 100 ticks para primer golpe y 30 de silencio | No | Parado |
| `PLANIFICANDO` | Plan provisional con lo ya observado; no modifica el mundo | No | Parado |
| `EN_COLA` | Plan provisional válido; FIFO global con máximo una orden admitida o en cola por dueño | No | Parado |
| `PREPARANDO` | Adquiere concesión, espera chunks, verifica recursos y completa el plan | Sí | Corre |
| `TRABAJANDO` | Ejecuta tareas y registra cada resultado real | Sí | Corre |
| `VOLVIENDO` | Éxito, parcial, cancelación o presupuesto agotado | Sí, hasta destino seguro | Terminado |
| `INFORMANDO` | Quieto ante el dueño con entrega pendiente | No | Parado |
| `ESPERANDO` | En casa; puede tener carga pendiente | No | Parado |

- **Relojes explícitos.** Los gestos, animaciones, hambre y ritual usan ticks del servidor. Los 30 minutos son tiempo
  real **con el servidor ejecutándose y la orden admitida**, incluyendo preparación, fatiga y esperas de hornos.
  Se acumulan con un reloj monotónico y se persiste duración consumida; apagar el servidor o esperar en cola no consume
  presupuesto. No se persiste un instante monotónico entre procesos. La caducidad de propiedad usa fecha UTC real.
- **Planificación acotada.** Máximo 5 s de servidor activo para generar el plan provisional, además del presupuesto
  de CPU por tick. Si lo supera, informa `PLAN_DEMASIADO_COMPLEJO` y deja referencia lista para cobrar.
- **Cola sin entidad cargada.** La solicitud vive en el coordinador global. Si al llegar su turno su chunk no está
  cargado, la concesión permite recuperarla; no depende del tick de esa entidad para despertar. Si el dueño se
  desconectó **antes de la admisión**, se cancela la solicitud y conserva la referencia hasta su regreso. Una orden
  ya admitida continúa sin él, como establece 10.4.
- **Cancelar sin ambigüedad.** Antes de salir de `RECIBIENDO` se devuelve la referencia; en cola se retira la entrada;
  admitida, se marca cancelación y se inicia vuelta tras cerrar la operación atómica actual. No se deshacen bloques
  ya rotos ni recetas terminadas, pero se recuperan ingredientes propios aún en estaciones cuando sea seguro.
- **Retorno acotado.** La vuelta conserva su slot para no congelarse. Máximo inicial: 5 min de servidor activo;
  agotado, usa el rescate a una posición segura de casa. No va al jugador si cambió de dimensión. El rescate termina
  esa orden y no sirve para proseguir la extracción; queda registrado para detectar bucles y abuso.
- **Vuelta segura.** Valida suelo, volumen del hitbox, fluidos, borde del mundo y permisos en el destino. Prueba anclas
  conocidas y una búsqueda local acotada de 8 bloques. Si ninguna es segura, queda en `RECUPERACION_PENDIENTE` en el
  registro, libera el slot y avisa al dueño/operador; conserva datos y no reaparece dentro de un bloque.

**Prioridad de eventos:** muerte → rescate de peligro → cancelación/presupuesto → tarea → seguimiento. Ninguna tarea
avanza mientras otra posee movimiento o inventario. El retorno no queda detenido por una pausa de fatiga pendiente.
`Task` devuelve `RUNNING`, `DONE`, `RETRYABLE` o `FAILED` con código de causa; dispone de precondición, progreso y
limpieza al cancelar. Un atasco mide falta de progreso de su tarea, no estar quieto cocinando.

**Orden de resolución de gestos:** tumba/ritual → entidad alcanzada → cofre alcanzado → aire. Se procesa una sola mano
por interacción y el servidor decide el resultado. Con varios esclavos se mantiene **una sola orden admitida o en cola por dueño** en v1: cancelar al aire
apunta siempre a esa orden. Los otros conservan reposo y rechazan otra referencia hasta cerrar o cancelar la anterior. Asignar cofre usa el esclavo propio más cercano a ≤ 8
bloques; si hay empate o ninguno, explica la causa y no cambia nada. No cancela una orden al interactuar con un bloque.

### 23.3 Planificador por cantidades y capacidades comprobadas

El índice de recetas responde «qué transformaciones conozco»; no demuestra «puedo conseguir los ingredientes».
Una etiqueta de mena identifica candidatos de bloques, pero no revela por sí sola qué item producirán sus loot tables,
con qué herramienta o si una máquina de otro mod puede procesarlos.

1. **Objetivo exacto.** `itemId` y cantidad pendiente, con los componentes admitidos de la referencia. Un filete no
   se sustituye por chuleta. La muestra permanece fuera de todos los inventarios consultados por el planificador.
2. **Capacidades explícitas.** Adaptadores v1 para crafting 2×2/3×3, fundición, alto horno y cortapiedras. Las recetas
   dinámicas/especiales solo entran si el adaptador puede calcular ingredientes, salida y restantes sin inventarlos.
   Ahumador, caña de pescar, pociones y máquinas de mods quedan fuera hasta disponer de adaptador y prueba propios.
3. **Fuentes observadas.** Candidatos naturales por etiqueta y reglas verificadas; entidades cazables y cultivos
   maduros no protegidos. Una mena añadida por un mod solo es resoluble si sus drops y requisitos son interpretables
   o tiene una regla de datapack explícita. No se evalúa loot aleatorio hasta ejecutar la acción real.
4. **Cantidades reales.** Para una receta de `s` salidas, lotes = `ceil(pendiente / s)`. Reserva ingredientes por lote,
   conserva sobrantes, recipientes y herramientas retornadas, y simula espacio final antes de consumir. Elimina ciclos
   como lingote → bloque → lingote del camino actual; el inventario reservado no se vuelve a contar para otro paso.
5. **Infraestructura separada.** Mesa, pico de madera, pico de piedra, horno y combustible son dependencias compartidas,
   no una nueva copia por cada lingote. Máximo 6 niveles incluyendo infraestructura, 64 tareas por plan y 512
   expansiones de candidatos por planificación. Esos límites producen un fallo explicable, no un bucle de búsqueda.
6. **Coste comparable.** Puntúa en segundos estimados: caminar + romper + fabricar + cocinar + fatiga, con penalización
   de riesgo solo entre rutas permitidas. Un riesgo prohibido no se vuelve aceptable por ser barato. Empates por id
   de receta y distancia para que `simular` sea reproducible con la misma instantánea.
7. **Cantidad efectiva.** Min(techo, tope de recurso, capacidad reservada); «sin techo» omite solo el primer término.
   El tope de hierro también cubre su conversión a bloques: cuenta equivalentes de materia prima por orden para
   evitar pedir bloques y multiplicar por nueve la extracción permitida. Si no cabe un lote completo, reduce el lote
   o informa antes de gastar recursos. Los drops aleatorios excedentes siguen siendo reales: se guardan como
   subproducto si caben y se informan; no se destruyen para fingir exactitud. Al alcanzar el límite no rompe otro bloque.
8. **Revalidación.** Antes de cada paso contrasta bloque, estación, permisos, herramienta, reservas y generación del
   recetario. Máximo 2 replanificaciones locales por orden; después vuelve con carga parcial y causa concreta. `/reload`
   invalida índices y planes todavía no ejecutados, conserva materiales reales y vuelve a consultar recetas.

**Progresión legible.** Retiene herramientas que fabricó y recuerda estaciones válidas; esas herramientas no forman
parte de la carga de cobro. La pantalla de equipo explica «Conservo pico y horno para el siguiente encargo». Si lo
pedido es precisamente una herramienta, produce otra unidad entregable; no entrega sin aviso el equipo prestado.
El informe separa unidades retiradas del cofre, fabricadas con suministros y extraídas del mundo.

### 23.4 Inventario, entrega y estaciones sin pérdidas silenciosas

**Distribución:** referencia (1), mano (1), armadura (4), bolsa (12). En la bolsa se reservan **al menos 4 huecos** para
infraestructura y supervivencia; la carga puede usar hasta 8, y menos si necesita más equipo. Si la preparación no
cabe, se reduce la tarea o falla antes de partir. Cada stack conserva componentes y un rol; stacks incompatibles en
rol no se mezclan aunque tengan el mismo item. No se introduce una segunda mochila invisible.

**Operaciones en el hilo del servidor:** validar → simular entradas/salidas → aplicar → registrar resultado.
El identificador de operación y la revisión de orden evitan repetir efectos por doble clic, paquete repetido o
reintento del ejecutor durante la misma sesión. Esto no convierte el guardado vanilla de varios chunks en una base
de datos transaccional; la recuperación frente a apagados abruptos se limita explícitamente en 23.6.

| Operación | Condición antes de aplicar | Qué se conserva si falla |
|---|---|---|
| Referencia | Dueño, reposo, item admitido y ranura libre | El stack sigue en la mano si no se acepta |
| Extracción del cofre | Asignación vigente, permiso y cantidad disponible | Relee el inventario real; extrae solo lo que realmente entra |
| Crafteo | Coincidencia de receta, ingredientes y sitio para resultado/restantes | No consume ingredientes si no cabe la salida |
| Rotura | Bloque actual, permisos, herramienta y espacio para loot | Sin permiso no rompe, no desgasta y no concede XP |
| Entrega al dueño | Cerca, mismo mundo, orden pendiente y receptor validado | Transfiere una vez; el resto cae a sus pies según 5, descontándolo del inventario |
| Entrega al cofre | Cofre válido y accesible | Inserción parcial; conserva el resto y avisa una vez |
| Equipo prestado | Reposo, slot compatible y destino para equipo sustituido | No borra la pieza anterior; si no hay sitio, rechaza el cambio |

**Un único camino de loot.** La rotura autorizada produce una sola tirada, una sola XP y un solo desgaste, respetando
Fortuna y Toque de Seda. Se comprueba si el método elegido ya dispara callbacks, XP o drops antes de añadirlos a mano.
Con `destroyProgress <= 0`, dureza inválida o herramienta inadecuada el plan no calcula una división ni rompe el bloque.
La XP pendiente no se entrega dos veces ni reaparece tras morir: al morir se cancela y se aplica una sola política de
drop documentada; para v1 se descarta, como coste adicional de perder el encargo.

**Hornos compartidos.** Una estación vacía se puede ocupar, pero una comprobación inicial no garantiza exclusividad:
otro jugador o una tolva puede cambiarla después. Se guarda posición, identidad de estación, lotes propios y contenido
esperado. Antes de retirar, se contrasta todo. Si hubo interferencia, se detiene la tarea y se recupera solo lo
atribuible; nunca se toma otro stack «porque es el mismo hierro». Si esa atribución no es demostrable, informa y deja
el contenido para revisión del dueño. No se recoge un horno propio que aún contenga objetos ajenos.

El horno permanece en un chunk con block-entity ticking durante cada hornada; no se deduce de «menos de 24 bloques».
Se verifica pertenencia a la ventana efectiva y se espera o se recoge antes de alejarse. Combustible sobrante, recipientes
y XP se contabilizan. Se usan tiempos de la receta y combustible del servidor; la tabla de 15.9 es solo una referencia.

### 23.5 Permisos, entorno compartido y propiedad

**Protección por defecto.** Las heurísticas de 15.7 no distinguen una pared natural de otra puesta antes de instalar
el mod. La v1 ofrece una defensa conservadora, no una garantía absoluta de origen. Comprueba lista blanca, exclusiones,
zona de base y permiso de operación antes de mutar el mundo. Los administradores pueden declarar zonas excluidas con
`/theslave zona excluir <id> <pos1> <pos2>` y eliminarlas con `zona quitar <id>`; se guardan por dimensión. Una
exclusión siempre prevalece. En estructuras o bloques ambiguos evita trabajar y explica la limitación.

- Una estación colocada por él no crea una zona de base que bloquee su propia galería; su propiedad solo exime de esa
  heurística, nunca de un claim o exclusión. Si otro jugador la modifica o la repone, invalida la propiedad recordada.
- No caza animales domesticados, con nombre, atados, con dueño, ni los que estén en claims ajenos o zonas de base.
  Antes de atacar revalida objetivo y permiso; no persigue fuera del radio de la orden. Ante duda, no caza.
- Las hojas `persistent=false` también pueden proceder de árboles cultivados por jugadores: son una pista de árbol,
  no una prueba de propiedad. El bosque dentro de una zona protegida sigue siendo intocable.
- Asignar un cofre exige reposo, permiso de uso y comprobar su inventario completo si es doble. Se guarda su
  posición/dimensión, se accede solo a ese inventario y se revalida cada uso; no se siguen tolvas ni redes de almacenamiento.
- Liberación y caducidad revocan acceso al cofre inmediatamente. En caducidad la carga, referencia y equipo prestado
  quedan bajo el dueño anterior en un depósito de recuperación persistente, reclamable por él con
  `/theslave recuperar <esclavoId>`: no se transfieren materiales de otro jugador. Ese depósito tiene un máximo de
  los slots físicos del esclavo; no acepta nuevas inserciones como almacén. El nuevo hogar se fija al adoptar.
- Vivos y muertos cuentan para `maxPorJugador`. Adopción y herencia del ritual verifican límite y propietario al inicio
  y al confirmar; dos jugadores no pueden reclamar el mismo vínculo. Los comandos que puedan tener varios objetivos
  aceptan `esclavoId`; si se omite y hay ambigüedad, no actúan.
- Caducidad se evalúa desde el registro al conectar/cargar y periódicamente en lotes, sin cargar todas las entidades.
  Una marca UTC de última conexión futura por cambio del reloj no libera anticipadamente al esclavo.

### 23.6 Guardado, recuperación y custodia del ritual

**Datos mínimos:** `DataVersion`, `esclavoId`, dueño, `entityUUID`, revisión, estado de vida, última posición segura,
hogar, skin, modo de reposo y resumen de orden (`orderId`, objetivo, límites, fases, duración consumida, resultado y
entrega pendiente). El registro guarda las referencias necesarias para recuperar órdenes, no una segunda copia de
inventario vivo lista para entregar. El inventario vivo pertenece a la entidad; las ofrendas a la tumba; el depósito
de caducidad al registro cuando se completa esa transición.

**Al arrancar:** reconstruye índices y cola → localiza entidades con concesiones acotadas → reconcilia revisiones e
identidad → revalida tareas → reanuda o vuelve. Una entidad no cargada no se declara muerta. Duplicados o datos
incompatibles pasan a recuperación y quedan en logs con `esclavoId/orderId`, sin generar recompensas. Los archivos
de versión de datos futura se preservan y se bloquea su uso; no se sobreescriben con valores por defecto.

**Límite honesto:** se cubren cierre ordenado, reinicio y paquetes duplicados. Un corte en mitad de guardar entidad,
registro y chunk puede dejar revisiones diferentes; no se promete atomicidad entre archivos de Minecraft. Antes de
publicar se fuerza un corte en esas fronteras y se valida una política conservadora de recuperación; si persiste una
ambigüedad de custodia, requiere reparación del operador, no recreación automática de objetos. Un backup se restaura
como conjunto de mundo y datos del mod, nunca mezclando una tumba nueva con un registro antiguo.

**Ritual concretado:**

| Aspecto | Regla |
|---|---|
| Cruz | Antorchas de alma de pie en `(x±2,y,z)` y `(x,y,z±2)` respecto a la tumba, sobre soporte válido |
| Espacio | Volumen libre sobre la tumba suficiente para el hitbox de la variante; no sustituye una construcción |
| Noche | `floorMod(dayTime, 24000)` entre 13000 y 23000; se comprueba al empezar y durante la escena |
| Seguridad | Sin hostiles a 16 bloques al iniciar; durante la escena interrumpe un hostil a ≤ 3, antorcha ausente o dueño a > 8 |
| Duración | Conjuro de 480 ticks; hasta 1200 ticks para ofrendas; sellado de 200 ticks. Total 34-94 s a 20 TPS |
| Espera | Al agotar el plazo de ofrendas, cancela, restaura la escena y conserva custodia para devolución |
| Ofrendas | Acepta exactamente 1 contrato y 1 manzana dorada normal; el exceso queda en el inventario del jugador |
| Custodia | Se retiran al depositar, pero solo se destruyen definitivamente al confirmar resurrección |
| Interrupción | Las devuelve una sola vez; si el dueño falta o no tiene espacio, quedan recuperables en la tumba |
| Resurrección | Verifica vínculo muerto, revisión, tumba y aparición segura; crea una instancia y actualiza el registro |
| Final | Retira tumba y escena tras confirmar el cambio; no coloca tierra/flor si destruiría soporte o contenido ajeno |

El botón de la lápida se calcula en servidor. Abrir el altar, pulsar Sellar, cerrar la pantalla o repetir un paquete no
consume de nuevo ni resucita otra copia. Si se corta el servidor, pasa a interrumpido al cargar y recupera custodia.
El entierro definitivo y la restauración administrativa comprueban ofrendas pendientes antes de retirar una tumba.
La Muerte es una representación de `ritualId`, nunca la propietaria de los objetos; se elimina si no existe el ritual.

**Accesibilidad sin perder la escena.** Ajustes de cliente separados para temblor, destello, viñeta y texto instantáneo;
con efectos reducidos se mantiene HUD, subtítulo completo y texto del tiempo de ofrendas. El dueño puede moverse y
cancelar: no se bloquea físicamente al jugador. Un tercero cercano no recibe cámara ni HUD de cine. Se restaura el
estado visual al cancelar, morir, desconectar o cambiar de dimensión y no se eliminan efectos Oscuridad de otra fuente.
Un lector sin audio debe poder completar el ritual con texto; se comprueban escala de GUI y textos largos.

### 23.7 Red, skins y separación de cliente/servidor

El servidor decide propiedad, materiales, cantidades, daño, reloj y ritual. El cliente envía intención, no resultados.
Cada petición de nombre, menú o ritual verifica remitente real, dimensión, distancia de interacción vanilla, entidad
o tumba activa, fase, revisión y permiso; nunca acepta el UUID de dueño proporcionado por el cliente como autorización.
Se limita tamaño y frecuencia de mensajes y se rechazan ids inexistentes antes de leer el mundo.

- **Nombre:** recortar espacios, entre 1 y 20 puntos de código visibles, sin saltos de línea, controles ni códigos de
  formato. Se renderiza como texto literal. La reserva de adopción dura 30 s y se libera al cerrar, alejarse o salir;
  solo se cobra el uso de adopción al confirmar, coherente con «no se gasta nada» si se cierra la pantalla.
- **Skin permanente:** archivar RGBA normalizado por hash en los datos del mundo, aunque el admin borre el PNG de la
  biblioteca. El registro conserva referencia al archivo de contenido; la tumba usa la misma. Recargar biblioteca
  construye un índice nuevo y lo intercambia completo, sin alterar skins existentes ni leer PNG por entidad/tick.
- **Slim:** un PNG no declara de forma fiable la geometría de sus brazos. La detección por transparencia es heurística;
  si es ambigua, usa ancho y registra el diagnóstico. La normalización incluye caras laterales y capa exterior,
  no solo duplicar una columna frontal. Se comprueba con skins 64×32, anchas y slim conocidas.
- **Límites de biblioteca:** 256 PNG por carpeta y 1024 en total, ordenados por ruta antes de limitar; registra los
  omitidos. Conserva duplicados de nombre como peso de sorteo, pero deduplica almacenamiento por hash. Lectura de
  archivos y decodificación fuera del tick; los resultados validados se publican en el hilo del servidor.
- **Transferencia:** un cliente solicita solo hashes anunciados por entidades que está siguiendo o una tumba cuyo menú
  tiene abierto; máximo 2 respuestas de 16 KB/s por jugador y 4 peticiones pendientes. Se agrupan peticiones del mismo
  hash y hay espera de reintento; si se satura, usa la textura de reserva. No acepta rutas ni PNG subidos por clientes.
- **Caché cliente:** 64 texturas LRU, libera recursos GPU al desalojar o desconectar y agrupa solicitudes en vuelo.
  Comprueba longitud de 16384 bytes y SHA-256 antes de registrar la textura; el id visual no concede acceso a archivos.
- **Servidor dedicado:** modelos, pantallas y caché GPU en `src/client/java`, siguiendo `splitEnvironmentSourceSets`
  de TheBall. El esquema de 15.1 es lógico; ningún registro de servidor importa clases gráficas.

### 23.8 Entrega por hitos y criterios de publicación

**M0 incluye una prueba técnica previa:** resolver versiones reales, arrancar servidor dedicado y comprobar hooks de
loot/protección, ventana de entity ticking, recuperación de chunk y renderer. Registrar firmas verificadas y limitaciones
en notas de implementación. No copiar cachés, `run/` ni archivos de build de TheBall al crear el proyecto.

**Primer corte jugable (M1-M4):** un dueño, un esclavo, un encargo de palos y después hierro, ejecución secuencial y un
horno propio. Primero demuestra conservación, ruta, cancelación y regreso. La interfaz avanzada del ritual, múltiples
hornos y compatibilidad de recetas ampliada se construyen después; siguen siendo parte de la v1 completa indicada
en 19, no requisitos ocultos para conseguir el primer lingote.

| Puerta de aceptación | Evidencia necesaria |
|---|---|
| Comprensión | Un jugador nuevo adopta, pide, consulta y cobra sin comandos; sabe qué hacer tras un fallo |
| Conservación | Referencia intacta; ingredientes, herramientas, restos y XP contabilizados sin doble entrega |
| Continuidad | Una orden admitida termina con el dueño desconectado y se recupera tras reinicio ordenado |
| Mundo compartido | Dos dueños no mezclan contexto, cofre ni loot; un veto de protección no tiene efectos laterales |
| Rendimiento | Cuatro órdenes cumplen presupuestos globales de 15.12; se registran p50/p95 de ms por tick y chunks reales |
| Reposo | Ninguna concesión tras entrega, muerte o espera; entidad descargada no pierde hambre |
| Ritual | Éxito, interrupción y reinicio conservan una sola custodia de ofrendas y una sola identidad |
| Presentación | Tres variantes, equipo, UV/capa exterior y textos legibles; servidor dedicado arranca sin clases cliente |

Las pruebas de rendimiento anotan hardware, semilla, mods, distancias de simulación/visión, duración y versión exacta.
Los presupuestos en ms son objetivos a medir, no garantías por contar lecturas de bloque. Bajo sobrecarga se reduce
el trabajo de escaneo por turno, nunca se omiten permisos ni se ejecuta todo el plan de golpe.

### 23.9 Referencias y decisiones pendientes reales

Referencias consultadas para revisar el enfoque, no para dar por válidas firmas de otra versión:

- [Eventos de Fabric](https://docs.fabricmc.net/develop/events/): los callbacks pertenecen a puntos de ejecución
  concretos; una modificación directa del mundo necesita comprobar qué eventos atraviesa.
- [Networking de Fabric](https://docs.fabricmc.net/develop/networking): separación de lados y validación del contenido
  de las peticiones en el servidor. La autoridad descrita en 23.7 es una decisión de arquitectura de este mod.
- [FakePlayer, API publicada para 26.2](https://maven.fabricmc.net/docs/fabric-api-0.152.0%2B26.2/net/fabricmc/fabric/api/entity/FakePlayer.html):
  confirma la existencia del concepto en Fabric; la firma y comportamiento de la dependencia objetivo se comprueban en M0.
- Referencias locales: `mods/TheBall/gradle.properties`, `build.gradle` y `src/main/resources/fabric.mod.json` para la
  base declarada de versiones y separación de fuentes; no demuestran que las clases propuestas ya existan aquí.

**Antes de cerrar v1:** decidir con mediciones si conviene paralelizar hornos y ajustar hambre, pausas y rendimiento
por tipo de recurso. No añadir nuevas órdenes, estaciones o formas de transferencia hasta que pase el primer corte.
La XP por defecto, capa exterior y cola de varias órdenes ya tienen un tratamiento de v1; no necesitan otra votación
para implementar ese corte. La cola global de trabajadores no equivale a una lista de encargos por esclavo.

**Resumen de esta revisión:** se corrigen producción/procedencia, muestras, cambio de encargo, profundidad del plan,
contabilidad del ejemplo de hierro, fatiga, tope de diamantes, retorno con ticket, reintento sin 7×7, propiedad múltiple,
claims, botín atribuible, hash de skins y recuperación tras reinicio. Se añaden ayuda contextual, estados y relojes,
transacciones, zonas excluidas, custodia de ofrendas, red validada y criterios de aceptación observables.

## 24. Eficiencia sin perder inteligencia ni presencia

**Requisito del usuario, obligatorio para v1:** la optimización no debe afectar a la funcionalidad. Sigue buscando
minerales, reconociendo animales, cazando, abriéndose camino, recogiendo materiales y usando el mundo real sin un
jugador al lado. Un jugador que se acerque lo ve actuar con normalidad. Solo su dueño recibe el saludo de 25.3.
No se acepta ahorrar RAM congelando al trabajador, fingiendo producción o limitándolo a un decorado de un chunk.

### 24.1 Qué significa conservar el 3 × 3

Se conserva como **base funcional y vecindario operativo**, no como promesa de que nueve chunks sean toda su memoria.
La unidad de gestión es una necesidad concreta: entidad en su posición, próximos nodos de ruta, animal objetivo,
bloque que rompe y estación que cocina. Un chunk puede estar cargado sin simular entidades; un bloque puede cocinar
sin que un animal lejano se mueva. El gestor solicita los niveles necesarios y verifica su estado efectivo.

| Situación | Cómo ahorra | Qué no puede cambiar |
|---|---|---|
| Jugadores ya simulan la zona | Reutiliza esa carga y retira tickets propios redundantes | Movimiento, percepción, tarea y animación |
| Nadie cerca, minando | Mantiene entorno necesario para entidad, acceso, peligros y retorno | Rompe bloques reales y observa cambios reales |
| Buscando animales | Consulta índices de entidades del entorno cargado; explora nuevos tramos para observar más | Encuentra y persigue presas accesibles dentro de su radio |
| Presa cruza un chunk | Prepara el tramo siguiente y mantiene ticking de presa y cazador | No persigue una posición cacheada como si fuera un animal vivo |
| Horno funcionando | Mantiene su block ticking; prioriza trabajo útil cerca mientras cocina | Conserva tiempos, combustible, contenido y resultado reales |
| Dos esclavos se cruzan | Une requisitos y comparte carga física coincidente | Mantiene órdenes, inventarios y permisos separados |
| Un jugador se acerca o se marcha | Releva fuentes de carga sin huecos de simulación | La misma entidad y operación continúan; no hay aparición de una copia |
| En casa sin orden | Ningún ticket del mod; reposo ligero si otro ya carga el chunk | Puede verse, consultarse, comer y saludar cuando corresponde |

**Puerta de optimización:** si repartir niveles no reproduce el comportamiento del 3 × 3 de referencia, se mantiene
el nivel necesario. El 3 × 3 tampoco se amplía «por si acaso». Se comprueban el halo que impone el motor, el solape
durante cambios de chunk y el tiempo que tarda realmente en descargar. Los chunks aportados por otro mod no se
cuentan como ahorro si TheSlave está impidiendo que se liberen.

### 24.2 Inteligencia mediante memoria pequeña y trabajo por eventos

- **Percepción incremental de menas.** La primera observación y cada chunk nuevo se procesan por secciones, usando
  paletas para descartar secciones sin candidatos. Guarda posiciones compactas y cursores, no copias de chunks,
  arrays de todos los bloques ni referencias fuertes a chunks descargables. Una sección ya examinada no se recorre
  otra vez sin una invalidación o necesidad concreta. Los cambios relevantes se revalidan antes de actuar.
- **Percepción de animales.** Usa el índice espacial de entidades de Minecraft; consulta local como máximo cada
  20 ticks, escalonada entre trabajadores, y al cambiar de entorno o perder la presa. Recuerda pocos UUID y posiciones
  recientes, con caducidad de 40 ticks; antes de atacar resuelve la entidad real. No recorre todas las entidades del
  mundo ni las de cada chunk cada tick. No conoce animales de chunks nunca observados.
- **Abrirse camino.** Ruta por tramos y excavación segura siguen disponibles. La caché de rutas se invalida ante
  obstáculo, daño, cambio del objetivo o chunk; no recalcula porque pasó otro tick. Peligro inmediato conserva prioridad
  y comprobación de 10.1. No reduce el hitbox ni ignora colisiones para que el camino resulte más barato.
- **Plan compartido, ejecución propia.** El índice de recetas es global por servidor y generación de recetario;
  conserva ids/referencias a datos ya cargados por Minecraft, no una copia completa por esclavo. Se reutilizan reglas
  de producción y estimaciones; reservas, cantidades y estado de ejecución permanecen separados por orden.
- **Conservar progreso.** Minado, hornada y rastro persisten entre ticks y cambios de fuente de carga. No reinicia el
  bloque que lleva medio picado al cambiar de observador. La ruta puede adaptarse a cambios reales del mundo.
- **Reposo ligero.** Sin orden no barre recursos ni calcula planes; percepción social se ejecuta al empezar tracking
  del dueño y con una comprobación local escalonada. No mantiene un hilo, un futuro o una cola infinita por entidad.

Se mantiene la capacidad de observar todo el radio mediante exploración física; «no está en caché» nunca significa
«no existe». Limitar la caché puede obligar a consultar de nuevo, pero no elimina fuentes o recetas admitidas.

### 24.3 Presupuesto de RAM, CPU y red comprobable

No se promete un número fijo de MB para chunks: depende del terreno, entidades, block entities, versión y demás mods.
Se exige medir memoria **incremental** respecto al mismo servidor sin trabajadores, después de calentamiento y GC.
La optimización conserva la base de corrección; las cifras siguientes son objetivos iniciales, pendientes de medir.

| Recurso | Política |
|---|---|
| Trabajadores | 4 activos globales como en 12; sin límite adicional que reduzca la autonomía existente |
| Carga de mundo | Requisitos mínimos compartidos, huella y halo medidos; sin tickets de jugador ni 7 × 7 |
| Datos propios por entidad | Objetivo ≤ 64 KiB retenidos, excluidos objetos base de Minecraft y chunks; medir con heap profiler |
| Rastro y memoria | 256 posiciones, 64 estaciones, 64 tareas; sin listas que crezcan con horas de exploración |
| Candidatos de recursos | Caché acotada de 1024 posiciones por orden; cursores permiten continuar sin guardar todo el terreno |
| Skins del servidor | Archivo en disco por hash; LRU de 16 RGBA (256 KiB de píxeles), sin todas las skins decodificadas permanentemente |
| Recetario | Un índice compartido; invalidación por reload, sin duplicar resultados y componentes por entidad |
| Presupuesto de búsquedas | Se mantiene el techo global de 15.12; medir p95 y asignaciones, repartir sin ráfagas sincronizadas |
| Actualización de estado distante | Solo cambios y suscriptores autorizados; mapas como máximo 1 actualización/s, sin mandar terreno |
| Tracking cercano | Paquetes normales de entidad, sin un segundo flujo de posiciones por frame ni render especial de servidor |

El gestor admite solicitudes antes de crearlas en el mundo; no solicita decenas de chunks y los libera después para
aparentar un límite bajo. Expone chunks exclusivos, compartidos, en preparación y pendientes de descarga. La presión
de memoria reduce cachés recomputables y evita precargas especulativas; no detiene órdenes normales por una nueva
cuota autónoma ni salta validaciones. Si el hardware no sostiene la base funcional, se informa en las mediciones para
ajustar recursos o el límite de concurrencia con el administrador, no se degrada silenciosamente el comportamiento.

### 24.4 Prueba de equivalencia obligatoria

Ejecutar el mismo escenario y semilla con gestión de carga base y optimizada: obtener hierro desde cero; excavar hasta
una veta tras un cambio de chunk; localizar una vaca y seguirla al chunk contiguo; cocinar; cancelar y regresar;
desconectar al dueño; reiniciar; acercarse y alejarse durante minería y caza. Repetir con cuatro órdenes y zonas solapadas.
Comparar operaciones, resultados, progreso y tiempos. El mundo sigue introduciendo azar, por lo que se fijan semillas
y entradas cuando sea posible y se revisan las diferencias de rutas, drops y entidades.

**Criterio:** ninguna capacidad perdida, ninguna desaparición o reinicio visual de tarea al acercarse, ninguna presa
congelada por un ticket insuficiente, ninguna fuente omitida por expulsión de caché, mismo respeto a peligros y
permisos. Una optimización que falle vuelve a la base antes de publicarse. Registrar p50/p95/p99 de MSPT, heap retenido,
picos al cruzar fronteras, chunks reales y tráfico por cliente; publicar mediciones reproducibles, no «cuesta cero».

## 25. Localización en mapas y saludo al dueño

### 25.1 Requisito común de los mapas

**Si el mapa permite ver a otros jugadores, el esclavo debe ser localizable con un icono identificable**, nombre
permanente y orientación cuando el formato lo permita. Se respetan dimensión, escala, cobertura, distancia y
restricciones del servidor de ese sistema. No necesita recibir órdenes nuevas para aparecer.

No es un `ServerPlayer`: los mapas que filtran por jugadores no lo incluirán automáticamente por tener skin humana.
La solución es una integración de marcadores/radar, no conectarlo como jugador falso ni darle distancia de simulación
de jugador. La compatibilidad forma parte del desarrollo y se valida por versión, no se promete para cualquier mod
sin adaptador. **El mapa nativo tiene implementación propia; JourneyMap y Xaero son las primeras integraciones a verificar.**

`MapPresenceService` produce `esclavoId`, nombre, dimensión, posición, orientación, fase y revisión desde la entidad
real o el último estado persistido. Los adaptadores consumen esa información sin cargar ni generar un solo chunk.
Se suscriben solo clientes/mapas pertinentes, máximo 1 actualización/s y solo si hay cambios; el seguimiento cercano
de la entidad conserva su cadencia vanilla. Una posición persistida sin simulación se rotula «última posición conocida»
si el mapa admite texto, nunca se representa como movimiento en tiempo real.

### 25.2 Mapa nativo y adaptadores de minimapa

| Sistema | Implementación prevista | Comprobación de aceptación |
|---|---|---|
| Mapa nativo con seguimiento | Decoración dinámica equivalente al puntero de jugador, rotulada con el nombre y diferenciada en tooltip/leyenda si procede; registro/sync según la API real | Si las reglas de ese mapa muestran jugadores, muestra al esclavo dentro de cobertura y dimensión, también mientras trabaja solo |
| Mapas nativos copiados | Mismo id de esclavo en cada vista pertinente; no escribir una marca permanente en el terreno del mapa | Moverse, morir, transferirse o desconectar no deja iconos fantasma o duplicados |
| JourneyMap | Icono para `theslave:esclavo` y adaptador a su API/radar para localizar según las opciones permitidas | Nombre/icono correctos en minimapa y mapa grande, sin depender de convertirlo en jugador |
| Xaero | Verificar radar de entidades, icono personalizado y API disponible en la versión instalada | La categoría efectiva no lo oculta cuando el modo compatible permite localizar jugadores/esclavos; documentar ajustes necesarios |
| Otro mod con API | Adaptador opcional de marcador o entidad, reutilizando el servicio común | Ausencia del mod no causa errores; sin API compatible se declara pendiente y no se simula compatibilidad |

**Nativo:** no se asume que `setCustomNameVisible` añada decoración. M0 comprueba cómo registrar/actualizar decoración y
sincronizarla por destinatario en 26.3. La marca vive fuera de la pintura de píxeles y se elimina al dejar de cumplir
condiciones; no se ensucian guardados de mapas cada tick. No se agregan marcadores a mapas que no admiten seguimiento
para fingir equivalencia. Se prueban mapa en mano, copia, marco, fuera de cobertura, cambio de dimensión y cierre del menú.

**Visibilidad:** hereda las reglas que permitan localizar a otros jugadores y las restricciones de radar del servidor;
una integración no concede permisos extra por su cuenta. Si un mapa solo expone datos de entidades cercanas, el
adaptador necesita su mecanismo autorizado de marcadores para localización distante; no amplía tracking ni carga
chunks para sortearlo. La experiencia de búsqueda debe funcionar con el alcance que ese mapa permite para jugadores.
La muerte retira el icono vivo; la tumba puede mostrarse como marcador distinto para el dueño si el sistema lo permite.

**Integraciones opcionales:** detección del mod en cliente, dependencia `compileOnly` o equivalente verificado y clases
aisladas; ninguna referencia a clases de mapas desde el arranque común. Servidor no necesita cargar sus renderers.
Los datos remotos van solo a receptores autorizados; no se transmite la lista global de esclavos a todos los clientes.

### 25.3 Al acercarte, lo ves trabajando y solo te saluda a ti

La entidad permanece en su posición real: pico en mano mientras mina, ataque mientras caza y espera visible mientras
cocina. Cualquier jugador dentro de tracking ve ese comportamiento y su nombre; no se condiciona el render a ser dueño.
Al empezar tracking recibe una instantánea de fase, objetivo, herramienta y progreso de animación para no aparecer
ocioso hasta el siguiente evento. El trabajo no se reinicia al aparecer en una pantalla.

**Saludo automático:** se activa cuando el dueño entra a ≤ 8 bloques, con línea de visión y al menos 2 s de presencia.
Levanta la mirada y hace un saludo corto de cabeza/mano disponible; una frase privada, por ejemplo «Aquí sigo, jefe»,
usa el límite de mensajes existente. Si está picando con la mano ocupada, usa cabeza y voz sin cancelar la rotura.

- Solo compara UUID de dueño, nunca skin, nombre, equipo o jugador más cercano. Terceros pueden verlo, pero no reciben
  saludo ni cambian su atención de trabajo. Los gestos de rechazo por intentar ordenar siguen separados del saludo.
- No vuelve a saludar por moverte un bloque: enfriamiento de 60 s y rearme tras estar a > 16 bloques durante 10 s.
  Entrar/salir del tracking por lag o un chunk no cuenta por sí solo como una nueva visita.
- Peligro, combate, caída o ritual posponen el saludo hasta estar seguro. Nunca abre menú, detiene ruta, tira
  herramienta, cancela una tarea o da la carga como efecto de saludar.
- La pose se ve para quienes ya siguen la entidad; la línea de texto solo llega al dueño. Transferir actualiza la
  identidad destinataria y borra la visita anterior; no sigue saludando al propietario antiguo.

### 25.4 Pruebas y referencias de integración

Se añaden a M5/M8: mapa nativo con dos jugadores y un esclavo; icono distante sin dueño cerca; cero tickets adicionales
por abrir mapas; cuatro esclavos actualizándose con el límite de red; icono sin duplicados tras reload, muerte y
transferencia; ausencia de errores sin mods de mapas; dueño que se acerca durante minado y tercero que hace lo mismo.
El saludo no modifica progreso, target ni inventario. El icono se puede usar para llegar y encontrarlo en la tarea
indicada, dentro de la precisión y alcance del mapa, sin teletransporte de presentación.

Fuentes primarias para concretar los adaptadores, consultadas en esta revisión:

- [JourneyMap: iconos personalizados](https://teamjm.github.io/journeymap-docs/latest/tools/custom-mob-icons/): describe
  iconos de entidades y radar; su existencia no garantiza localización remota de un trabajador descargado.
- [JourneyMap: integración](https://github.com/TeamJM/journeymap-docs/blob/6.0.x/docs/en/tools/integration.md): guía para
  comprobar categorías y API. La versión compatible con Minecraft objetivo se fija al implementar.
- [Xaero's Minimap, página del autor](https://chocolateminecraft.com/minimap2.php): anuncia radar de entidades e iconos;
  se verifica qué integración permite la versión instalada y sus reglas de visibilidad.

**Cambio de prioridad:** bajo consumo, autonomía física completa, mapas y observación/saludo son requisitos de v1.
El objetivo no es esconder coste recortando habilidades: es evitar trabajo duplicado y cargar solo lo que el motor
necesite para mantener exactamente esas habilidades. Ninguna cifra de ahorro se considera demostrada sin las pruebas.

## 26. Buscar recursos y elegir inteligentemente por dónde minar

**Decisión funcional:** superficie y subsuelo tienen estrategias distintas. Para un mineral no elige primero una
mina cualquiera y después busca algo dentro: **localiza vetas próximas, compara accesos razonables y ejecuta en dos
fases: llegar al nivel/zona de trabajo → extraer**. Conocer la posición de una mena no autoriza cavar verticalmente
desde donde está. Reutiliza preferentemente una cueva o mina explorada por jugadores, reconocible sobre todo por
antorchas, siempre que ayude a llegar a ese objetivo sin un rodeo desproporcionado.

### 26.1 Clasificar el encargo y localizar objetivos

1. Resolver el producto hasta la fuente física que falta: hierro → hierro en bruto/mena; pan → trigo; filete → vaca.
   Si el cofre asignado o los sobrantes resuelven la necesidad, los usa como en 6.4 y evita una expedición innecesaria.
2. Fuentes de **superficie**: árbol, cultivo, arena, animal o mena expuesta accesible. Busca el objetivo compatible
   más próximo y seguro, camina y trabaja. Una mena en una pared exterior no exige bajar primero a una mina.
3. Fuentes de **subsuelo**: selecciona hasta 12 grupos de mena observados del recurso buscado. Mantiene su ventaja
   de conocer coordenadas dentro del área observada, aunque estén detrás de roca; no lanza rayos de visión para
   fingir que solo ve caras expuestas. Ordena primero por distancia 3D y herramienta necesaria, conservando alternativas.
4. Una posición no observada es desconocida, no vacía. El radio de exploración es **horizontal**, 32 bloques desde el
   origen, 48 en reintento; la búsqueda vertical consulta secciones de ese entorno en los niveles relevantes del
   mundo. Esto permite bajar a una mina bajo la base sin que la profundidad agote por sí sola el radio horizontal.
   Lecturas solo sobre chunks disponibles y en lotes; no carga una columna de chunks extra para cada veta.

El conocimiento exacto de vetas es local, no un índice de todas las menas del servidor. Cuando no hay observaciones
suficientes, explora para obtenerlas dentro del mismo radio, sin afirmar que sabe dónde está un recurso fuera de él.

### 26.2 Reconocer una mina de jugador: cueva con antorchas

**Señal principal indicada por el usuario: una cueva con antorchas.** No exige carteles ni bloques nuevos. Al observar
el entorno, registra entradas transitables y corredores de aire que bajan hacia roca, junto con antorchas de pie o
pared. Las antorchas de alma también cuentan como señal de exploración, independientemente del ritual.

| Evidencia | Interpretación | Uso |
|---|---|---|
| Entrada a cueva + antorcha en el corredor accesible | Mina/cueva probablemente explorada | Alta prioridad para reutilizar |
| Varias antorchas enlazadas por un corredor transitable | Ruta de exploración con continuidad | Mayor confianza para seguir ese tramo |
| Escalera excavada, galería regular y antorchas | Acceso minero preparado | Prioridad alta, si su dirección y nivel ayudan |
| Cueva sin antorchas | Acceso natural aún no preparado | Alternativa; calcula luz y seguridad necesarias |
| Antorcha aislada en campo, fachada o cerca de cofre/cama | No demuestra una mina | No crea una entrada minera por sí sola |
| Antorchas detrás de pared, puerta cerrada o claim sin permiso | Señal conocida, acceso no autorizado o no probado | Descarta esa ruta; no rompe para acercarse a la luz |

**Detección barata:** reutiliza bloques y posiciones ya observados por el escáner, comprueba señales a lo largo de las
rutas candidatas y consulta el nivel de luz del juego cuando hace falta. No busca todas las antorchas del mundo ni
ejecuta un flood fill de todas las cuevas. Para enlazar señales, usa rutas cortas y limitadas sobre espacio existente;
proximidad euclídea de dos antorchas separadas por una pared no demuestra que exista corredor.

Una primera antorcha basta para considerar el acceso; no exige ver desde la superficie la mina completa. Conforme
avanza encuentra nuevas antorchas y confirma la continuidad. Cada acceso recordado contiene entrada, última posición
segura, nivel alcanzado, señales observadas y última validación. Máximo **16 accesos**, como parte de las 64 estaciones/
anclas de memoria, no otra lista creciente. Conserva las rutas útiles, no un grafo de cada bloque del subsuelo.

**Las antorchas no conceden propiedad ni permiso.** También aparecen en estructuras generadas y pueden colocarlas
otros jugadores. «Explorada por jugadores» es una inferencia de navegación. Claims, exclusiones, casas y bloques
colocados siguen protegidos. Una antorcha dentro de un corredor minero, sin señales de vivienda, no crea por sí sola
una esfera de base de 10 bloques que haga imposible usar esa mina; las otras señales de base de 15.7 sí se respetan.
No retira antorchas ni aprovecha una etiqueta de mina para romper soportes o abrir cofres ajenos.

### 26.3 Comparar parejas de veta y acceso, no solo distancias

Para cada veta próxima calcula alternativas de acceso:

- **A. Mina explorada:** entrada conocida con antorchas, escalera o corredor ya abierto. Camina por el acceso y desde
  un frente autorizado abre la galería necesaria hacia la veta, con herramientas, luz y escalones reales.
- **B. Cueva natural:** entrada transitable sin preparar. Puede aprovechar su desnivel si puede iluminarla y el riesgo
  está permitido. No se mete en una cueva peligrosa solo porque haya un hueco.
- **C. Nueva bocamina:** cara de ladera o roca discreta, fuera de base y exclusiones, con apoyo, altura y regreso
  válidos. Abre escalera lateral hasta el nivel adecuado y después galería. No agujerea el suelo donde recibió la orden.

El coste se expresa en segundos estimados y separa **marcha por hueco existente, excavación de acceso, galería hasta
la veta, preparación y regreso**. El mismo tiempo de extracción compartido no se cuenta varias veces. Se tienen en
cuenta dureza, herramienta, hambre/moral y luz. Lava, claims, caídas o soportes inseguros son veto, no una penalización
que pueda compensarse con cercanía. Desconocer un tramo añade incertidumbre y exige validarlo al acercarse.

**Regla de prioridad con desvío acotado:**

1. Encuentra la pareja veta/acceso viable de menor coste: `Tmejor`.
2. Entre los accesos explorados, prefiere el más barato **solo si** su tiempo no supera
   `Tmejor + min(0,25 × Tmejor, 60 segundos)`. Evita ir a una mina lejana para aprovechar una antorcha.
3. Si no hay mina explorada dentro de ese margen, elige cueva natural o bocamina nueva según el coste real permitido.
4. Si dos opciones siguen empatadas, favorece menor excavación nueva, después menor riesgo y distancia de retorno.

Los valores 25 % y 60 s son iniciales para pruebas, no una decisión que deba mantenerse si produce rodeos absurdos.
Priorizar reutilización significa preferir trabajo ya hecho **cuando compensa**, no hacerlo a cualquier precio.
La veta más cercana puede quedar descartada si exige cruzar lava o romper una casa; debe explicar ese caso y elegir
la siguiente accesible. No promete que distancia en línea recta sea siempre el mejor camino.

**Ejemplos de comportamiento esperado:**

| Situación | Decisión |
|---|---|
| Mena expuesta a 8 bloques, mina con antorchas a 50 | Extrae la expuesta; no baja por obligación |
| Veta enterrada cerca, acceso explorado cuesta 70 s y abrir lateral cuesta 60 s | Reutiliza la mina: 70 ≤ 60 + 15 |
| Misma veta, acceso explorado cuesta 240 s y abrir lateral cuesta 60 s | Abre bocamina discreta: 240 > 75; no cava verticalmente donde está |
| Dos vetas, la más próxima cruza una casa y otra tiene galería permitida | Usa la segunda; proximidad no anula protecciones |
| Hay antorchas a 5 bloques, pero detrás de una pared de base | No rompe la pared ni concluye que puede entrar por ahí |
| Ya está en una mina y hay otra veta próxima al frente actual | Amplía desde el frente seguro; no vuelve a la superficie para recalcular toda la bajada |

### 26.4 Ejecución en fases y aprendizaje reutilizable

**Fase 1: acceso.** Equipa lo necesario, llega a la entrada elegida y avanza por la cueva/escalera hasta una posición
de trabajo próxima a la cota objetivo. Seguir antorchas ayuda a reconocer un corredor, pero no a olvidar la dirección
de la veta: si el corredor se aleja demasiado, evalúa un frente lateral permitido en vez de seguirlo hasta el fondo.
Anota anclas de entrada, cambios de nivel y frente; respeta puertas y protecciones y no rompe nada para forzar acceso.

**Fase 2: extracción.** Desde ese frente seguro abre galería hacia la mena conocida, con altura del humanoide o
del duende, alternando comprobar entorno, romper y avanzar. Si falta desnivel, construye escalera de un bloque por
paso. Verificar fluidos, techo y suelo sigue siendo obligatorio. El objetivo exacto orienta la galería: no excava a
ciegas en una dirección aleatoria ni recorre pasillos que no acercan a una veta.

**Después de cada veta:** si queda presupuesto y cantidad, reordena las vetas observadas desde el frente actual.
Reutiliza la misma entrada, túneles y herramientas mientras compense. Una memoria de mina no reserva sus minerales:
si otro jugador los ha extraído, invalida esas posiciones, conserva el acceso útil y elige otra veta. Las estimaciones
se corrigen con tiempos observados de marcha/excavación; no necesita aprendizaje automático ni una red externa.

**Para recursos de superficie:** omite ambas fases mineras y sigue `localizar → caminar → recolectar/cazar → volver`.
Si preparar una herramienta requiere piedra/mineral, esa dependencia puede usar la estrategia minera sin convertir
la orden de superficie completa en una expedición subterránea.

### 26.5 Algoritmo de referencia y coste acotado

```text
elegir_fuente(necesidad, contexto):
    si inventario_autorizado_resuelve(necesidad): usar_material_real()
    si fuente_superficial_accesible(necesidad): plan_superficie()

    vetas = candidatas_observadas(necesidad, maximo=12)
    accesos = entradas_recordadas_y_locales(maximo=16)
    opciones = estimar_parejas(vetas, accesos, bocaminas_locales)
    descartar_prohibidas_y_ciclos(opciones)
    refinar_ruta_de_las_3_mejores(opciones)   // incremental, no 192 pathfindings
    si falta_observacion: explorar_tramo_util_y_continuar()
    si no_hay_viable: informar_causa_y_volver()

    mejor = menor_coste_viable(opciones)
    reutilizable = mejor_explorada_dentro_del_margen(mejor)
    elegida = reutilizable_o_mejor()
    ejecutar_acceso_y_despues_extraccion(elegida)
```

La estimación inicial usa distancias, niveles, corredores recordados y durezas de bloques ya observados; no calcula
rutas completas para todas las parejas. Se refinan como máximo 3 alternativas por decisión dentro del presupuesto
global de 15.12; las cotas baratas permiten continuar con otra candidata si una falla. Descubrimientos incrementales
no equivalen a una replanificación completa del encargo. No reinicia los límites, el reloj o el radio por cada veta.
Retiene accesos válidos entre órdenes y vuelve a comprobarlos al usarlos. Solo un cambio material invalida la elección;
no alterna continuamente entre dos entradas por diferencias mínimas de estimación.

**Consulta de estado:** «Entrando por la mina con antorchas», «Bajando a la veta de hierro» o «Abriendo una entrada
lateral; la mina conocida queda demasiado lejos». Un informe opcional de depuración muestra costes de las candidatas
y veto aplicado, para entender por qué eligió una ruta sin esperar a que se atasque.

### 26.6 Criterios de aceptación de esta estrategia

Añadir escenarios reproducibles: cueva con antorchas frente a cueva sin antorchas; antorcha de fachada; veta superficial
con mina distante; veta enterrada con acceso cercano; acceso iluminado con rodeo excesivo; ruta separada por pared;
veta agotada por un jugador; nueva bocamina; duende en túnel de un bloque; descenso con hambre/equipo insuficientes;
cuatro mineros reutilizando una mina y cruzando fronteras sin jugadores presentes.

Se acepta cuando selecciona por coste y permisos, respeta el margen de reutilización, no cava verticalmente donde
está, no destruye una base, aprovecha corredores abiertos y no pierde autonomía al entrar en un chunk nuevo.
Se mide cuántos bloques nuevos rompe y cuántos pasos de rodeo ahorra frente a elegir siempre la entrada más próxima.
