package com.livecrops.mixin;

import com.livecrops.LiveCropsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "tickChunk", at = @At("HEAD"))
    private void livecrops$observeVanillaTick(LevelChunk chunk, int speed, CallbackInfo ci) {
        LiveCropsMod.vanillaTick((ServerLevel) (Object) this, chunk);
    }
}
