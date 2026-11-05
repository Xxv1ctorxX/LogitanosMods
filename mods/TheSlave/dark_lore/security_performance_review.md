# Seguridad, estabilidad y recursos de TheSlave

Revisión del 5 de noviembre de 2026, con agentes de lógica/persistencia, mundo/IA y rituales/animaciones. Implementación integrada sobre el mod existente; el lore y los precios acordados siguen siendo la referencia.

## Fallos corregidos

| Riesgo o fallo | Cambio aplicado |
|---|---|
| Botín concedido aunque la rotura fallara | Permiso, cálculo, rotura confirmada y entrega en ese orden; desgaste solo tras éxito |
| Sustituir el bloque mientras se picaba | Se revalidan estado exacto, alcance, visibilidad, dureza, contenedores, fluidos, lava y herramienta |
| Saltarse protección al desconectarse el dueño | Con protecciones activas, la minería espera al propietario conectado en la misma dimensión; respeta spawn y callbacks Fabric |
| Fabricación que consumía parte de los ingredientes antes de fallar | Copia de mochila y confirmación completa de la tanda usando la receta nativa |
| Pérdida de componentes y recipientes | Resultados, remainders y combustibles resueltos mediante las recetas y componentes nativos |
| Desperdicio de combustible y fundiciones incompletas dadas por terminadas | Crédito persistente, seguimiento de tandas reales y conservación de energía en la fusión |
| Muestra gratuita al repetir un encargo | Se retira una muestra real del inventario; no se consumen piezas encantadas o con nombre |
| Pérdida de muestra si el dueño salía mientras se esperaba la orden | Se conserva con el esclavo y puede cobrarse después; temporizadores de recepción persistentes |
| Guardado distinto de mochila y jugador al entregar o cobrar | PREP con copias, custodia, confirmación real de playerdata y entidades, COMMIT y recuperación idempotente |
| Excedente de cobro tirado al suelo | Permanece en la mochila o referencia para cobrarlo al hacer espacio |
| Paquetes masivos, jugador obsoleto o acciones remotas | Límites antes de encolar y revalidación de conexión, propietario, distancia, custodia y chunks cargados |
| Repetir compras/devoluciones antiguas | Recibos compactos con identidad y huella, sin volver a pagar ni reservar |
| Confirmación de entidad anterior a un guardado concurrente | Se encadena una nueva escritura para capturar el actor recién incorporado |
| Un COMMIT antiguo liberaba la custodia de una operación posterior | La liberación comprueba el UUID de operación; los originales retirados siguen teniendo prioridad |
| Escenas simultáneas que se pisaban en el HUD | Prioridad estable por propietario, distancia e identidad del altar; reloj de escena monotónico |

## Presupuestos de servidor

- Escaneo de minería compartido: **1.024 posiciones por tick** y **128 por trabajador** por defecto. Turnos equitativos, cercanía primero y cursor de memoria constante. Máximo de 64 candidatos y 512 posiciones vetadas por trabajador. Solo terreno cargado.
- Configuración acotada: radio de búsqueda 1–32; presupuesto global 128–4.096; individual 16–256. El límite de vínculos permanece fijo en tres.
- Índice de conocimiento preparado por lotes compartidos: hasta 32 recetas, 8 bloques, 8 fuentes o 128 nodos, según fase, con objetivo temporal de 2 ms entre unidades. No se crea por cada NPC. Una tabla o receta de otro mod puede exceder ese objetivo en una sola unidad.
- Fabricación y fundición: hasta 16 tandas por acción. Planificación acotada en cantidad, profundidad, nodos y pasos. Las herramientas equipadas participan en la extracción.
- Hogares, tumbas y operaciones indexados por identidad y chunk. Los snapshots confirmados no quedan retenidos en RAM. El historial comercial confirmado conserva recibos pequeños y se consulta por operación.
- Brújula: caché limitada a 128 jugadores, un segundo de reutilización de destino y verificación física del enclave. Bosque: 64 muestras deterministas por región. La infección no se propaga.
- Menos caras duplicadas y asignaciones de render; las grietas solo se anuncian al cambiar de etapa. La presentación permanece en el cliente.

Estos límites reducen trabajo y memoria del mod. No constituyen una medida de RAM total, MSPT o FPS de un servidor real. La confirmación de operaciones escribe y sincroniza archivos: el rendimiento final también depende del disco.

## Compatibilidad y validación

Se conservan IDs y ordinales existentes. Los campos nuevos de temporizadores, energía y custodia tienen valores compatibles al faltar en partidas antiguas. Las operaciones PREP conservan información completa de recuperación; los COMMIT no vuelven a aplicar inventarios antiguos de jugadores. Los vínculos, reservas, difuntos y NPC descargados siguen contando para el límite.

`gradlew.bat --offline --no-daemon build` integra pruebas de economía, almas/brújula, migraciones, recuperación, hogares, muerte, rituales, red, trabajo, recetas nativas y geometría. Las pruebas de fronteras de caída usan el motor de producción con participantes y futuros controlados; las de archivos provocan fallos reales de escritura. Las pruebas de recursos verifican nueve hashes protegidos y la coherencia de JSON, texturas y modelos.

Resultado final: `build previewLore` pasó; después del último ajuste de poda pasaron de nuevo `jar sourcesJar verifyLogic`. Dentro de `verifyLogic`, `TransferenciasTest` ejecutó 62 comprobaciones sobre el motor real, ACK, reconexiones, recuperación, prioridad de custodia, inserción y poda con archivos. También pasaron `verifyFabricacion` (19 casos), `verifyKnowledgeBudget` (lotes, convergencia y ciclos), `verifyTrabajoSeguro` (cursor, equidad, presupuesto y orden de extracción) y las demás tareas de `check`. Las nueve pruebas Python de recursos pasaron y los nueve assets protegidos mantienen su hash.

El almacenamiento `theslave_transferencias/` conserva el último COMMIT por actor más los PREP necesarios. Solo retiene PREP completos en RAM. Un historial confirmado anterior se retira después de comprobar el recibo nuevo y su índice; interrumpir esa limpieza conserva evidencia y se reintenta al arrancar.

## Límites y trabajo pendiente

- **No se ha arrancado Minecraft ni un servidor dedicado durante esta revisión.** Es una restricción explícita de este equipo. La compilación común/cliente y las firmas locales de mixins se comprueban; su aplicación real, las rutas de IA, la generación de terreno, la composición GPU y la sincronización con clientes reales necesitan un entorno autorizado.
- Falta medir RAM/MSPT/FPS con el número real de jugadores, la semilla y los demás mods del servidor. Los callbacks Fabric permiten a las protecciones intervenir; no se afirma compatibilidad probada con todos los sistemas de claims.
- Las garantías de recuperación se limitan a las operaciones y escenarios verificados. No se ofrece atomicidad universal entre archivos vanilla de terreno, entidades y jugadores, ni tolerancia a corrupción arbitraria o restauraciones parciales del mundo.
- **Audio definitivo aplazado para esta tarde por decisión del usuario.** Los sonidos vanilla existentes siguen siendo provisionales. No se ha usado ElevenLabs ni añadido conexiones o credenciales de audio al código o al JAR.
- Los assets de látigo y contrato permanecen intactos y protegidos por hashes.
