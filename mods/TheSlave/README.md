# The Slave

Un esclavo con contrato que mina, craftea y funde lo que le pidas, controlado con gestos y un látigo. Ahora se adquiere a unos **Notarios de Ceniza** escondidos, se invoca de noche ante un **Altar de Ceniza**, y si muere solo vuelve con un tributo. Dos esclavos pueden **fundirse** en uno.

| | |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | ≥ 0.20.1 |
| Fabric API | 0.165.0+26.3 |
| Java | 25 |
| Versión del mod | 1.0.0 |

**Estado:** ampliación en revisión con pruebas automatizadas de economía, registros, herencia, guiones, recursos y geometría. No se ha validado dentro de una partida. Por instrucción del usuario, en este equipo no se arranca Minecraft ni un servidor local: solo compilación y pruebas de código/simuladas. Ver "Pendiente y limitaciones".

## Diseño del lore oscuro

- [Lore y mecánicas acordadas](dark_lore/new_lore.md).
- [Guía técnica de implementación y producción de audio](dark_lore/instructions_lore.md).

## Instalación

- **Servidor y jugadores:** el mod debe estar en ambos (hay pantallas, renderers y HUD de cliente).
- El `.jar` está en `jar/TheSlave/theslave-1.0.0.jar` (en la raíz del repo) o en `build/libs/` tras `./gradlew build` (ignora el `-sources`).
- Cópialo a la carpeta `mods` junto con Fabric API.
- Los mundos existentes siguen funcionando: `theslave_registro.json` se migra a `theslave_vinculos.json`; los enclaves solo aparecen en chunks nuevos (o con `/theslave enclave`).

## Crafteos

El Contrato ya **no se craftea**: se compra a un Notario. Se craftean estos tres:

**Relicario de Ánimas** (sin forma): 3 × Bloque de vidrio, 1 × Lingote de oro → 1 relicario (capacidad 64 almas).

**Látigo** (con forma): 1 × Hilo, 2 × Cuero, 1 × Palo → 1 látigo.

```
. . H      H = Hilo
. C .      C = Cuero
P C .      P = Palo
```

**Brújula de Ceniza** (receta especial, 3x3):

```
Cuarzo      Hierro     Cuarzo
Hierro      Redstone   Hierro
Netherrack  Hierro     Relicario (con >= 1 Alma Vigilante)
```

Materiales necesarios: 2 × Cuarzo, 4 × Lingote de hierro, 1 × Redstone, 1 × Netherrack, 1 × Relicario con al menos un Alma Vigilante. Resultado: 1 Brújula de Ceniza; el relicario vuelve al inventario con un Alma Vigilante menos.

> Las imágenes de crafteo no se han generado: el script del repo necesita Pillow, que no está instalado en esta máquina.

## Cómo se juega

1. **Capturar almas:** con un Relicario de Ánimas en el inventario, matar **cuerpo a cuerpo** un zombi (Hambrienta), esqueleto (Vigilante) o araña (Acechante) mete el alma sola. Las muertes a distancia o por terceros no cuentan.
2. **Encontrar un Notario de Ceniza** (brújula o exploración). Vende, con el precio exacto en pantalla:

| Oferta | Precio |
|---|---|
| Contrato de Condena, grado 1 (0 vínculos) | 16 Fe + 4 Au + 2 de cada alma |
| Contrato de Condena, grado 2 (1 vínculo) | 32 Fe + 8 Au + 2 Dia + 8 de cada alma |
| Contrato de Condena, grado 3 (2 vínculos) | 64 Fe + 16 Au + 6 Dia + 14 de cada alma |
| Tributo de la Condena | 4 Au + 1 Esm + 1 de cada alma |
| Sello de Almas | 16 Au + 8 Esm + 3 Dia + 3 de cada alma |
| Tributo de la Doble Condena | 16 Au + 6 Esm + 1 Dia + 2 de cada alma |
| Relicario de Ánimas | 1 Au |
| Altar de Ceniza | 4 Fe |

   La compra de un contrato entrega **Contrato de Condena + Ofrenda a la Muerte (con las almas) + látigo nuevo**. Máximo 3 vínculos y un solo contrato sin usar. Se puede **devolver** si contrato, ofrenda y látigo siguen intactos.
