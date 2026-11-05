# Instrucciones de TheSlave

- Ampliar el mod existente conforme a `dark_lore/new_lore.md`; no cambiar el lore para justificar limitaciones de la implementación.
- Con cada edición de código, compilar y ejecutar las pruebas apropiadas.
- No abrir Minecraft, ventanas de juegos, partidas locales ni servidores de prueba en este equipo. No ejecutar `runClient`, `runServer` ni aceptar una EULA. Solo pruebas de código y simulaciones sin arrancar el juego.
- El audio se hará en una fase posterior. No usar ElevenLabs ni otras APIs de audio en esta fase.
- No modificar los assets de látigo ni contrato. Sus hashes están en `tools/protected_assets.json` y se verifican con `tools/test_lore_assets.py`.
- No ejecutar el generador base completo `generate_assets.py`: podría sobrescribir recursos protegidos. El generador de lore debe respetarlos.
- Mantener cambios ajenos y no instalar juegos ni recursos de juego fuera del repositorio.
