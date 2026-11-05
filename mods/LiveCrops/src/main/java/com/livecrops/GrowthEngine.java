package com.livecrops;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.gamerules.GameRules;

public final class GrowthEngine {
    private final MinecraftServer server;
    private final ServerSettings settings;
    private final Map<ServerLevel, LevelState> levels = new IdentityHashMap<>();
    private final ArrayDeque<Tracker> queue = new ArrayDeque<>();
    private final ArrayDeque<Adoption> adoptions = new ArrayDeque<>();
    private final ArrayDeque<HomeDiscovery> homes = new ArrayDeque<>();
    private final SplittableRandom random = new SplittableRandom();
    private LiveCropsConfig config;
    private int resident;
    private long processed, grown, discarded, lastWarning;
    private long tick;
    private int homePlayerCursor;
    private boolean applying;
    private long lastWorkNanos;

    private static final class LevelState {
        final ServerLevel level;
        final GrowthClock clock;
        final Long2ObjectOpenHashMap<Tracker> loaded = new Long2ObjectOpenHashMap<>();
        // Solo claves de chunks residentes; se retiran al descargar.
        final LongLinkedOpenHashSet discoveries = new LongLinkedOpenHashSet();
        final LongOpenHashSet adopting = new LongOpenHashSet();
        long frame;
        boolean advances;
        double before;
        LevelState(ServerLevel level) {
            this.level = level;
            clock = level.getDataStorage().computeIfAbsent(GrowthClock.TYPE);
        }
    }
    private static final class Tracker {
        final LevelState owner;
        final long key;
        final CropChunkData data;
        long tickedFrame = Long.MIN_VALUE;
        boolean queued;
        int cursor;
        double target;
        double settledTarget;
        long retryUntil;
        Tracker(LevelState owner, long key, CropChunkData data) { this.owner = owner; this.key = key; this.data = data; }
    }
    private static final class Adoption {
        final ServerLevel level;
        final long key;
        int section, offset;
        final AdoptionRequest request;
        Adoption(ServerLevel level, long key) { this(level, key, null); }
        Adoption(ServerLevel level, long key, AdoptionRequest request) { this.level = level; this.key = key; this.request = request; }
    }
    private static final class AdoptionRequest {
        final UUID player;
        int pending, registered, ignored, skippedChunks;
        AdoptionRequest(UUID player) { this.player = player; }
    }
    private static final class HomeDiscovery {
        final UUID player;
        final ResourceKey<Level> dimension;
        final DiscoveryArea area;
        final String signature;
        int cursor;
        HomeDiscovery(UUID player, LevelData.RespawnData respawn, int radius) {
            this.player = player; dimension = respawn.dimension();
            area = new DiscoveryArea(respawn.pos().getX(), respawn.pos().getZ(), radius);
            signature = homeSignature(respawn, radius);
        }
    }

    public GrowthEngine(MinecraftServer server) {
        this.server = server;
        settings = server.getDataStorage().computeIfAbsent(ServerSettings.TYPE);
        config = settings.config();
    }
    public LiveCropsConfig config() { return config; }
    private LevelState state(ServerLevel level) {
        return levels.computeIfAbsent(level, world -> {
            LevelState result = new LevelState(world);
            for (CropKind kind : CropKind.ALL) if (!kind.enabled(config)) result.clock.invalidate(kind.ordinal());
            return result;
        });
    }

    private static LevelChunk loaded(ServerLevel level, long key) {
        return level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
    }

    public void load(ServerLevel level, LevelChunk chunk) {
        CropChunkData data = chunk.getAttached(LiveCropsMod.CROPS);
        if (data != null && data.size() > 0) {
            LevelState owner = state(level);
            if (owner.loaded.containsKey(chunk.getPos().pack())) return;
            if (data.size() > config.maxTrackedCropsPerChunk || resident + data.size() > config.maxResidentTrackedCrops) {
                chunk.removeAttached(LiveCropsMod.CROPS);
                warnLimit();
                return;
            }
            if (data.repairFuture(owner.clock.value())) {
                chunk.markUnsaved();
                LiveCropsMod.LOGGER.warn("Reloj futuro en {} {}. Se rebasa sin crecimiento retroactivo.", level.dimension(), chunk.getPos());
            }
            Tracker tracker = new Tracker(owner, chunk.getPos().pack(), data);
            owner.loaded.put(tracker.key, tracker);
            resident += data.size();
            enqueue(tracker);
        }
        if (config.enabled && config.autoAdoptOnFirstLoad && !Boolean.TRUE.equals(chunk.getAttached(LiveCropsMod.ADOPTED))) {
            state(level).discoveries.add(chunk.getPos().pack());
        }
    }

