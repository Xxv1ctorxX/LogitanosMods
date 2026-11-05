package com.livecrops.mixin;

import com.livecrops.LiveCropsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void livecrops$changed(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        BlockState previous = cir.getReturnValue();
        if (previous != null && previous != state) LiveCropsMod.blockChanged((LevelChunk) (Object) this, pos, previous, state);
    }
}
