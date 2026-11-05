# Validación de LudopathyRoulette 1.0.0

Esta entrega no ha instalado ni arrancado Minecraft, cliente o servidor. No se ha copiado a ninguna carpeta de juego. Las bibliotecas de Minecraft utilizadas son dependencias de compilación ya disponibles en Gradle.

## Comprobaciones ejecutables

`gradlew build collectJar` compila fuentes comunes y cliente con Java 25 y ejecuta `verifyCore`. Este programa no arranca el juego. Su informe queda en `build/reports/core-tests.txt`; al directorio de entrega solo se copia el JAR instalable.

- Fronteras del sorteo entero y rechazo de pesos inválidos.
- Un millón de sorteos deterministas; contraste chi cuadrado con tres grados de libertad y umbral 20 para esa semilla.
- Conversión porcentual con restos mayores y redondeo de recargos.
- Diez mil combinaciones por rueda, destino interior, sectores extremos, pasos temporales y saltos de frames.
- Escritura atómica UTF-8, reapertura de diario y clasificación conservadora de cada fase no liquidada ante parada inesperada.
- Parada limpia conserva obligaciones; parada inesperada no las ejecuta automáticamente.
- Sintaxis JSON, recetas e imágenes generadas.

Se inspeccionan también el empaquetado del JAR, puntos de entrada, presencia de clases cliente, recursos y firmas de los métodos de mixin en las dependencias de 26.3. La compilación no equivale a una prueba de aplicación de mixins.

## Pendiente en un equipo con el juego

- Conexión real y rechazo de cliente incompatible; carga dedicada sin clases cliente.
- Interacción de puerta en las cuatro orientaciones; retirada, creativo, explosiones y pistones.
- Edición concurrente, revocación de permisos y visualización a distintas escalas GUI.
- Cobro de stacks con componentes, XP fraccionaria, inventario lleno y entrega parcial.
- Tótem, muerte, reaparecer, efectos e interacción con otros mods.
- Aplicación real de mixins para botín/XP y caducidad de criaturas.
- Latencia, TPS bajos, ticks congelados, espectadores y salida/carga de chunks.
- Render, clipping, audio/subtítulos y legibilidad en sectores pequeños.
- Perfil de CPU, GPU, RAM, red y disco en escenarios de carga.
- Fallos reales entre guardado vanilla y diario: la suite valida la política de revisión, no demuestra atomicidad de archivos externos.

## Alcance de esta implementación respecto al plan

Las dos máquinas, editor, muestras servidor, juego, castigos, comandos, buzón y diario están implementados. La GUI dibuja una rueda con profundidad sugerida y casino en proyección elíptica; el bloque utiliza geometría 3D. No se afirma una escena 3D de UI equivalente a una cámara de mundo. El audio usa eventos vanilla reutilizados; no hay grabaciones externas.

La política de recuperación es conservadora: cualquier obligación incompleta tras parada inesperada exige intervención; no hay reconciliación automática del inventario vanilla. Las configuraciones viven en un registro mundial con límite 4.096; no se libera su memoria al descargar el chunk. Auditoría auxiliar limitada a dos logs, no retención por días. Los textos de la GUI están en español; objetos, muerte y subtítulos tienen traducciones española e inglesa.

El diseño de stock finito, caja de recaudación, economía externa, física libre y jackpot progresivo sigue fuera de v1. Estos puntos no se presentan como funcionalidades implementadas.