3. **Invocar** (Altar de Ceniza, de noche, en el Overworld): usa el Contrato de Condena sobre el altar llevando su Ofrenda a la Muerte. El látigo recibido no es un requisito de invocación. Ceremonia de 12 s; el esclavo agradece su regreso, entrega su **Lecho de Ceniza personal** y le pones nombre. El altar necesita espacio libre detrás (portal) y delante (cuerpos).
4. **Órdenes:** clic con la mano vacía sobre un esclavo propio abre *Seguirme / Esperar aquí* (la mochila pasa a *agachado + mano vacía*). Los gestos con el látigo siguen igual (ver `idea.md`).
5. **Hogar y aptitudes:** coloca su lecho de dos bloques en casa. La ficha de órdenes muestra sus cinco aptitudes (apetito, resistencia, agilidad, minería y recuperación), normalmente alrededor de ±10–20 %. Agachado con comida entregas provisiones en cantidad: come de su mochila, descansa y reanuda el mismo encargo. El descanso ordinario es de cinco minutos; un fusionado especializado puede reducirlo a dos.

### Muerte, súplica y fusión

- Al morir, **su lecho de casa se vuelve tumba** y guarda su alma, también si murió en otra dimensión. El equipo y la carga caen donde murió el cuerpo. Si aún llevas el lecho sin colocar, colocarlo crea la tumba correspondiente.
- Interactúa con la tumba para **recoger su alma** una vez. Llévala con un **Tributo de la Condena** a cualquier Altar de Ceniza, de noche en el Overworld (20 s). Para un fusionado necesitas el **Tributo de la Doble Condena** (24 s). Basta con usar una pieza llevando la otra; la tumba permanece en casa. Al volver, conserva identidad y aptitudes y su tumba vuelve a ser lecho.
- Agachado puedes recoger el lecho para moverlo. Para renunciar definitivamente al difunto: **agachado + pico, dos interacciones en cinco segundos**; se libera su plaza. Un alma antigua o de un vínculo liberado queda inválida.
- **Fusión** (18 s): usa el Sello de Almas con dos normales tuyos a menos de seis bloques y con paso libre. La pantalla muestra atributos y aspecto, permite intercambiar las mitades y requiere confirmar. Caminan, quedan suspendidos por cadenas, sus mitades convergen y se cosen. El vínculo superviviente conserva su hogar; un fusionado no vuelve a fusionarse.
- El consumo definitivo se realiza al final. Amanecer después de empezar no cancela la ceremonia. Una interrupción anterior conserva los objetos. La confirmación ya iniciada se recupera mediante su WAL; no se reproduce la animación desde su tick anterior.
- Las partidas anteriores reciben sus lechos pendientes mediante entrega durable, cuando haya espacio en el inventario; las lápidas antiguas permiten recoger el alma.

## Config (`config/theslave/config.json`)

`presupuestoMinutos` (1-120), `radioMenas` (1-32), `escaneoGlobalPorTick` (128-4096; defecto 1024), `escaneoPorEsclavo` (16-256; defecto 128), `caducidadDias` (1-365), `respetarProtecciones`, `oscuridadRitual`, `maxPorJugador` (fijo en 3). Se recarga con `/theslave recargar`. (`temblorCamara` ya no hace nada.)

La busqueda de bloques reparte un presupuesto global entre trabajadores y visita primero los bloques cercanos. No fuerza cargas de chunks. Con protecciones activadas, la mineria espera si el propietario no esta conectado en la misma dimension o no puede construir; valida permisos y los eventos de rotura de Fabric antes de extraer. Las recetas se preparan por lotes compartidos al arrancar o recargar datapacks.

