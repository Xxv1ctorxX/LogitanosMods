package com.livecrops;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SplittableRandom;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Pruebas sin cliente, mundo, red ni descargas adicionales. */
public final class CoreTests {
    private static int checks;
    private static final StringBuilder report = new StringBuilder();
    public static void main(String[] args) throws Exception {
        run("Tasas 100/40/5 y cero", CoreTests::rates);
        run("Tramos atendidos y desatendidos sin duplicar", CoreTests::ledger);
        run("100000 transiciones con memoria constante", CoreTests::transitions);
        run("Poisson truncada: límites y valores extremos", CoreTests::bounds);
        run("Distribución Poisson: media, varianza y cola", CoreTests::distribution);
        run("Tallos: espacio libre y ausencia de fruta acumulada", CoreTests::stems);
        run("Arrays: altas, duplicados persistidos y eliminación", CoreTests::entries);
        run("Codec: instantánea independiente y deuda parcial", CoreTests::codec);
        run("Codec: corrupción, versiones y tipos inválidos", CoreTests::corruption);
        run("Recuperación de reloj futuro", CoreTests::futureClock);
        run("Configuración integrada: copia, codec y guardado con el mundo", CoreTests::configuration);
        run("Configuración: valores inválidos y archivo corrupto", CoreTests::invalidConfiguration);
        run("Reloj persistente: revisiones tras desactivar y readmitir", CoreTests::clockRevisions);
        run("Hooks: firmas reales en Minecraft 26.3", CoreTests::mixinTargets);
        run("Hook vanilla: el método observado ejecuta random ticks", CoreTests::vanillaCall);
        run("Sin cargas de chunks ni delta de reloj local por dimensión", CoreTests::noChunkLoading);
        run("Comandos: opciones tipadas y cultivo con namespace", CoreTests::commandOptions);
        run("Detección retroactiva: radios y coordenadas negativas", CoreTests::discoveryGeometry);
        run("Detección retroactiva: valores predeterminados y persistencia", CoreTests::discoveryDefaults);
        run("Cama/respawn: lectura sin activar ni consumir el bloque", CoreTests::respawnSource);
        Path out = Path.of("build/reports/core-tests.txt");
        Files.createDirectories(out.getParent());
        report.append("OK: ").append(checks).append(" grupos de comprobaciones.\n");
        Files.writeString(out, report.toString());
        System.out.print(report);
    }
    private interface Checked { void run() throws Exception; }
    private static void run(String name, Checked test) throws Exception {
        test.run(); checks++; report.append("PASS ").append(name).append('\n');
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Condición incumplida"); }
    private static void close(double actual, double expected, double tolerance) {
        if (Math.abs(actual - expected) > tolerance) throw new AssertionError(actual + " != " + expected);
    }
    private static void rejects(Checked operation) throws Exception {
        boolean failed = false;
        try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) { failed = true; }
        check(failed);
    }
    private static void rates() throws Exception {
        close(GrowthMath.increment(3, 0.4), 0.00029296875, 1e-15);
        close(GrowthMath.increment(3, 0.05), 0.00003662109375, 1e-15);
        close(GrowthMath.increment(0, 0.4), 0, 0);
        rejects(() -> GrowthMath.increment(3, Double.NaN));
        rejects(() -> GrowthMath.increment(3, 1.1));
        double mixed = 10000 * GrowthMath.increment(3, 0.4) + 10000 * GrowthMath.increment(3, 0.05);
        close(mixed, 3.2958984375, 1e-12);
    }
    private static void ledger() {
        DormantLedger ledger = new DormantLedger(0, 0);
        close(ledger.effective(10), 10, 0);
        ledger.attended(10, 11);
        close(ledger.effective(11), 10, 0);
        for (int i = 11; i < 100; i++) ledger.attended(i, i + 1);
        close(ledger.effective(100), 10, 0);
        close(ledger.effective(150), 60, 0);
        double birth = ledger.effective(140);
        close(GrowthMath.debt(ledger.effective(150), birth), 10, 0);
        close(GrowthMath.debt(45, birth), 0, 0);
        DormantLedger restored = new DormantLedger(ledger.accumulated(), ledger.anchor());
        close(restored.effective(200), ledger.effective(200), 0);
    }
    private static void transitions() {
        DormantLedger ledger = new DormantLedger(0, 0);
        SplittableRandom random = new SplittableRandom(482);
        double clock = 0, expected = 0;
        for (int i = 0; i < 100000; i++) {
            double before = clock;
            double step = random.nextBoolean() ? 0.4 : 0.05;
            clock += step;
            if (random.nextBoolean()) ledger.attended(before, clock);
            else expected += step;
            close(ledger.effective(clock), expected, 1e-7);
        }
    }
    private static void bounds() throws Exception {
        SplittableRandom random = new SplittableRandom(20);
        for (double lambda : new double[]{0, 0.001, 1, 7, 40, 1e6, Double.MAX_VALUE}) {
            for (int cap = 0; cap <= 8; cap++) for (int i = 0; i < 1000; i++) {
                int result = GrowthMath.sample(lambda, cap, random);
                check(result >= 0 && result <= cap);
                if (lambda == 0) check(result == 0);
            }
        }
        rejects(() -> GrowthMath.sample(Double.NaN, 7, random));
    }
    private static void distribution() {
        SplittableRandom random = new SplittableRandom(124987);
        int n = 200000;
        double lambda = 1.7, sum = 0, square = 0;
        int zero = 0, capped = 0;
        for (int i = 0; i < n; i++) {
            int result = GrowthMath.sample(lambda, 7, random);
            sum += result; square += result * result;
            if (result == 0) zero++;
            if (result == 7) capped++;
        }
        double probability = Math.exp(-lambda), expected = 0, expected2 = 0, cumulative = 0;
        for (int i = 0; i < 7; i++) {
            expected += i * probability; expected2 += i * i * probability;
            cumulative += probability; probability *= lambda / (i + 1);
        }
        expected += 7 * (1 - cumulative); expected2 += 49 * (1 - cumulative);
        close(sum / n, expected, 0.015);
        close(square / n - Math.pow(sum / n, 2), expected2 - expected * expected, 0.025);
        close(zero / (double) n, Math.exp(-lambda), 0.004);
        close(capped / (double) n, 1 - cumulative, 0.002);
    }
    private static void stems() {
        SplittableRandom random = new SplittableRandom(781);
        int fruits = 0, n = 100000;
        for (int i = 0; i < n; i++) fruits += GrowthMath.stemStages(5, 0.2, 0, 0.25, random);
        close(fruits / (double) n, -Math.expm1(-0.25), 0.005);
        for (int i = 0; i < 1000; i++) {
            check(GrowthMath.stemStages(1e6, 0.2, 7, 0, random) == 7);
            check(GrowthMath.stemStages(1e6, 0.2, 7, 1, random) == 8);
            check(GrowthMath.stemStages(0, 0.2, 7, 1, random) == 0);
        }
    }
    private static void entries() {
        CropChunkData data = new CropChunkData(100);
        for (int i = 0; i < 4096; i++) data.add(i, i % 9, 0, 0);
        check(data.size() == 4096);
        for (int i = 0; i < 4096; i++) check(data.find(i) >= 0);
        for (int i = 0; i < 4096; i++) data.remove(0);
        check(data.size() == 0);
    }
    private static void codec() {
        CropChunkData data = new CropChunkData(100);
        data.add(20, 3, 0, 0); data.add(30, 7, 5, 2);
        data.attended(110, 111);
        data.consume(0, 3);
        var json = CropChunkData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        CropChunkData restored = CropChunkData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        close(restored.effective(120), 19, 0);
        check(restored.size() == 2 && restored.position(1) == 30 && restored.kind(1) == 7);
        close(restored.consumed(0), 3, 0); close(restored.admitted(1), 2, 0);
        data.remove(0);
        check(restored.size() == 2);
        restored.consume(0, 2); close(restored.consumed(0), 3, 0);
    }
    private static void corruption() {
        for (String json : new String[]{"{}", "{\"version\":2,\"accumulated\":0,\"anchor\":0,\"crops\":[]}",
                "{\"version\":1,\"accumulated\":-1,\"anchor\":0,\"crops\":[]}",
                "{\"version\":1,\"accumulated\":0,\"anchor\":0,\"crops\":[{\"pos\":1,\"kind\":99,\"consumed\":0,\"admitted\":0}]}"}) {
            check(CropChunkData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent());
        }
    }
    private static void futureClock() {
        CropChunkData data = new CropChunkData(100);
        data.add(1, 0, 0, 0);
        check(data.repairFuture(50));
        close(data.effective(50), 0, 0);
        close(data.effective(60), 10, 0);
        check(!data.repairFuture(60));
    }
    private static void configuration() throws Exception {
        LiveCropsConfig config = new LiveCropsConfig();
        close(config.onlineRate, 0.4, 0); close(config.emptyServerRate, 0.05, 0);
        LiveCropsConfig copy = config.copy();
        copy.crops.put("minecraft:wheat", false);
        check(config.crops.get("minecraft:wheat"));
        copy.onlineRate = 0.3;
        var encoded = LiveCropsConfig.CODEC.encodeStart(JsonOps.INSTANCE, copy).getOrThrow();
        close(LiveCropsConfig.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().onlineRate, 0.3, 0);
        ServerSettings settings = new ServerSettings();
        check(!settings.isDirty());
        settings.update(copy);
        check(settings.isDirty());
        var saved = ServerSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        ServerSettings restored = ServerSettings.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        close(restored.config().onlineRate, 0.3, 0);
        check(!restored.config().crops.get("minecraft:wheat"));
        LiveCropsConfig invalid = restored.config();
        invalid.onlineRate = 2;
        rejects(() -> restored.update(invalid));
        close(restored.config().onlineRate, 0.3, 0);
        copy.onlineRate = 0.2;
        close(restored.config().onlineRate, 0.3, 0);
        for (String invalidJson : new String[]{"null", "{broken", "{\"onlineRate\":2}"}) {
            check(LiveCropsConfig.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(invalidJson)).error().isPresent());
        }
    }
    private static void invalidConfiguration() throws Exception {
        LiveCropsConfig config = new LiveCropsConfig();
        config.onlineRate = Double.NaN; rejects(config::validate);
        config = new LiveCropsConfig(); config.emptyServerRate = 0.9; rejects(config::validate);
        config = new LiveCropsConfig(); config.maxTrackedCropsPerChunk = 4097; rejects(config::validate);
        config = new LiveCropsConfig(); config.maxQueuedChunks = 0; rejects(config::validate);
        config = new LiveCropsConfig(); config.maxWorkMillisPerTick = Double.POSITIVE_INFINITY; rejects(config::validate);
        config = new LiveCropsConfig(); config.maxWorkMillisPerTick = 1e-20; rejects(config::validate);
        config = new LiveCropsConfig(); config.crops.put("example:unsupported", true); rejects(config::validate);
    }

    private static void clockRevisions() {
        GrowthClock clock = new GrowthClock();
        double old = clock.revision(0);
        check(!clock.invalidated(0, old));
        clock.invalidate(0);
        check(clock.invalidated(0, old));
        check(!clock.invalidated(0, clock.revision(0)));
        check(!clock.invalidated(1, clock.revision(1)));
        clock.advance(GrowthMath.increment(3, 0.4));
        close(clock.value(), GrowthMath.increment(3, 0.4), 0);
    }

    private static ClassReader bytecode(String name) throws Exception {
        try (var stream = CoreTests.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(stream != null);
            return new ClassReader(stream);
        }
    }

    private static void methodExists(String name, String method, String descriptor) throws Exception {
        int[] found = {0};
        bytecode(name).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String actualName, String actualDescriptor, String signature, String[] exceptions) {
                if (actualName.equals(method) && actualDescriptor.equals(descriptor)) found[0]++;
                return null;
            }
        }, ClassReader.SKIP_CODE);
        check(found[0] == 1);
    }

    private static void mixinTargets() throws Exception {
        String pos = "Lnet/minecraft/core/BlockPos;";
        String state = "Lnet/minecraft/world/level/block/state/BlockState;";
        methodExists("net/minecraft/server/level/ServerLevel", "tickChunk", "(Lnet/minecraft/world/level/chunk/LevelChunk;I)V");
        methodExists("net/minecraft/world/level/chunk/LevelChunk", "setBlockState", "(" + pos + state + "I)" + state);
        methodExists("net/minecraft/world/item/BlockItem", "place", "(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;");
        methodExists("net/minecraft/world/level/block/CropBlock", "getGrowthSpeed",
                "(Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/BlockGetter;" + pos + ")F");
        try (var stream = CoreTests.class.getClassLoader().getResourceAsStream("livecrops.mixins.json")) {
            check(stream != null);
            var manifest = JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            check(manifest.get("required").getAsBoolean());
            for (var mixin : manifest.getAsJsonArray("mixins")) {
                bytecode("com/livecrops/mixin/" + mixin.getAsString());
            }
        }
    }

    private static void vanillaCall() throws Exception {
        boolean[] randomTick = {false};
        bytecode("net/minecraft/server/level/ServerLevel").accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals("tickChunk")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                        if (owner.equals("net/minecraft/world/level/block/state/BlockState") && method.equals("randomTick")) randomTick[0] = true;
                    }
                };
            }
        }, 0);
        check(randomTick[0]);
    }

    private static void noChunkLoading() throws Exception {
        boolean[] effectiveTicks = {false};
        for (String name : new String[]{"GrowthEngine", "LiveCropsCommand"}) {
            bytecode("com/livecrops/" + name).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                            if (owner.startsWith("net/minecraft/")) {
                                check(!method.equals("getChunk") && !method.equals("getChunkAt") && !method.startsWith("getChunkFuture"));
                                check(!method.equals("addTicket") && !method.equals("addTicketWithRadius"));
                                check(!method.equals("getGameTime"));
                                if (method.equals("runsNormally")) effectiveTicks[0] = true;
                            }
                        }
                    };
                }
            }, 0);
        }
        check(effectiveTicks[0]);
    }

    private static void commandOptions() throws Exception {
        // Inspección de bytecode: no inicializa registros ni arranca Minecraft.
        boolean[] identifierParser = {false};
        bytecode("com/livecrops/LiveCropsCommand").accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals("configuration")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                        if (owner.equals("net/minecraft/commands/arguments/IdentifierArgument") && method.equals("id")) identifierParser[0] = true;
                        check(!owner.equals("com/mojang/brigadier/arguments/StringArgumentType") || !method.equals("word"));
                    }
                };
            }
        }, 0);
        check(identifierParser[0]);
        for (var field : LiveCropsConfig.class.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            check(field.getType() == boolean.class || field.getType() == int.class || field.getType() == double.class
                    || java.util.Map.class.isAssignableFrom(field.getType()));
        }
        var reader = new com.mojang.brigadier.StringReader("minecraft:wheat false");
        Object identifier = net.minecraft.commands.arguments.IdentifierArgument.id().parse(reader);
        check(identifier.toString().equals("minecraft:wheat"));
        check(reader.getRemaining().equals(" false"));
    }

    private static void discoveryGeometry() throws Exception {
        DiscoveryArea single = new DiscoveryArea(-1, -17, 0);
        check(single.candidateCount() == 1 && single.chunkX(0) == -1 && single.chunkZ(0) == -2);
        check(single.intersects(0));
        DiscoveryArea corner = new DiscoveryArea(15, 15, 1);
        check(corner.candidateCount() == 4);
        java.util.Set<String> hits = new java.util.HashSet<>();
        for (int i = 0; i < corner.candidateCount(); i++) if (corner.intersects(i)) hits.add(corner.chunkX(i) + ":" + corner.chunkZ(i));
        check(hits.equals(java.util.Set.of("0:0", "1:0", "0:1")));
        SplittableRandom random = new SplittableRandom(94311);
        for (int trial = 0; trial < 100; trial++) {
            int x = random.nextInt(), z = random.nextInt();
            DiscoveryArea area = new DiscoveryArea(x, z, 512);
            check(area.candidateCount() <= 4225);
            java.util.Set<Long> unique = new java.util.HashSet<>();
            boolean centerFound = false;
            for (int i = 0; i < area.candidateCount(); i++) {
                int cx = area.chunkX(i), cz = area.chunkZ(i);
                check(unique.add(((long) cx << 32) ^ (cz & 0xffffffffL)));
                if (cx == (x >> 4) && cz == (z >> 4)) { check(area.intersects(i)); centerFound = true; }
            }
            check(centerFound);
        }
        rejects(() -> new DiscoveryArea(0, 0, -1));
        rejects(() -> new DiscoveryArea(0, 0, 513));
    }

    private static void discoveryDefaults() throws Exception {
        LiveCropsConfig defaults = new LiveCropsConfig();
        check(defaults.autoAdoptOnFirstLoad && defaults.discoverAroundRespawn && defaults.respawnDiscoveryRadiusBlocks == 128);
        var oldMinimal = new com.google.gson.JsonPrimitive("{\"onlineRate\":0.4,\"emptyServerRate\":0.05}");
        LiveCropsConfig upgraded = LiveCropsConfig.CODEC.parse(JsonOps.INSTANCE, oldMinimal).getOrThrow();
        check(upgraded.autoAdoptOnFirstLoad && upgraded.discoverAroundRespawn);
        LiveCropsConfig custom = defaults.copy();
        custom.respawnDiscoveryRadiusBlocks = 256;
        custom.discoverAroundRespawn = false;
        ServerSettings settings = new ServerSettings(); settings.update(custom);
        var encoded = ServerSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        LiveCropsConfig restored = ServerSettings.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().config();
        check(!restored.discoverAroundRespawn && restored.respawnDiscoveryRadiusBlocks == 256);
        custom.respawnDiscoveryRadiusBlocks = 513; rejects(custom::validate);
        custom.respawnDiscoveryRadiusBlocks = -1; rejects(custom::validate);
    }

    private static void respawnSource() throws Exception {
        boolean[] personal = {false}, fallback = {false};
        bytecode("com/livecrops/GrowthEngine").accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals("home")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                        if (method.equals("getRespawnConfig")) personal[0] = true;
                        if (method.equals("getRespawnData")) fallback[0] = true;
                        check(!method.equals("findRespawnPositionAndUseSpawnBlock") && !method.equals("getChunk"));
                    }
                };
            }
        }, 0);
        check(personal[0] && fallback[0]);
    }
}
