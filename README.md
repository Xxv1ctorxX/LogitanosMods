# Logitanos Mods

Mods del servidor de Minecraft **Logitanos**. Todos son para **Fabric** y se compilan en local desde este repo.

## Mods

| Mod | Versión | Qué es | Minecraft | Fabric Loader | Fabric API | Java |
|---|---|---|---|---|---|---|
| [**The Ball**](mods/TheBall/README.md) | 1.1.0 | Come un bocata, recupera el papel de aluminio y fabrica una bola gigante que puedes empujar y agrandar. Con aluminio, durum, portería y conos. Desde la 1.1: el **Trono**, la **Reina de Aluminio**, XP por hojas, armadura, Corona, Cetro y ¡Dios del aluminio! | 26.3 | ≥ 0.20.1 | 0.165.0+26.3 | 25 |
| [**LiveCrops**](mods/LiveCrops/README.md) | 1.0.0 | Cultivos sin jugadores cerca: 40 % con jugadores conectados y 5 % con servidor vacío, sin cargar chunks. Solo servidor. | 26.3 | ≥ 0.20.1 | 0.165.0+26.3 | 25 |
| [**The Slave**](mods/TheSlave/README.md) | 1.0.0 | Un esclavo con contrato que mina, craftea y funde lo que le pidas con gestos; se compra a Notarios de Ceniza, se invoca de noche en un Altar de Ceniza, y si muere deja una tumba y solo vuelve con un tributo; dos pueden fundirse. | 26.3 | ≥ 0.20.1 | 0.165.0+26.3 | 25 |
| [**Ludopathy Roulette**](mods/LudopathyRoulette/README.md) | 1.0.0 | Ruleta de fortuna y casino, premios y castigos configurables, puerta protegida y buzón persistente. Cliente y servidor. | 26.3 | ≥ 0.20.1 | 0.165.0+26.3 | 25 |

## Estructura del repo

```
LogitanosMods/
├─ README.md                  este archivo
├─ jar/                       los .jar ya compilados, uno por mod
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
2. Coge el `.jar` del mod de `jar/<NombreDelMod>/` en este repo, o compílalo tú (ver abajo).
3. Mételo en `mods/` del servidor junto con [Fabric API](https://modrinth.com/mod/fabric-api) (la versión indicada en la tabla).
4. **TheBall requiere el mod también en los clientes. LiveCrops funciona solo en el servidor** y los clientes no necesitan instalarlo. Consulta el README de cada mod.

## Compilar

Necesitas JDK 25. Desde la carpeta del mod:

```bash
cd mods/TheBall
./gradlew build        # Windows: gradlew.bat build
```

El `.jar` queda en `mods/TheBall/build/libs/` (ignora el que acaba en `-sources`).

En Windows, cada mod incluye un `compile.bat`: ejecútalo desde cualquier carpeta
para compilar, pasar sus verificaciones y copiar el `.jar` instalable a
`jar/<NombreDelMod>/` en la raíz del repositorio. Por ejemplo:

```powershell
.\mods\TheBall\compile.bat
```

Admite opciones de Gradle, como `compile.bat --offline` si las dependencias ya están en caché.

Para LudopathyRoulette: `mods/LudopathyRoulette/compilar.ps1 -Offline` compila y verifica con las dependencias cacheadas; deja la entrega en `jar/LudopathyRoulette/`, sin instalar ni arrancar Minecraft.

## Añadir un mod nuevo

1. Crea `mods/NombreDelMod/` con su proyecto Gradle (puedes copiar `TheBall` como plantilla).
2. Escribe su `README.md` con instrucciones y crafteos (las imágenes de crafteo se generan con un script; ver
   [CLAUDE.md](CLAUDE.md#cómo-se-generan-las-imágenes-de-crafteo)).
3. Añádelo a la tabla de arriba y a `jar/` una vez compilado.

## Publicar una versión

No hay compilación automática en la nube. Cada mod se compila en local y se versiona con un tag `<mod>-v<versión>`:

```bash
cd mods/TheBall
gradlew.bat build
git tag theball-v1.0.0
git push --tags
```

Si en algún momento quieres adjuntar el `.jar` a una release de GitHub, se sube a mano desde la pestaña **Releases**.