La fabricacion y fundicion usan recetas nativas y conservan componentes, recipientes y combustible sobrante. Se procesan como maximo 16 tandas por accion de trabajo. Las herramientas equipadas tambien se usan para minar. Repetir un encargo toma una muestra real del inventario del propietario.

Las entregas de provisiones, equipo, muestras y cobros usan custodia persistente entre jugador y esclavo. Si no cabe toda la carga, el resto permanece con el esclavo. La recepción de órdenes conserva su muestra al desconectarse el dueño. Detalles y límites en [la revisión de seguridad y recursos](dark_lore/security_performance_review.md).

## Comandos (op)

`/theslave recargar` · `/theslave simular <item> <n>` · `/theslave tumba escena` · `/theslave tumba resucitar` · `/theslave notario` (coloca un Notario) · `/theslave enclave <cueva|superficie|casa|refugio>` (construye un enclave en el chunk actual)

## Ids

| Qué | Id |
|---|---|
| Objetos | `contrato`, `latigo`, `relicario_animas`, `ofrenda_muerte`, `tributo_condena`, `sello_almas`, `tributo_doble_condena`, `brujula_ceniza`, `alma_vinculada` y el bloque-objeto `lecho_ceniza` (todos `theslave:`) |
| Bloques | `lecho_ceniza`, `tumba`, `altar_ceniza`, `piedra_infectada`, `pizarra_infectada`, `tierra_infectada`, `cesped_infectado`, `grava_infectada`, `arena_infectada`, `netherrack_infectada`, `piedra_negra_infectada`, `raiz_ceniza`, `raicillas_infectadas`, `losa_ceniza`, `velas_notario`, `urna_animas`, `archivo_condenas`, `mesa_notario`, `lampara_animas`, `piedra_registro` |
| Entidades | `theslave:esclavo`, `theslave:la_muerte`, `theslave:notario`, `theslave:umbral` |
| Componentes | `theslave:almas`, `theslave:pacto`, `theslave:alma_vinculada` |
| Receta especial | `theslave:brujula_ceniza` |
| Pestaña creativa | `theslave:the_slave` |

```
/give @s theslave:relicario_animas[theslave:almas={hambrienta:3,vigilante:2,acechante:1}]
```

## Enclaves

Cada región (Overworld ~2.240 bloques, Nether ~990) propone sus enclaves a partir de la semilla; solo se construyen al generarse un chunk nuevo con terreno válido. Tipos: **cueva** (entrada lateral natural, sin pista en superficie), **superficie** (ruina con escalera), **Casa bajo las Raíces** (bosque de roble oscuro) y **refugio del Nether** (plataforma exterior con espacio abierto). Se excluyen 1.500 bloques alrededor del spawn del Overworld y se separan los enclaves al menos 2.000 bloques en Overworld y 1.000 en Nether. La entrada está en la celda (2, 7) del chunk. La infección es una huella estática; no crece ni se propaga.

La brújula apunta exclusivamente a entradas construidas del mismo mundo. Si no hay ninguna, usarla inicia una búsqueda asíncrona de una región, con hasta dos chunks candidatos, una tarea por dimensión y diez segundos entre búsquedas. No modifica chunks antiguos. Una zona permite rastrear hasta nueve regiones; si no se encuentra terreno válido, hay que desplazarse y volver a buscar. Llevarla en el inventario no genera terreno. Tras reiniciar, las entradas indexadas se vuelven a verificar físicamente antes de usarlas como destino; las huérfanas se descartan.

## Para desarrolladores

- Icono del mod: `assets/theslave/icon.png`, registrado en `fabric.mod.json`. Emblema pixel art de La Muerte, el Umbral y sus cadenas, dibujado a 64×64 y ampliado a 256×256 sin suavizado. Se regenera con `python tools/generate_mod_icon.py` sin modificar objetos ni texturas protegidas.

