package com.livecrops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LiveCropsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Codec<LiveCropsConfig> CODEC = Codec.STRING.comapFlatMap(json -> {
        try {
            LiveCropsConfig config = GSON.fromJson(json, LiveCropsConfig.class);
            if (config == null) return DataResult.error(() -> "Configuración vacía");
            config.validate();
            return DataResult.success(config);
        } catch (RuntimeException error) {
            return DataResult.error(() -> "Configuración inválida: " + error.getMessage());
        }
    }, GSON::toJson);
    public static final String[] IDS = {"wheat", "carrots", "potatoes", "beetroots", "nether_wart",
            "cocoa", "sweet_berry_bush", "pumpkin_stem", "melon_stem"};
    public boolean enabled = true;
    public double onlineRate = 0.40;
    public double emptyServerRate = 0.05;
    public int maxTrackedCropsPerChunk = 2048;
    public int maxResidentTrackedCrops = 65536;
    public int maxQueuedChunks = 256;
    public int maxCatchUpEntriesPerTick = 256;
    public double maxWorkMillisPerTick = 0.5;
    public int loadedDormantRefreshTicks = 100;
    public int missingNeighborRetryTicks = 100;
    public int adoptionMaxRadiusChunks = 6;
    public boolean autoAdoptOnFirstLoad = true;
    public boolean discoverAroundRespawn = true;
    public int respawnDiscoveryRadiusBlocks = 128;
    public boolean stemsProduceFruit = true;
    public Map<String, Boolean> crops = defaults();

    private static Map<String, Boolean> defaults() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String id : IDS) result.put("minecraft:" + id, true);
        return result;
    }

    public void validate() {
        rate(onlineRate, "onlineRate");
        rate(emptyServerRate, "emptyServerRate");
        if (emptyServerRate > onlineRate) throw new IllegalArgumentException("emptyServerRate debe ser <= onlineRate");
        range(maxTrackedCropsPerChunk, 1, 4096, "maxTrackedCropsPerChunk");
        range(maxResidentTrackedCrops, 1, 1_048_576, "maxResidentTrackedCrops");
        range(maxQueuedChunks, 1, 4096, "maxQueuedChunks");
        range(maxCatchUpEntriesPerTick, 1, 65536, "maxCatchUpEntriesPerTick");
        if (!Double.isFinite(maxWorkMillisPerTick) || maxWorkMillisPerTick < 0.01 || maxWorkMillisPerTick > 10) {
            throw new IllegalArgumentException("maxWorkMillisPerTick debe estar entre 0.01 y 10");
        }
        range(loadedDormantRefreshTicks, 1, 72000, "loadedDormantRefreshTicks");
        range(missingNeighborRetryTicks, 1, 72000, "missingNeighborRetryTicks");
        range(adoptionMaxRadiusChunks, 0, 16, "adoptionMaxRadiusChunks");
        range(respawnDiscoveryRadiusBlocks, 0, 512, "respawnDiscoveryRadiusBlocks");
        if (crops == null) throw new IllegalArgumentException("Falta crops");
        Map<String, Boolean> known = defaults();
        for (var entry : crops.entrySet()) {
            if (!known.containsKey(entry.getKey()) || entry.getValue() == null) {
                throw new IllegalArgumentException("Cultivo inválido: " + entry.getKey());
            }
        }
        known.putAll(crops);
        crops = known;
    }

    private static void rate(double value, String field) {
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException(field + " debe estar entre 0 y 1");
    }

    private static void range(int value, int min, int max, String field) {
        if (value < min || value > max) throw new IllegalArgumentException(field + " debe estar entre " + min + " y " + max);
    }

    public LiveCropsConfig copy() { return GSON.fromJson(GSON.toJson(this), LiveCropsConfig.class); }
    public String describe() { return GSON.toJson(this); }
}
