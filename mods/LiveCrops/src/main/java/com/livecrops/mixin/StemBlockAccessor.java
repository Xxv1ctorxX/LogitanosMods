package com.livecrops.mixin;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StemBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StemBlock.class)
public interface StemBlockAccessor {
    @Accessor("fruitSupportBlocks") TagKey<Block> livecrops$fruitSupport();
}
