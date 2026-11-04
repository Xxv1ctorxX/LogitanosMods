# The Ball

Te comes un **bocata** (o un **durum**), te devuelve **papel de aluminio**, y con el papel fabricas una **bola**
que puedes agrandar, empujar, lanzar, recoger… y meter en una **portería**.

## Versiones

| Requisito | Versión |
|---|---|
| **Minecraft** | **26.3** (solo esta versión) |
| **Fabric Loader** | **0.20.1** o superior |
| **Fabric API** | **0.165.0+26.3** (la de 26.3; cualquiera de esa versión vale) |
| **Java** | **25** o superior |
| Versión del mod | `1.0.0` · mod id `theball` · Licencia MIT |

---

## Instalación

Necesitas **un solo `.jar`** (`theball-1.0.0.jar`) y [Fabric API](https://modrinth.com/mod/fabric-api) para Minecraft 26.3.

### Servidor

1. Servidor **Fabric** (Loader ≥ 0.20.1) para **Minecraft 26.3** con **Java 25** (instalador: <https://fabricmc.net/use/server/>).
2. Mete en `mods/` del servidor `theball-1.0.0.jar` y Fabric API.
3. Arranca el servidor.

### Jugadores

Cada jugador necesita **el mismo `.jar` + Fabric API** en su carpeta `mods/`. La bola es una entidad nueva con render
propio y los clientes vanilla no la pueden mostrar. No hay versión "solo servidor".

### Conseguir el `.jar`

- **Releases** del repo (si hay tag `theball-v*`), o
- **Actions → Build → artefacto `theball-jar`**, o
- Compilarlo tú (JDK 25): desde esta carpeta, `./gradlew build` (Windows: `gradlew.bat build`).
  Sale en `build/libs/theball-1.0.0.jar` (ignora el `-sources`). Para probar en el juego: `./gradlew runClient`.

---

## Crafteos

Cada receta lleva su imagen y, debajo, **Materiales necesarios** (lo que gastas) y **Resultado** (lo que sale).
**Carne** = *cualquiera* de chuleta de cerdo, filete, cordero, pollo o conejo **cocinados**.
Las recetas "sin forma" se pueden colocar en cualquier hueco de la mesa.

### Comida

#### Bocata

![Receta del bocata](docs/recipes/bocata.png)

**Materiales necesarios**
- 6 × Pan
- 2 × Lingote de aluminio
- 1 × Carne cocinada

**Resultado:** 1 Bocata. Al comerlo devuelve **1 papel de aluminio** (nutrición 8, se puede comer con el hambre llena).

#### Durum

![Receta del durum](docs/recipes/durum.png)

**Materiales necesarios**
- 6 × Lingote de aluminio
- 2 × Pan
- 1 × Carne cocinada

**Resultado:** 1 Durum. Al comerlo devuelve **3 papeles de aluminio** (nutrición 10, se puede comer con el hambre llena).

> Si no te cabe el papel en el inventario, cae al suelo.

### Aluminio

#### Lingote de aluminio (fundir)

![Fundir aluminio](docs/recipes/furnace.png)

**Materiales necesarios**
- 1 × Mena de aluminio, Mena de aluminio de pizarra **o** Aluminio en bruto (cualquiera de las tres)
- Combustible (en un horno o un alto horno)

**Resultado:** 1 Lingote de aluminio (0.7 xp). Horno: 10 s · Alto horno: 5 s.

#### Papel de aluminio (sin forma)

![Receta del papel de aluminio](docs/recipes/aluminum_foil.png)

**Materiales necesarios**
- 2 × Lingote de aluminio

**Resultado:** 1 Papel de aluminio.

#### Bloque de aluminio

![Receta del bloque de aluminio](docs/recipes/aluminum_block.png)

**Materiales necesarios**
- 9 × Lingote de aluminio

**Resultado:** 1 Bloque de aluminio.

#### Bloque de aluminio → 9 lingotes (sin forma)

![Bloque a lingotes](docs/recipes/aluminum_ingots_from_block.png)

**Materiales necesarios**
- 1 × Bloque de aluminio

**Resultado:** 9 Lingotes de aluminio.

#### Bloque de aluminio → 5 papeles (cortapiedras)

![Cortapiedras](docs/recipes/stonecutter.png)

**Materiales necesarios**
- 1 × Bloque de aluminio (en la cortapiedras)

**Resultado:** 5 Papeles de aluminio. Es más barato que hacerlo con lingotes: 5 papeles por 9 lingotes, en lugar de 4,5.

### Bola

#### Bola de aluminio (sin forma)

![Receta de la bola](docs/recipes/aluminum_ball.png)

**Materiales necesarios**
- 9 × Papel de aluminio (en cualquier posición de la mesa)

**Resultado:** 1 Bola de aluminio de 9 hojas.

> En materiales en bruto son 18 lingotes de aluminio (9 papeles × 2 lingotes), o 2 bloques de aluminio en la cortapiedras (10 papeles).

#### Fusionar bolas (yunque)

![Yunque](docs/recipes/anvil.png)

**Materiales necesarios**
- 2 × Bola de aluminio (cualquier tamaño)
- Niveles de experiencia: `2 + hojas/50` (máximo 39)

**Resultado:** 1 Bola de aluminio con las hojas de las dos sumadas (máximo 2000).

### Fútbol

#### Portería de fútbol

![Receta de la portería](docs/recipes/goal.png)

**Materiales necesarios**
- 5 × Lingote de aluminio
- 3 × Hilo

**Resultado:** 1 Portería de fútbol.

#### Cono (sin forma)

![Receta del cono](docs/recipes/cone.png)

**Materiales necesarios**
- 1 × Tinte naranja
- 1 × Lana blanca

**Resultado:** 4 Conos. Con un solo tinte y una lana tienes 4 conos de entrenamiento.

---

## Aluminio: dónde encontrarlo

- **Mena de aluminio** y **Mena de aluminio de pizarra**, en todos los biomas del Overworld, con una frecuencia
  parecida a la del hierro (se generan en tres capas: alta Y 80–384, media Y −24–56 y toda la altura hasta Y 72 en
  vetas pequeñas).
- Se mina con **pico de piedra** o mejor.
- Suelta **Aluminio en bruto** (afecta Fortuna). Con **Toque de seda** suelta la mena.

---

## Cómo se juega con la bola

### Conseguir papel
Cómete bocatas y durums (el papel vuelve al inventario) o fabrícalo con lingotes. Ver [Crafteos](#crafteos).

### Colocar, recoger y lanzar
- **Colocar**: click derecho sobre un bloque con la bola en la mano.
- **Recoger**: click derecho sobre la bola **sin papel de aluminio en la mano**. Se guarda con todas sus hojas.
- **Lanzar**: mantén click derecho con la bola en la mano para cargar y suelta. Cuanto más cargas, más fuerte sale.
  Cuanto más grande es, más cuesta lanzarla.

### Agrandar
Click derecho sobre la bola con **papel de aluminio** en la mano: +1 hoja. En creativo no se gasta.

**Cuanto más grande, más papel cuesta cada hoja** (hasta 500 hojas), y luego baja de nuevo:

| Hojas | Papel por hoja nueva |
|---|---|
| 9 | 1 |
| 100 | 4 |
| 300 | 11 |
| 500 | 17 (el más caro) |
| 1000 | 12 |
| 1500 | ≈ 6 |
| 2000 | máximo, no crece más |

Para ahorrar papel, **funde varias bolas en el yunque** (el papel ya invertido se suma).

### Tamaño

Hasta 500 hojas crece despacio (el volumen es proporcional al papel); a partir de ahí, más rápido hasta 8 bloques:

| Hojas | Diámetro |
|---|---|
| 9 | 0.12 bloques |
| 100 | 0.26 |
| 500 | 0.46 |
| 2000 (máx.) | 8 bloques |

Las bolas de menos de 1 bloque no son sólidas (se empujan al pasar por encima); a partir de ≈ 610 hojas ya tienen colisión.

### Moverla
- **Pégale** y sale despedida en dirección contraria. Cuanto más grande, menos sale.
- Si **caminas contra ella**, también la empujas.
- Tiene gravedad, rozamiento y rueda de forma visible. Es inmune a lava, vacío, etc.

### Portería y conos
- La **portería de fútbol 11** mide 7,32 m × 2,44 m con 2 m de fondo. Se coloca con click derecho sobre el suelo y
  la boca mira hacia ti.
- Si la bola entra por la boca y pasa la línea: **¡¡GOOOL!!** (mensaje en la action bar, fuegos artificiales y sonido).
- Se puede atravesar la red a pie. **Pégale al larguero** para recogerla; si hay una bola dentro, el golpe va a la bola.
  Para girarla, recógela y vuelve a colocarla.
- Los **conos** son bloques de entrenamiento para hacer eslálom.

---

## Pestaña de creativo

**The Ball** contiene todo: bocata, durum, menas, portería, cono, bruto, lingote, bloque, papel y bolas de ejemplo
(9, 100, 500, 1000, 1500, 2000 hojas).

## Ids

| Item / bloque / entidad | ID |
|---|---|
| Bocata | `theball:bocata` |
| Durum | `theball:durum` |
| Papel de aluminio | `theball:aluminum_foil` |
| Bola de aluminio (item y entidad) | `theball:aluminum_ball` |
| Lingote de aluminio | `theball:aluminum_ingot` |
| Aluminio en bruto | `theball:raw_aluminum` |
| Bloque de aluminio | `theball:aluminum_block` |
| Mena de aluminio | `theball:aluminum_ore` |
| Mena de aluminio de pizarra | `theball:deepslate_aluminum_ore` |
| Portería (item y entidad) | `theball:goal` |
| Cono | `theball:cone` |

Dar una bola de tamaño concreto: `/give @s theball:aluminum_ball[theball:sheets=100]`

Idiomas: `es_es` y `en_us`.

---

## Para desarrolladores

```
src/main/java/com/theball/       TheBallMod, registry/, item/, entity/, block/, mixin/AnvilMenuMixin
src/client/java/com/theball/client/   TheBallClient y renderers (bola, portería)
src/main/resources/              fabric.mod.json, assets/theball/{lang,models,items,textures}, data/theball/{recipe,loot_table,worldgen}
tools/generate_textures.py       regenera las texturas (python3 + Pillow)
tools/generate_recipe_images.py  regenera las imágenes de crafteo de docs/recipes/ (usa el jar de Minecraft para las texturas vanilla)
docs/CONTEXTO.md                 notas de diseño originales (algunas cifras han cambiado, esto manda)
```

Cosas que se cambian fácil:
- Tamaño máximo (`MAX_SHEETS`), fórmula de diámetro (`diameterFor`) y coste de papel (`foilCostFor`) en
  `AluminumBallEntity`.
- Rozamiento, rebotes y fuerza del golpe en `tick()` / `hurtServer()`.
- Carnes del bocata y durum, en `data/theball/recipe/bocata.json` y `durum.json`.
- Frecuencia del aluminio, en `data/theball/worldgen/placed_feature/`.
