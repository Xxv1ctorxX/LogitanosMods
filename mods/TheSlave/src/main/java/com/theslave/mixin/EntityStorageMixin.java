package com.theslave.mixin;

import com.theslave.ritual.EntityDurability;
import com.theslave.ritual.EntityStorageAccess;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityStorage.class)
public abstract class EntityStorageMixin implements EntityStorageAccess {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private SimpleRegionStorage simpleRegionStorage;

    @Override public SimpleRegionStorage theslave$regionStorage() { return simpleRegionStorage; }

    @Redirect(method="storeEntities",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/storage/SimpleRegionStorage;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> theslave$write(SimpleRegionStorage storage,ChunkPos pos,CompoundTag tag) {
        return EntityDurability.escritura(level,storage,pos,tag,storage.write(pos,tag));
    }

    @Redirect(method="storeEntities",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/storage/SimpleRegionStorage;write(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> theslave$writeEmpty(SimpleRegionStorage storage,ChunkPos pos,Supplier<CompoundTag> supplier) {
        // En 26.3 esta sobrecarga solo se usa para IOWorker.STORE_EMPTY.
        return EntityDurability.escritura(level,storage,pos,new CompoundTag(),storage.write(pos,supplier));
    }
}