- `src/main`: `ai/`, `entity/`, `ritual/` (`Ceremonias`, `Ritual`, `Captura`), `comercio/` (precios y simulación de inventarios antes del pago), `worldgen/` (`Enclaves`, `Estructuras`), `block/`, `registry/`, `net/`, `Registro` (vínculos), `Aptitudes`.
- `src/client`: renderers, pantallas (`NombreScreen`, `TumbaScreen`, `OrdenesScreen`, `NotarioScreen`, `FusionScreen`) y `RitualOverlay`.
- `python -I tools/generate_lore_assets.py` regenera los recursos del lore. No ejecutar el generador base completo: los assets de látigo y contrato están protegidos por hashes. `python tools/test_lore_assets.py` comprueba que permanecen intactos.
- Compilar y verificar: `./gradlew build`. Previsualizar modelos JSON sin juego: `./gradlew previewLore`. No ejecutar `runClient` ni `runServer` en este equipo.

## Látigo: asset y animación

- Icono pixel art de **32×32**, transparencia binaria y paleta de cuero; espiral logarítmica de **2,4 vueltas**, punta hacia el centro y mango recto tangente. El inventario siempre utiliza el icono, también durante los golpes.
- Primera/tercera persona, suelo y marcos utilizan un **tubo octogonal continuo**, con textura pixelada, normales, tapas y grosor decreciente. Comparten la geometría del icono mediante `assets/theslave/latigo_geometry.json`; el JSON de cubos sirve como respaldo.
- Animación visual de 18 ticks: anticipación hasta el tick 3, despliegue progresivo hasta el chasquido del tick 9 y retorno a la espiral exacta en el tick 18. Una única cuerda conserva la longitud de todos sus segmentos. El mango permanece rígido.
- El servidor anuncia inicio/cancelación a quien lo usa y a los observadores. Cambiar de ranura, soltar el látigo, morir o desconectarse limpia el despliegue. El cambio de dimensión también reinicia el estado visual.
- Se conservan los gestos y controles existentes del mod. La cuerda desplegada tiene dirección visual local al objeto: aún hay que ajustar en juego su alineación exacta con el objetivo, ambas manos y distintos FOV.

## Verificación y límites

- Compilación de servidor y cliente y pruebas automatizadas de economía, migración, captura de almas, mantenimiento, modelo de guardado, geometría, UV y recursos. Los ensayos de caída usan fronteras controladas y archivos reales; no se ha ejecutado una partida ni validado la integración completa con un mundo en funcionamiento.
- **Audio aplazado por decisión del usuario.** No se ha utilizado ElevenLabs. Los sonidos vanilla son provisionales.
- Portal opaco, recorte geométrico de La Muerte, cuerpos temporales, gestos, cadenas, mitades y costura implementados. Falta inspección visual GPU desde primera y tercera persona; las previsualizaciones disponibles son externas al juego.
- WAL, identidad estable, recibos, custodia, ACK de playerdata/entidades y recuperación tras reconectar implementados. Las garantías probadas se limitan a los escenarios de las pruebas; no se afirma tolerancia universal a corrupción del disco, fallos de energía o interferencias de otros mods. Ver [auditoría de persistencia](build/reports/persistence-audit.md) y [rituales](build/reports/ritual-audit.md).
- La generación y la brújula tienen presupuestos limitados y validan destinos. Frecuencia efectiva, rutas de IA y composición de enclaves requieren evaluación en un mundo autorizado.
- Los contratos antiguos sin pacto ya no generan entidades gratis; la migración conserva los vínculos y la recuperación de los difuntos.
- La fusión conserva mochila, referencia y equipo. Si no caben, cancela sin retirar los originales ni el sello.
- Los recursos de látigo y contrato están protegidos por hashes. Solo se han revisado sus pruebas; sus assets se mantienen intactos.
