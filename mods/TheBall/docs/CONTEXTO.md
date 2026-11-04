# TheBall — Mod de Minecraft: la bola de papel de aluminio

> **Documento histórico de diseño (versión inicial).** Está desfasado: el mod usa ahora MC 26.3 / Java 25 y tiene
> aluminio, durum, portería, conos y otras cifras. Para lo actual, mira el [README](../README.md).

## Idea
Mod sencillo y de coña. Te comes un **bocata**, te devuelve **papel de aluminio**, y con el papel
fabricas una **bola de aluminio** que puedes ir haciendo más grande, empujar y recoger.

## Funcionalidad

### Items
| Item | ID | Descripción |
|---|---|---|
| Bocata | `theball:bocata` | Comida. Al terminar de comerlo, el jugador recibe 1 papel de aluminio (si no le cabe en el inventario, se le cae al suelo). |
| Papel de aluminio | `theball:aluminum_foil` | Ingrediente. Sirve para crear la bola y para agrandarla. |
| Bola de aluminio | `theball:aluminum_ball` | Versión en item de la bola. Guarda su tamaño. Al usarla sobre un bloque se coloca la entidad bola. |

### Recetas
- **Bola de aluminio**: 9 papeles de aluminio en la mesa de crafteo (sin forma) -> 1 bola de aluminio (tamaño inicial 9).
- **Bocata** (propuesta mía, el usuario no la especificó; cambiar si no gusta): 1 pan + 1 chuleta de cerdo cocinada, sin forma.

### La bola (entidad `theball:aluminum_ball`)
Es una **entidad**, no un bloque, porque tiene que moverse y poder recogerse.

- **Crecer**: click derecho sobre la bola con papel de aluminio en la mano -> +1 hoja, gasta 1 papel (en creativo no lo gasta). Sonido de crujido. La hitbox y el modelo crecen.
- **Mover**: al pegarle (ataque) sale empujada en dirección contraria al atacante. Cuanto más grande, menos sale despedida. Tiene gravedad, rozamiento y rueda de forma visible. Si el jugador camina contra ella, también la empuja.
- **Recoger**: shift + click derecho (sin papel de aluminio en la mano) -> pasa a inventario como item `aluminum_ball` conservando su tamaño. Se puede volver a colocar con click derecho sobre un bloque.
- **Solidez**: tiene colisión, se puede apuntar a ella y no se puede atravesar.
- **Persistencia**: el tamaño se guarda en el mundo (NBT) y en el item (data component `theball:sheets`).
- **Límite**: tamaño máximo (por defecto 400 hojas, ~4 bloques de diámetro). Al llegar, avisa en la action bar.

### Fórmula de tamaño (ajustable)
`diámetro = clamp(0.2 * sqrt(hojas), 0.3, 4.0)`
9 hojas = 0.6 bloques, 100 hojas = 2 bloques, 400 hojas = 4 bloques.

### Fuera de alcance (por ahora)
Física realista (rebotes complejos), daño a mobs, que se pueda montar, sonidos propios.

## Decisiones técnicas
- **Loader**: Fabric. **Minecraft**: 1.21.1. **Java**: 21. **Mappings**: oficiales de Mojang (`loom.officialMojangMappings()`).
- Plantilla base: `FabricMC/fabric-example-mod` (rama 1.21), con `splitEnvironmentSourceSets()`; el código de cliente (renderer) va en `src/client`.
- La entidad extiende `Entity` (no `LivingEntity`). Tamaño sincronizado con `SynchedEntityData` y dimensiones dinámicas con `getDimensions` + `refreshDimensions()`.
- Se registra con `FabricEntityTypeBuilder` (`trackedUpdateRate(1)`, `forceTrackedVelocityUpdates(true)`, `fireImmune()`) para que el movimiento se vea fluido.
- Física simple en `tick()`: gravedad, `move()`, rozamiento (más fuerte en suelo), algo de frenado en agua. Se simula en cliente y servidor.
- Tamaño del item: data component propio `theball:sheets` (Integer, valor por defecto 9, `maxStackSize` 1).
- Render: esfera generada por código (malla lat/long) con textura de papel arrugado; se escala con el diámetro y rota según lo que rueda (cuaternión acumulado).
- Texturas 16x16 de los items y textura de la esfera (128x64) generadas por script, en estilo pixel art.
- Idiomas: `es_es` y `en_us`. Pestaña propia de creativo "The Ball".

## Estructura prevista
```
TheBall/
├─ build.gradle, gradle.properties, settings.gradle, gradlew(.bat)
└─ src/
   ├─ main/java/com/theball/
   │  ├─ TheBallMod.java            (ModInitializer)
   │  ├─ registry/ModItems.java, ModEntities.java, ModComponents.java
   │  ├─ item/BocataItem.java, AluminumBallItem.java
   │  └─ entity/AluminumBallEntity.java
   ├─ main/resources/
   │  ├─ fabric.mod.json
   │  ├─ assets/theball/{lang,models/item,textures/item,textures/entity}
   │  └─ data/theball/recipe/{aluminum_ball,bocata}.json     (OJO: carpeta `recipe` en singular desde 1.21)
   └─ client/java/com/theball/client/
      ├─ TheBallClient.java
      └─ AluminumBallRenderer.java
```

## Estado
- [x] Idea y mecánicas definidas (este documento)
- [ ] Proyecto Gradle + código + recursos
- [ ] Compilar (`gradlew build`) y probar en el juego

## Notas del entorno
- En el entorno cloud de Claude no hay salida a Maven/Mojang/Fabric, así que **no se puede compilar ahí**. Hay que compilar en el PC del usuario.
- El PC del usuario tiene **Java 11**; hace falta instalar **JDK 21** (por ejemplo Temurin) antes de compilar.
- Para probar: `gradlew runClient` (abre Minecraft de desarrollo) o `gradlew build` y meter el `.jar` de `build/libs` en la carpeta `mods` junto con Fabric API.
- El código se escribe sin poder compilar, así que es probable que salgan pequeños errores de nombres de API en la primera compilación: se pegan aquí y se corrigen.
