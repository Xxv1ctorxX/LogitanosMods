# Logitanos Mods

Mods del servidor de Minecraft **Logitanos**. Todos son para **Fabric** y se compilan desde este repo.

## Mods

| Mod | Qué es | Minecraft | Fabric Loader | Fabric API | Java |
|---|---|---|---|---|---|
| [**The Ball**](mods/TheBall/README.md) | Come un bocata, recupera el papel de aluminio y fabrica una bola gigante que puedes empujar y agrandar. Con aluminio, durum, portería y conos. | 26.3 | ≥ 0.20.1 | 0.165.0+26.3 | 25 |

## Estructura del repo

```
LogitanosMods/
├─ README.md                  este archivo
├─ .github/workflows/build.yml   compila los mods en GitHub y publica los .jar
└─ mods/
   └─ TheBall/                un mod = un proyecto Gradle independiente
      ├─ README.md            instrucciones, crafteos y mecánicas
      ├─ docs/                notas de diseño
      ├─ src/                 código y recursos
      └─ build.gradle ...
```

Cada mod vive en su propia carpeta dentro de `mods/` con su propio `build.gradle` y su propio README.

## Instalar en el servidor

1. Servidor **Fabric** para la versión de Minecraft del mod (ver tabla de arriba: Minecraft, Loader y Java).
2. Descarga el `.jar` del mod (pestaña **Releases**, o **Actions → Build → Artifacts**).
3. Mételo en `mods/` del servidor junto con [Fabric API](https://modrinth.com/mod/fabric-api) (la versión indicada en la tabla).
4. **Los jugadores necesitan los mismos `.jar`** en su carpeta `mods/` (los mods añaden entidades con render propio).

## Compilar

Necesitas JDK 25. Desde la carpeta del mod:

```bash
cd mods/TheBall
./gradlew build        # Windows: gradlew.bat build
```

El `.jar` queda en `mods/TheBall/build/libs/` (ignora el que acaba en `-sources`).

## Añadir un mod nuevo

1. Crea `mods/NombreDelMod/` con su proyecto Gradle (puedes copiar `TheBall` como plantilla).
2. Escribe su `README.md` con instrucciones y crafteos (las imágenes de crafteo se generan con un script; ver
   [CLAUDE.md](CLAUDE.md#cómo-se-generan-las-imágenes-de-crafteo)).
3. Añade un job en `.github/workflows/build.yml` copiando el de `theball` (cambia la ruta).
4. Añádelo a la tabla de arriba.

## Releases

Cada mod se publica con un tag `<mod>-v<versión>`:

```bash
git tag theball-v1.0.0
git push --tags
```

GitHub compila y adjunta el `.jar` a la release.
