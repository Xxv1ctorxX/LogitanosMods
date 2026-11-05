package com.livecrops;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Solo se marca para guardar cuando un comando cambia los valores predeterminados. */
public final class ServerSettings extends SavedData {
    public static final Codec<ServerSettings> CODEC = LiveCropsConfig.CODEC.xmap(ServerSettings::new, ServerSettings::config);
    public static final SavedDataType<ServerSettings> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("livecrops", "settings"), ServerSettings::new, CODEC, null);
    private LiveCropsConfig config;

    public ServerSettings() { this(new LiveCropsConfig()); }
    private ServerSettings(LiveCropsConfig config) { this.config = config.copy(); }
    public synchronized LiveCropsConfig config() { return config.copy(); }
    public synchronized void update(LiveCropsConfig next) {
        next.validate();
        config = next.copy();
        setDirty();
    }
}
