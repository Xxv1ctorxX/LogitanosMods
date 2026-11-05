# Revisión técnica de TheSlave

5 de noviembre de 2026. Ampliación del mod existente para Minecraft 26.3/Fabric/Java 25. Revisión coordinada de lógica y persistencia, mundo/IA y animaciones con tres agentes, integrada y comprobada por el agente principal.

## Comportamiento incorporado

- Comercio personal, tres vínculos máximos, contratos reservados, costes 2/8/14 de cada alma y entrega de contrato, ofrenda y látigo. La invocación conserva el látigo y añade el lecho personal al terminar.
- Lecho de Ceniza de dos bloques ligado al individuo. Al morir se vuelve tumba; equipo y mochila caen donde murió. El alma se recoge una vez y se lleva con el tributo al altar nocturno del Overworld. La tumba puede permanecer en casa en otra dimensión. Retorno conserva identidad y transforma la tumba de nuevo en lecho.
- Entrega durable de lechos a vínculos existentes de versiones anteriores, con reintento al liberar espacio. Compatibilidad con lápidas y generaciones antiguas.
- Provisiones en cantidad, consumo automático, esfuerzo, descanso de cinco minutos, especialización fusionada de dos minutos, reposo pasivo y encargo conservado durante pausas/cargas. Regreso al hogar con navegación y rescate seguro cuando sea necesario.
- Cuatro secuencias distintas, objetos sobre altar, almas, cuerpos temporales, gestos de La Muerte, portal opaco con recorte geométrico, cadenas y fusión por mitades con costura. La previsualización muestra aspecto y atributos y permite intercambiar mitades.
- Enclaves con terreno validado, búsqueda limitada por brújula, comprobación física de entradas indexadas y descarte de huérfanas tras reiniciar.
- Recursos nativos pixel art y modelos JSON revisados. Los nueve recursos protegidos del látigo y contrato mantienen sus hashes originales.

## Verificación ejecutada

La revisión posterior de seguridad y recursos está detallada en [security_performance_review.md](security_performance_review.md). Incorpora límites de escaneo compartidos, preparación incremental de recetas, fabricación nativa, herramientas equipadas, protección de transferencias de objetos y compactación del historial en RAM.

`gradlew.bat --offline --no-daemon build previewLore` compila lógica común y cliente, ejecuta `check` y produce una hoja geométrica de los modelos sin ventana de juego.

Las tareas integradas son `verifyWhip`, `verifyLore`, `verifyLogic`, `verifyRitual`, `verifyRitualDurable`, `verifyEntityDurability`, `verifyDeathDurability`, `verifyHomeDurability`, `verifyWorld`, `verifyTrabajoSeguro`, `verifyFabricacion` y `verifyKnowledgeBudget`. Incluyen pruebas del motor usado por producción, escritura de archivos, fallos de guardado, fronteras de caída, recuperación repetida, reconexión, generación de almas, herencia, mantenimiento, API local de mixins, UV y geometría.

`python -I -m unittest discover -s tools -p 'test_*assets.py' -v`: nueve pruebas de recursos, incluidos los hashes protegidos y las 32 variantes del lecho. Las previsualizaciones son archivos en `build/previews/`; no son capturas dentro de Minecraft.

## Límites de la evidencia

No se ha abierto un cliente, servidor, partida ni ventana de videojuego durante esta revisión de código. La inicialización de registros y parsers en pruebas Java no crea mundos ni inicia un servidor. La carpeta `run/` de intentos anteriores fue eliminada, sin aceptar la EULA. No se detectaron nuevas instalaciones de Minecraft fuera del repositorio; la caché de compilación existente se conserva.

Los contratos de mixins se contrastaron contra los JAR locales; sus transformaciones y la integración completa con un mundo necesitan validación en un entorno de juego autorizado. Tampoco se ha validado composición GPU, shaders de terceros, rendimiento con terreno real ni convivencia con todos los mods de protección.

La recuperación implementa intención durable, identidad estable, recibos y custodia con confirmaciones de archivo. Las pruebas cubren las fronteras descritas; no prometen tolerancia universal a hardware defectuoso, corrupción arbitraria de archivos o restauración parcial de copias del mundo.

El audio definitivo queda aplazado por decisión del usuario. No se ha utilizado ElevenLabs; los sonidos vanilla existentes son provisionales.
