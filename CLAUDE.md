# LogitanosMods — contexto para Claude

Monorepo de mods **Fabric** del servidor de Minecraft **Logitanos**. Idioma del proyecto: **español**.
Cada mod es un proyecto Gradle independiente en `mods/<Nombre>/`. Hoy solo existe `mods/TheBall`.

## Estructura y convenciones

```
README.md                      índice de mods + instalar/compilar/añadir mod (para humanos)
jar/<Mod>/                     los .jar ya compilados, listos para el servidor
mods/<Mod>/
  README.md                    instalación, CRAFTEOS (con imágenes), mecánicas, ids, notas de desarrollo
  docs/                        notas de diseño; docs/recipes/*.png = imágenes de crafteo generadas
  tools/                       scripts (texturas, imágenes de crafteo)
  src/ build.gradle ...
```

No hay CI: **nada se compila en GitHub, todo se compila en local** (`gradlew build` o el `compilar.ps1` de cada mod)
y el `.jar` se copia a `jar/<Mod>/`. No crear workflows ni añadir jobs de Actions.

Cada mod debe indicar sus versiones de Minecraft / Fabric Loader / Fabric API / Java tanto en su README como en la
tabla del README raíz (se actualizan a mano si cambia `gradle.properties`).

Al añadir un mod nuevo hay que: crear `mods/<Mod>/`, escribir su README (con crafteos), y añadirlo a la tabla de
`README.md` raíz.
`.gitignore` raíz ya excluye `.gradle/`, `build/`, `run/`, logs de crash de la JVM (`hs_err_pid*`, `replay_pid*`).

Cosas del entorno: la ruta contiene `ñ` (`Coñas`); en Git Bash funciona entre comillas. Si el cwd del shell está
dentro de una carpeta que se va a mover, `mv` falla con "Permission denied". No hay commits todavía salvo que el
usuario los pida (`git init` hecho en `main`).

## Historial de lo que se hizo (sesión inicial)

1. La carpeta original era `TheBall/` con `CONTEXTO.md` y el proyecto en `TheBall/mod/TheBall/`. Se movió todo a
   `mods/TheBall/`, `CONTEXTO.md` a `mods/TheBall/docs/` (marcado como histórico/desfasado) y se borraron los logs
   de crash de la JVM.
2. El workflow de GitHub estaba dentro del mod (no lo lee GitHub en un monorepo): se movió a `.github/workflows/`
   con `working-directory: mods/TheBall`. **Después se eliminó: el usuario no quiere ejecuciones automáticas en la
   nube, así que no hay CI en el repo.**
3. El README del mod estaba desfasado (decía hierro en vez de aluminio, no tenía aluminio/portería/cono/yunque).
   Se reescribió **leyendo las fuentes reales**, no la documentación vieja:
   - Recetas: `src/main/resources/data/<modid>/recipe/*.json`
   - Worldgen de menas: `data/<modid>/worldgen/placed_feature/*.json` y `ModWorldgen.java`
   - Drops/herramienta: `data/<modid>/loot_table/blocks/`, `data/minecraft/tags/block/`
   - Mecánicas y cifras: las clases de `entity/`, `item/`, `mixin/` (p. ej. `foilCostFor`, `diameterFor`)
   - Nombres: `assets/<modid>/lang/es_es.json`

   **Regla: al documentar un mod, el código y los JSON mandan; los README/CONTEXTO antiguos pueden mentir.**

## The Ball 1.1 (el Trono y la Reina de Aluminio)

- Diseño en `mods/TheBall/plan_update.md` (con §17 = lo que cambió al implementarlo). Estructura del código en la
  sección «Para desarrolladores» del README del mod. Balance en `config/theball.json` (clase `TheBallConfig`).
- Texturas de la 1.1: `tools/generate_king_textures.py` (no toca las de la 1.0, que salen de `generate_textures.py`).
- Para comprobar APIs de 26.3: `javap -cp <jar>` sobre los jars de `.gradle/loom-cache/minecraftMaven/...` (common y
  clientOnly; en Git Bash el classpath se separa con `;`). Cosas que ya han cambiado respecto a 1.21: `syncVelocity`
  en vez de `hurtMarked`, `EntityTypes.TEXT_DISPLAY`, `PoseStack.rotateDegrees(Axis, deg)`, `FLASH` necesita
  `ColorParticleOption`, `drop(stack, false, Prediction.SERVER_ONLY)`, `ServerEntityLevelChangeEvents`.