    public void unload(ServerLevel level, LevelChunk chunk) {
        LevelState owner = levels.get(level);
        Tracker tracker = owner == null ? null : owner.loaded.remove(chunk.getPos().pack());
        if (owner != null) {
            owner.discoveries.remove(chunk.getPos().pack());
            owner.adopting.remove(chunk.getPos().pack());
        }
        if (tracker != null) { resident -= tracker.data.size(); queue.remove(tracker); tracker.queued = false; }
        var jobs = adoptions.iterator();
        while (jobs.hasNext()) {
            Adoption job = jobs.next();
            if (job.level == level && job.key == chunk.getPos().pack()) {
                jobs.remove(); completeAdoption(job, true);
            }
        }
        if (owner != null && owner.loaded.isEmpty() && owner.discoveries.isEmpty() && owner.adopting.isEmpty()) {
            owner.loaded.trim(); owner.discoveries.trim(); owner.adopting.trim();
        }
    }

    public void begin(ServerLevel level) {
        LevelState owner = state(level);
        owner.frame++;
        owner.before = owner.clock.value();
        // Nether/End comparten gameTime con overworld: su delta local puede ser 0.
        // Se cuenta un tick efectivo del nivel, respetando freeze y tick step.
        owner.advances = level.tickRateManager().runsNormally();
    }

    public void vanillaTick(ServerLevel level, LevelChunk chunk) {
        LevelState owner = levels.get(level);
        if (owner == null) return;
        long key = chunk.getPos().pack();
        if (config.enabled && config.autoAdoptOnFirstLoad && !owner.adopting.contains(key)
                && !Boolean.TRUE.equals(chunk.getAttached(LiveCropsMod.ADOPTED))) owner.discoveries.add(key);
        if (owner.loaded.isEmpty()) return;
        Tracker tracker = owner.loaded.get(chunk.getPos().pack());
        if (tracker != null) tracker.tickedFrame = owner.frame;
    }

    public void end(ServerLevel level) {
        LevelState owner = state(level);
        if (!owner.advances) return;
        double rate = server.getPlayerCount() > 0 ? config.onlineRate : config.emptyServerRate;
        if (config.enabled) owner.clock.advance(GrowthMath.increment(level.getGameRules().get(GameRules.RANDOM_TICK_SPEED), rate));
        double now = owner.clock.value();
        for (Tracker tracker : owner.loaded.values()) {
            LevelChunk chunk = loaded(level, tracker.key);
            if (chunk == null) continue;
            if (tracker.tickedFrame == owner.frame) {
                tracker.data.attended(owner.before, now);
                chunk.markUnsaved();
            }
            if (!tracker.queued && tracker.data.effective(now) > tracker.settledTarget
                    && (tracker.tickedFrame == owner.frame || tick % config.loadedDormantRefreshTicks == 0)) enqueue(tracker);
        }
    }

    private void enqueue(Tracker tracker) {
        if (tracker.queued || queue.size() >= config.maxQueuedChunks) return;
        tracker.target = tracker.data.effective(tracker.owner.clock.value());
        tracker.cursor = 0;
        tracker.retryUntil = tick + config.missingNeighborRetryTicks;
        tracker.queued = true;
        queue.addLast(tracker);
    }

    public boolean register(ServerLevel level, BlockPos pos) {
        if (!config.enabled) return false;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        BlockState block = chunk.getBlockState(pos);
        CropKind kind = CropKind.of(block);
        if (kind == null || !kind.enabled(config) || kind.finished(block, config)) return false;
        LevelState owner = state(level);
        CropChunkData data = chunk.getAttached(LiveCropsMod.CROPS);
        if (data == null) data = new CropChunkData(owner.clock.value());
        if (data.find(pos.asLong()) >= 0) return false;
        if (resident >= config.maxResidentTrackedCrops || data.size() >= config.maxTrackedCropsPerChunk) { warnLimit(); return false; }
        data.add(pos.asLong(), kind.ordinal(), data.effective(owner.clock.value()), owner.clock.revision(kind.ordinal()));
        chunk.setAttached(LiveCropsMod.CROPS, data);
        resident++;
        Tracker tracker = owner.loaded.get(chunk.getPos().pack());
        if (tracker == null) {
            tracker = new Tracker(owner, chunk.getPos().pack(), data);
            tracker.settledTarget = data.effective(owner.clock.value());
            owner.loaded.put(tracker.key, tracker);
        }
        chunk.markUnsaved();
        return true;
    }

