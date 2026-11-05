package com.livecrops;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LiveCropsMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("livecrops");
    public static final AttachmentType<CropChunkData> CROPS = AttachmentRegistry.<CropChunkData>builder()
            .persistent(CropChunkData.CODEC).buildAndRegister(Identifier.fromNamespaceAndPath("livecrops", "crops"));
    // Sin syncWith: ningún payload, registro o instalación adicional en el cliente.
    public static final AttachmentType<Boolean> ADOPTED = AttachmentRegistry.<Boolean>builder()
            .persistent(com.mojang.serialization.Codec.BOOL).buildAndRegister(Identifier.fromNamespaceAndPath("livecrops", "adopted"));
    public static final AttachmentType<String> DISCOVERED_HOME = AttachmentRegistry.<String>builder()
            .persistent(com.mojang.serialization.Codec.STRING).copyOnDeath()
            .buildAndRegister(Identifier.fromNamespaceAndPath("livecrops", "discovered_home"));
    private static GrowthEngine engine;

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> engine = new GrowthEngine(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> engine = null);
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
            if (engine != null) engine.load(level, chunk);
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            if (engine != null) engine.unload(level, chunk);
        });
        ServerTickEvents.START_LEVEL_TICK.register(level -> { if (engine != null) engine.begin(level); });
        ServerTickEvents.END_LEVEL_TICK.register(level -> { if (engine != null) engine.end(level); });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (engine != null) engine.work(); });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> LiveCropsCommand.register(dispatcher));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (engine != null && level instanceof ServerLevel serverLevel
                    && !player.isSpectator() && level.getBlockState(hit.getBlockPos()).is(Blocks.SWEET_BERRY_BUSH)) {
                engine.watchBerry(serverLevel, hit.getBlockPos());
            }
            return InteractionResult.PASS;
        });
        LOGGER.info("LiveCrops cargado: crecimiento adaptativo, sin cargar chunks ni simular jugadores.");
    }

    public static GrowthEngine engine() { return engine; }
    public static void vanillaTick(ServerLevel level, LevelChunk chunk) {
        if (engine != null) engine.vanillaTick(level, chunk);
    }
    public static void blockChanged(LevelChunk chunk, BlockPos pos, BlockState old, BlockState state) {
        if (engine != null && chunk.getLevel() instanceof ServerLevel level) engine.changed(level, chunk, pos, old, state);
    }
    public static void planted(BlockPlaceContext context) {
        if (engine != null && context.getLevel() instanceof ServerLevel level) engine.register(level, context.getClickedPos());
    }
}