- `./gradlew runServer` usa `mods/TheBall/run/`; sin `eula.txt` aceptado se para tras inicializar el mod (sirve para
  ver que registra bien). Aceptar el EULA lo decide el usuario.

## Cómo se generan las imágenes de crafteo

Las recetas se muestran como imágenes estilo mesa de crafteo (`mods/<Mod>/docs/recipes/*.png`), generadas por
`mods/TheBall/tools/generate_recipe_images.py` (Python + Pillow; no requiere Minecraft abierto).

Cómo funciona:
- **Texturas del mod**: lee los PNG de `src/main/resources/assets/<modid>/textures/` (refs `mod:item/x`, `mod:block/x`).
- **Texturas vanilla** (pan, carne, hilo, lana…): las lee del **jar del cliente de Minecraft** que Gradle/Loom deja
  tras compilar una vez: `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-*/<versión>/*.jar`
  (refs `mc:item/x`, `mc:block/x`). Se autodetecta o se pasa con `--jar`. Si el mod no se ha compilado nunca, no
  existe: compilarlo primero (`./gradlew build`) o pasar otro jar.
- **Bloques** (`cube:<ref>`): se dibujan como cubo isométrico con las caras sombreadas (top 100 %, izq. 80 %, der. 60 %).
  Items sin textura propia (el cono, cuyo item es un modelo de bloque con varios elementos) se dibujan a mano en
  `cone_icon`; para otro item parecido, hacer una función igual.
- **GUI**: el panel, los huecos y la flecha se dibujan con Pillow imitando la GUI vanilla (gris #C6C6C6, bordes
  biselados). No se usa la textura real de la GUI.
- Dos funciones públicas, usadas desde `build(tex)`:
  - `crafting(tex, nombre, grid, resultado, count, note, shapeless)` → mesa 3×3 (siempre dibuja los 9 huecos).
  - `simple(tex, nombre, título, filas, note)` → filas `entradas → salida` para horno, cortapiedras, yunque, etc.
- Las recetas están **escritas a mano en `build()`**: el script no lee los JSON de `recipe/`. Si cambia una receta o
  se añade otra, hay que editar `build()` y regenerar (`python tools/generate_recipe_images.py` desde `mods/<Mod>`).
- Para materiales intercambiables (varias carnes) se muestra una y se explica en la `note` de la imagen.
- Las imágenes se enlazan desde el README del mod: `![Receta del X](docs/recipes/x.png)`. **Cada receta lleva
  debajo, en texto, "Materiales necesarios" (lista con cantidades: `6 × Pan`...) y "Resultado"** (el usuario lo pidió
  porque solo con la imagen no se entiende, p. ej. el cono: 1 tinte + 1 lana = 4 conos). Los materiales se sacan
  contando los ingredientes del JSON de la receta, no mirando la imagen.

### Para un mod nuevo

1. Copiar `tools/generate_recipe_images.py` a `mods/<NuevoMod>/tools/`.
2. Cambiar `MOD_TEX` si el layout difiere, y reescribir `build()` con sus items y recetas (lee antes los JSON).
3. Compilar el mod una vez (para tener el jar de Minecraft), ejecutar el script y **mirar al menos algunas imágenes**
   (texto cortado, huecos mal puestos, texturas raras) antes de enlazarlas.
4. Si hay ≥ 2 mods, valorar mover el script a `tools/` en la raíz y parametrizarlo por mod, en vez de copiarlo.

### Notas / trampas ya encontradas

- Texto de notas/títulos más ancho que el panel → se cortaba; ahora el ancho se calcula con `text_width`.
- El script usa `Image.get_flattened_data()` (Pillow ≥ 12; `getdata` está obsoleto).
- Las imágenes contienen texturas de Mojang (pan, carne…); es práctica habitual en wikis de mods, pero conviene
  decírselo al usuario si pregunta por licencias.

## README de cada mod: estructura esperada

Título y una frase · **tabla de versiones (Minecraft, Fabric Loader, Fabric API, Java, versión del mod; salen de `gradle.properties` y `fabric.mod.json`)** · instalación (servidor, jugadores, conseguir el .jar) ·
**Crafteos** (imagen + Materiales necesarios + Resultado) · dónde encontrar los materiales · cómo se juega / mecánicas con cifras · pestaña de creativo ·
tabla de ids (+ ejemplo de `/give` con componentes) · sección para desarrolladores (estructura y qué se cambia fácil).