    public void watchBerry(ServerLevel level, BlockPos pos) {
        // Se ejecuta antes de la interacción: el hook de mutación detectará la bajada de edad.
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null && berryWatches.size() < 256) berryWatches.put(pos.immutable(), level);
    }
    private final Map<BlockPos, ServerLevel> berryWatches = new java.util.HashMap<>();

    public void changed(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState old, BlockState block) {
        if (applying) return;
        if (CropKind.of(old) == null && CropKind.of(block) == null) return;
        CropChunkData data = chunk.getAttached(LiveCropsMod.CROPS);
        if (data != null) {
            int index = data.find(pos.asLong());
            if (index >= 0) {
                CropKind kind = CropKind.ALL[data.kind(index)];
                boolean sameLife = kind.matches(block) && kind.matches(old)
                        && (kind.stem() || kind.age(block) >= kind.age(old));
                if (!sameLife || kind.finished(block, config)) remove(level, chunk, data, index);
                else if (kind.attached(block) || kind.attached(old)) {
                    data.consume(index, data.effective(state(level).clock.value()));
                    chunk.markUnsaved();
                }
            }
        }
        if (berryWatches.get(pos) == level && old.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                && block.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                && CropKind.SWEET_BERRY.age(block) < CropKind.SWEET_BERRY.age(old)) register(level, pos);
    }

    private void remove(ServerLevel level, LevelChunk chunk, CropChunkData data, int index) {
        data.remove(index);
        resident--;
        LevelState owner = state(level);
        Tracker tracker = owner.loaded.get(chunk.getPos().pack());
        if (tracker != null) tracker.cursor = Math.min(tracker.cursor, index);
        if (data.size() == 0) {
            chunk.removeAttached(LiveCropsMod.CROPS);
            if (tracker != null) { queue.remove(tracker); tracker.queued = false; owner.loaded.remove(tracker.key); }
        }
        chunk.markUnsaved();
    }

    private static boolean neighborsLoaded(ServerLevel level, BlockPos pos) {
        int minX = (pos.getX() - 1) >> 4, maxX = (pos.getX() + 1) >> 4;
        int minZ = (pos.getZ() - 1) >> 4, maxZ = (pos.getZ() + 1) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            if (chunk == null || !chunk.isLightCorrect()) return false;
        }
        return true;
    }

    private void process(Tracker tracker) {
        ServerLevel level = tracker.owner.level;
        LevelChunk chunk = loaded(level, tracker.key);
        CropChunkData data = tracker.data;
        if (chunk == null || chunk.getAttached(LiveCropsMod.CROPS) != data) { finish(tracker); return; }
        if (tracker.cursor >= data.size()) { finish(tracker); return; }
        int i = tracker.cursor;
        BlockPos pos = BlockPos.of(data.position(i));
        if ((pos.getX() >> 4) != ChunkPos.getX(tracker.key) || (pos.getZ() >> 4) != ChunkPos.getZ(tracker.key)
                || pos.getY() < chunk.getMinY() || pos.getY() >= chunk.getMinY() + chunk.getHeight()) {
            remove(level, chunk, data, i); discarded++; return;
        }
        CropKind kind = CropKind.ALL[data.kind(i)];
        BlockState block = chunk.getBlockState(pos);
        if (!kind.matches(block) || !kind.enabled(config) || kind.finished(block, config)
                || tracker.owner.clock.invalidated(kind.ordinal(), data.admitted(i))) {
            remove(level, chunk, data, i); discarded++; return;
        }
        double intensity = GrowthMath.debt(tracker.target, data.consumed(i));
        if (intensity > 0 && !neighborsLoaded(level, pos)) {
            if (tick < tracker.retryUntil) { queue.remove(tracker); queue.addLast(tracker); return; }
            discarded++;
        } else if (intensity > 0 && !kind.attached(block)) {
            int remaining = kind.maxAge - kind.age(block);
            int cap = remaining + (kind.stem() && config.stemsProduceFruit ? 1 : 0);
            double probability = kind.probability(level, pos, block);
            int steps = kind.stem() && config.stemsProduceFruit
                    ? GrowthMath.stemStages(intensity, probability, remaining,
                            Integer.bitCount(kind.fruitSpaces(level, pos)) / 4.0, random)
                    : GrowthMath.sample(intensity * probability, cap, random);
            applying = true;
            try {
                if (steps > 0 && remaining > 0) {
                    block = block.setValue(kind.ageProperty, Math.min(kind.maxAge, kind.age(block) + steps));
                    if (level.setBlock(pos, block, Block.UPDATE_CLIENTS)) grown++;
                }
                if (kind.stem() && config.stemsProduceFruit && steps > remaining) kind.produceFruit(level, pos, random);
            } finally { applying = false; }
            if (kind.finished(block, config)) { remove(level, chunk, data, i); processed++; return; }
        }
        data.consume(i, tracker.target);
        tracker.cursor++;
        chunk.markUnsaved();
        processed++;
        if (tracker.cursor >= data.size()) finish(tracker);
    }

    private void finish(Tracker tracker) {
        queue.remove(tracker); tracker.queued = false;
        tracker.settledTarget = Math.max(tracker.settledTarget, tracker.target);
    }

    public void work() {
        tick++;
        berryWatches.clear();
        long start = System.nanoTime();
        long budget = (long) (config.maxWorkMillisPerTick * 1_000_000);
        if (config.enabled && config.discoverAroundRespawn && tick % 100 == 1) discoverHomes(start, budget);
        int entries = 0;
        while (config.enabled && !queue.isEmpty() && entries < config.maxCatchUpEntriesPerTick && System.nanoTime() - start < budget) {
            process(queue.peekFirst()); entries++;
        }
        while (config.enabled && entries < config.maxCatchUpEntriesPerTick && System.nanoTime() - start < budget) {
            if (adoptions.size() < config.maxQueuedChunks && !homes.isEmpty() && config.discoverAroundRespawn) homeStep();
            else if (adoptions.size() < config.maxQueuedChunks && config.autoAdoptOnFirstLoad && discoveryStep()) { /* una clave */ }
            else if (!adoptions.isEmpty()) adoptStep(adoptions.peekFirst());
            else break;
            entries++;
        }
        lastWorkNanos = System.nanoTime() - start;
    }

    private LevelData.RespawnData home(ServerPlayer player) {
        var respawn = player.getRespawnConfig();
        LevelData.RespawnData point = respawn == null ? server.getRespawnData() : respawn.respawnData();
        if (server.getLevel(point.dimension()) == null) point = server.getRespawnData();
        return point;
    }

    private static String homeSignature(LevelData.RespawnData point, int radius) {
        return point.dimension() + ":" + point.pos().asLong() + ":" + radius;
    }

    private void discoverHomes(long start, long budget) {
        homes.removeIf(job -> server.getPlayerList().getPlayer(job.player) == null);
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) { homePlayerCursor = 0; return; }
        for (int checked = 0; checked < players.size(); checked++) {
            if (homes.size() >= config.maxQueuedChunks || System.nanoTime() - start >= budget) break;
            homePlayerCursor %= players.size();
            ServerPlayer player = players.get(homePlayerCursor++);
            LevelData.RespawnData point = home(player);
            String signature = homeSignature(point, config.respawnDiscoveryRadiusBlocks);
            if (signature.equals(player.getAttached(LiveCropsMod.DISCOVERED_HOME))) continue;
            UUID id = player.getUUID();
            if (homes.stream().anyMatch(job -> job.player.equals(id))) continue;
            homes.addLast(new HomeDiscovery(id, point, config.respawnDiscoveryRadiusBlocks));
        }
    }

    private void homeStep() {
        HomeDiscovery job = homes.peekFirst();
        ServerPlayer player = server.getPlayerList().getPlayer(job.player);
        if (player == null || !job.signature.equals(homeSignature(home(player), config.respawnDiscoveryRadiusBlocks))) {
            homes.removeFirst(); return;
        }
        ServerLevel level = server.getLevel(job.dimension);
        if (level == null) { homes.removeFirst(); return; }
        if (job.cursor >= job.area.candidateCount()) {
            player.setAttached(LiveCropsMod.DISCOVERED_HOME, job.signature);
            homes.removeFirst(); return;
        }
        int index = job.cursor++;
        if (!job.area.intersects(index)) return;
        LevelChunk chunk = level.getChunkSource().getChunkNow(job.area.chunkX(index), job.area.chunkZ(index));
        if (chunk == null || Boolean.TRUE.equals(chunk.getAttached(LiveCropsMod.ADOPTED))) return;
        LevelState owner = state(level);
        long key = chunk.getPos().pack();
        if (owner.adopting.add(key)) {
            owner.discoveries.remove(key);
            adoptions.addLast(new Adoption(level, key));
        }
    }

    /** Una sola clave por operación. Nunca recorre regiones ni mantiene chunks vivos. */
    private boolean discoveryStep() {
        for (LevelState owner : levels.values()) {
            if (owner.discoveries.isEmpty()) continue;
            long key = owner.discoveries.removeFirstLong();
            LevelChunk chunk = loaded(owner.level, key);
            if (chunk != null && !owner.adopting.contains(key)
                    && !Boolean.TRUE.equals(chunk.getAttached(LiveCropsMod.ADOPTED))) {
                owner.adopting.add(key);
                adoptions.addLast(new Adoption(owner.level, key));
            }
            return true;
        }
        return false;
    }

    private void adoptStep(Adoption job) {
        LevelChunk chunk = loaded(job.level, job.key);
        if (chunk == null) {
            adoptions.removeFirst(); state(job.level).adopting.remove(job.key);
            completeAdoption(job, true); return;
        }
        LevelChunkSection[] sections = chunk.getSections();
        if (job.section >= sections.length) {
            chunk.setAttached(LiveCropsMod.ADOPTED, true);
            adoptions.removeFirst();
            state(job.level).adopting.remove(job.key);
            completeAdoption(job, false);
            return;
        }
        LevelChunkSection section = sections[job.section];
        if (job.offset == 0 && !section.maybeHas(block -> CropKind.of(block) != null)) { job.section++; return; }
        int offset = job.offset++;
        int x = offset & 15, z = (offset >> 4) & 15, y = offset >> 8;
        BlockState block = section.getBlockState(x, y, z);
        if (CropKind.of(block) != null) {
            boolean added = register(job.level, new BlockPos(
                    chunk.getPos().getMinBlockX() + x, chunk.getMinY() + job.section * 16 + y, chunk.getPos().getMinBlockZ() + z));
            if (job.request != null) {
                if (added) job.request.registered++;
                else job.request.ignored++;
            }
        }
        if (job.offset == 4096) { job.offset = 0; job.section++; }
    }

    private void completeAdoption(Adoption job, boolean skipped) {
        AdoptionRequest request = job.request;
        if (request == null) return;
        if (skipped) request.skippedChunks++;
        if (--request.pending > 0) return;
        String result = "Adopción terminada: " + request.registered + " registrados, " + request.ignored
                + " omitidos (ya registrados, maduros, desactivados o límite), " + request.skippedChunks + " chunks cancelados/descargados.";
        if (request.player != null) {
            var player = server.getPlayerList().getPlayer(request.player);
            if (player != null) player.sendSystemMessage(Component.literal(result));
        }
        LiveCropsMod.LOGGER.info(result);
    }

    public int adoption(ServerLevel level, BlockPos pos, int radius, boolean confirm, UUID player) {
        if (radius < 0 || radius > config.adoptionMaxRadiusChunks) throw new IllegalArgumentException("Radio fuera del límite configurado");
        if (confirm && !config.enabled) throw new IllegalStateException("LiveCrops está desactivado. Actívalo antes de adoptar.");
        AdoptionRequest request = confirm ? new AdoptionRequest(player) : null;
        int count = 0;
        for (int x = (pos.getX() >> 4) - radius; x <= (pos.getX() >> 4) + radius; x++) {
            for (int z = (pos.getZ() >> 4) - radius; z <= (pos.getZ() >> 4) + radius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                if (!confirm) count++;
                if (confirm && adoptions.size() < config.maxQueuedChunks) {
                    long key = chunk.getPos().pack();
                    if (state(level).adopting.add(key)) {
                        request.pending++;
                        state(level).discoveries.remove(key);
                        adoptions.addLast(new Adoption(level, key, request)); count++;
                    }
                }
            }
        }
        return count;
    }

    public int forget(ServerLevel level, BlockPos pos, boolean confirm) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return 0;
        CropChunkData data = chunk.getAttached(LiveCropsMod.CROPS);
        int count = data == null ? 0 : data.size();
        if (confirm) {
            unload(level, chunk); chunk.removeAttached(LiveCropsMod.CROPS);
            chunk.setAttached(LiveCropsMod.ADOPTED, true);
        }
        return count;
    }

    public void rates(double online, double empty) {
        LiveCropsConfig next = config.copy(); next.onlineRate = online; next.emptyServerRate = empty;
        applyConfig(next);
    }
    public void setting(String field, Object value) throws ReflectiveOperationException {
        LiveCropsConfig next = config.copy();
        var target = LiveCropsConfig.class.getField(field);
        if (java.lang.reflect.Modifier.isStatic(target.getModifiers())
                || !(target.getType() == boolean.class || target.getType() == int.class || target.getType() == double.class)) {
            throw new IllegalArgumentException("Opción no configurable: " + field);
        }
        target.set(next, value);
        applyConfig(next);
    }
    public void cropSetting(String id, boolean enabled) {
        LiveCropsConfig next = config.copy();
        if (!next.crops.containsKey(id)) throw new IllegalArgumentException("Cultivo no compatible: " + id);
        next.crops.put(id, enabled);
        applyConfig(next);
    }
    public void resetSettings() { applyConfig(new LiveCropsConfig()); }
    private void applyConfig(LiveCropsConfig next) {
        next.validate();
        for (LevelState owner : levels.values()) for (CropKind kind : CropKind.ALL) {
            if (kind.enabled(config) && !kind.enabled(next)) owner.clock.invalidate(kind.ordinal());
        }
        config = next;
        settings.update(next);
        if (!config.autoAdoptOnFirstLoad) for (LevelState owner : levels.values()) owner.discoveries.clear();
        if (!config.discoverAroundRespawn) homes.clear();
    }

    public String info() {
        int chunks = 0, discoveryCount = 0;
        for (LevelState owner : levels.values()) { chunks += owner.loaded.size(); discoveryCount += owner.discoveries.size(); }
        return String.format(java.util.Locale.ROOT,
                "LiveCrops: %s · cerca: vanilla · lejos: %.0f %% · vacío: %.0f %% · residentes: %d cultivos / %d chunks · cola: %d · adopción: %d · procesados: %d · crecidos: %d · descartes: %d · último lote: %.3f ms. Totales mundiales no cargados: desconocidos.",
                config.enabled ? "activo" : "desactivado", config.onlineRate * 100, config.emptyServerRate * 100,
                resident, chunks, queue.size(), adoptions.size(), processed, grown, discarded, lastWorkNanos / 1_000_000.0)
                + " Detección pendiente: " + discoveryCount + " chunks / " + homes.size() + " zonas de respawn.";
    }

    public String cropStatus(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return "Zona descargada.";
        CropKind kind = CropKind.of(chunk.getBlockState(pos));
        if (kind == null) return "Bloque no compatible con LiveCrops.";
        CropChunkData data = chunk.getAttached(LiveCropsMod.CROPS);
        Tracker tracker = state(level).loaded.get(chunk.getPos().pack());
        String reason = "";
        if (!config.enabled) reason = " · mod desactivado";
        else if (!kind.enabled(config)) reason = " · tipo desactivado";
        else if (kind.finished(chunk.getBlockState(pos), config)) reason = " · cultivo maduro";
        else if (!neighborsLoaded(level, pos)) reason = " · esperando vecinos o iluminación";
        else if (!kind.attached(chunk.getBlockState(pos)) && kind.probability(level, pos, chunk.getBlockState(pos)) == 0) {
            reason = " · sin progreso: luz o soporte insuficientes";
        }
        return kind.id() + " · edad " + kind.age(chunk.getBlockState(pos)) + "/" + kind.maxAge
                + " · " + (data != null && data.find(pos.asLong()) >= 0 ? "registrado" : "sin seguimiento")
                + " · " + (tracker != null && tracker.tickedFrame == tracker.owner.frame ? "atendido por vanilla" : "desatendido")
                + (tracker != null && tracker.queued ? " · compensación pendiente" : "") + reason;
    }

    private void warnLimit() {
        if (tick - lastWarning >= 1200 || lastWarning == 0) {
            LiveCropsMod.LOGGER.warn("Límite de seguimiento de LiveCrops alcanzado. Las plantas siguen creciendo con vanilla.");
            lastWarning = Math.max(1, tick);
        }
    }
}
